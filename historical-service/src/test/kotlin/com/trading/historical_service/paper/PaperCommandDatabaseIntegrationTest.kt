package com.trading.historical_service.paper

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import reactor.core.publisher.Mono
import java.sql.DriverManager
import java.time.Duration

@Testcontainers
class PaperCommandDatabaseIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_command")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test
    fun `wallet initialization is atomic and client order replay is idempotent`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                val ddl = javaClass.getResourceAsStream(
                    "/db/migration/V9__create_paper_trading_ledger.sql"
                )!!.bufferedReader().readText()
                statement.execute(ddl)
                val scopedDdl = javaClass.getResourceAsStream(
                    "/db/migration/V12__paper_wallet_scoped_credentials.sql"
                )!!.bufferedReader().use { it.readText() }
                statement.execute(scopedDdl)
                statement.execute(requireNotNull(javaClass.getResourceAsStream(
                    "/db/migration/V13__paper_wallet_credential_audit.sql"
                )).bufferedReader().use { it.readText() })
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username).password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val commands = PaperCommandController(db, true)
        fun <T : Any> Mono<T>.await(): T = requireNotNull(block(Duration.ofSeconds(10)))

        val provisioned = commands.createWallet(CreatePaperWalletRequest("250.00000000")).await()
        val wallet = provisioned.walletId
        assertTrue(PaperWalletTokens.isWellFormed(provisioned.walletToken))
        val access = PaperWalletAccessStore(db)
        assertTrue(access.authorized(wallet, provisioned.walletToken).await())
        assertFalse(access.authorized(wallet, PaperWalletTokens.generate()).await())
        val stored = db.sql("SELECT token_hash FROM paper_wallet_access WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> requireNotNull(row.get("token_hash", String::class.java)) }
            .one().await()
        assertEquals(PaperWalletTokens.sha256(provisioned.walletToken), stored)
        assertFalse(stored.contains(provisioned.walletToken))
        val balance = db.sql("SELECT available FROM paper_balances WHERE wallet_id=:wallet AND asset='USDT'")
            .bind("wallet", wallet)
            .map { row, _ -> requireNotNull(row.get("available", java.math.BigDecimal::class.java)) }
            .one().await()
        assertEquals(0, balance.compareTo(java.math.BigDecimal("250.00000000")))

        val request = SubmitPaperOrderRequest(
            "test-key-1", "BTCUSDT", PaperOrderSide.BUY, "0.01000000", 1000L
        )
        val auth = "Bearer ${provisioned.walletToken}"
        val first = commands.submit(wallet, request, auth).await()
        val replay = commands.submit(wallet, request, auth).await()
        assertEquals(first.orderId, replay.orderId)
        assertEquals("NEW", replay.status)
        val count = db.sql("SELECT count(*) AS total FROM paper_orders WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await()
        assertEquals(1L, count)

        val conflict = assertThrows(RuntimeException::class.java) {
            commands.submit(wallet, request.copy(quantity = "0.02000000"), auth).await()
        }
        assertTrue(conflict is ResponseStatusException ||
            conflict.cause is ResponseStatusException)
        val after = db.sql("SELECT count(*) AS total FROM paper_orders WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await()
        assertEquals(1L, after)

        // Shared bootstrap secret, wrong wallet capability and a missing
        // header cannot read an idempotent order or place a new one.
        for (invalid in listOf("", "Bearer " + "a".repeat(40),
            "Bearer ${PaperWalletTokens.generate()}")) {
            val denied = assertThrows(RuntimeException::class.java) {
                commands.submit(wallet, request, invalid).await()
            }
            val response = (denied as? ResponseStatusException)
                ?: (denied.cause as? ResponseStatusException)
            assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, response?.statusCode)
        }
        assertEquals(1L, db.sql("SELECT COUNT(*) AS total FROM paper_orders")
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await())

        // Recheck even after the WebFilter may have already accepted the
        // original bearer; rotation invalidates it for DB mutation.
        val lifecycle = PaperWalletCredentialLifecycle(db)
        val rotated = lifecycle.rotate(wallet, provisioned.walletToken).await()
        val oldDenied = assertThrows(RuntimeException::class.java) {
            commands.submit(wallet, request.copy(clientOrderId = "old-after-rotation"), auth).await()
        }
        val oldResponse = (oldDenied as? ResponseStatusException)
            ?: (oldDenied.cause as? ResponseStatusException)
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, oldResponse?.statusCode)
        val fresh = commands.submit(wallet, request.copy(clientOrderId = "new-after-rotation"),
            "Bearer $rotated").await()
        assertEquals("NEW", fresh.status)

        assertTrue(lifecycle.revoke(wallet, rotated).await())
        val revoked = assertThrows(RuntimeException::class.java) {
            commands.submit(wallet, request.copy(clientOrderId = "after-revoke"),
                "Bearer $rotated").await()
        }
        val revokedResponse = (revoked as? ResponseStatusException)
            ?: (revoked.cause as? ResponseStatusException)
        assertEquals(org.springframework.http.HttpStatus.UNAUTHORIZED, revokedResponse?.statusCode)
        val finalCount = db.sql("SELECT COUNT(*) AS total FROM paper_orders WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await()
        assertEquals(2L, finalCount)
    }
}
