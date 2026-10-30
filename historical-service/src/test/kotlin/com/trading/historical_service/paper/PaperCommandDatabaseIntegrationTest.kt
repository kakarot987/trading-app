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

        val wallet = commands.createWallet(CreatePaperWalletRequest("250.00000000")).await().walletId
        val balance = db.sql("SELECT available FROM paper_balances WHERE wallet_id=:wallet AND asset='USDT'")
            .bind("wallet", wallet)
            .map { row, _ -> requireNotNull(row.get("available", java.math.BigDecimal::class.java)) }
            .one().await()
        assertEquals(0, balance.compareTo(java.math.BigDecimal("250.00000000")))

        val request = SubmitPaperOrderRequest(
            "test-key-1", "BTCUSDT", PaperOrderSide.BUY, "0.01000000", 1000L
        )
        val first = commands.submit(wallet, request).await()
        val replay = commands.submit(wallet, request).await()
        assertEquals(first.orderId, replay.orderId)
        assertEquals("NEW", replay.status)
        val count = db.sql("SELECT count(*) AS total FROM paper_orders WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await()
        assertEquals(1L, count)

        val conflict = assertThrows(RuntimeException::class.java) {
            commands.submit(wallet, request.copy(quantity = "0.02000000")).await()
        }
        assertTrue(conflict is ResponseStatusException ||
            conflict.cause is ResponseStatusException)
        val after = db.sql("SELECT count(*) AS total FROM paper_orders WHERE wallet_id=:wallet")
            .bind("wallet", wallet)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one().await()
        assertEquals(1L, after)
    }
}
