package com.trading.historical_service.paper

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/** Scoped capabilities against disposable PostgreSQL, not an identity provider. */
@Testcontainers
class PaperWalletAccessIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_access")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test fun `scoped token is not transferable and revocation rejects access`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                for (path in listOf(
                    "/db/migration/V9__create_paper_trading_ledger.sql",
                    "/db/migration/V12__paper_wallet_scoped_credentials.sql",
                    "/db/migration/V14__immutable_paper_wallet_initial_funding.sql"
                )) {
                    statement.execute(requireNotNull(javaClass.getResourceAsStream(path))
                        .bufferedReader().use { it.readText() })
                }
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val commands = PaperCommandController(db, true)
        val store = PaperWalletAccessStore(db)
        val first = requireNotNull(commands.createWallet(CreatePaperWalletRequest())
            .block(Duration.ofSeconds(10)))
        val second = requireNotNull(commands.createWallet(CreatePaperWalletRequest())
            .block(Duration.ofSeconds(10)))

        assertNotEquals(first.walletId, second.walletId)
        assertNotEquals(first.walletToken, second.walletToken)
        fun allowed(wallet: UUID, token: String): Boolean =
            requireNotNull(store.authorized(wallet, token).block(Duration.ofSeconds(10)))
        assertTrue(allowed(first.walletId, first.walletToken))
        assertTrue(allowed(second.walletId, second.walletToken))
        assertFalse(allowed(first.walletId, second.walletToken))
        assertFalse(allowed(second.walletId, first.walletToken))
        assertFalse(allowed(first.walletId, "bad-token"))
        assertFalse(allowed(UUID.randomUUID(), first.walletToken))

        val legacyWallet = db.sql("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id")
            .map { row, _ -> requireNotNull(row.get("wallet_id", UUID::class.java)) }
            .one().block(Duration.ofSeconds(10))!!
        assertFalse(allowed(legacyWallet, first.walletToken),
            "Pre-migration wallets must not inherit a global credential")

        val updated = db.sql(
            "UPDATE paper_wallet_access SET revoked_at=now() WHERE wallet_id=:wallet"
        ).bind("wallet", first.walletId)
            .fetch().rowsUpdated().block(Duration.ofSeconds(10))
        assertEquals(1L, updated)
        assertFalse(allowed(first.walletId, first.walletToken))
        assertTrue(allowed(second.walletId, second.walletToken),
            "Revocation must affect only one wallet")
    }

    @Test fun `token generation is opaque non-repeating and digest has fixed width`() {
        val a = PaperWalletTokens.generate()
        val b = PaperWalletTokens.generate()
        assertTrue(PaperWalletTokens.isWellFormed(a))
        assertTrue(PaperWalletTokens.isWellFormed(b))
        assertNotEquals(a, b)
        assertEquals(64, PaperWalletTokens.sha256(a).length)
        assertNotEquals(PaperWalletTokens.sha256(a), PaperWalletTokens.sha256(b))
        assertThrows(IllegalArgumentException::class.java) { PaperWalletTokens.sha256("admin") }
    }
}
