package com.trading.historical_service.paper

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/** Disposable DB checks for new funding provenance and legacy non-backfill. */
@Testcontainers
class PaperFundingRepositoryIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_funding")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test fun `new seed is immutable and legacy wallets are never backfilled`() {
        lateinit var legacy: UUID
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V12__paper_wallet_scoped_credentials.sql"
                )) {
                    stmt.execute(requireNotNull(javaClass.getResourceAsStream(
                        "/db/migration/$migration"
                    )).bufferedReader().use { it.readText() })
                }
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use {
                    assertTrue(it.next())
                    legacy = it.getObject(1, UUID::class.java)
                }
                // The legacy wallet predates V14. Never infer that it was
                // funded with any particular amount.
                stmt.execute(
                    "INSERT INTO paper_balances(wallet_id,asset,available) VALUES ('$legacy','USDT',123)"
                )
                stmt.execute(requireNotNull(javaClass.getResourceAsStream(
                    "/db/migration/V14__immutable_paper_wallet_initial_funding.sql"
                )).bufferedReader().use { it.readText() })
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
        val audit = PaperFundingRepository(db)
        val wallet = requireNotNull(commands.createWallet(
            CreatePaperWalletRequest("250.00000000")
        ).block(Duration.ofSeconds(10))).walletId
        fun read(id: UUID) = requireNotNull(audit.audit(id).block(Duration.ofSeconds(10)))
        val initial = read(wallet)
        assertEquals("RECONCILED", initial.status)
        assertEquals(0, initial.recordedInitialUsdt!!.compareTo(BigDecimal("250")))
        assertEquals(0, initial.expectedAvailableUsdt!!.compareTo(initial.availableUsdt))
        assertEquals(0, initial.quoteLedgerNetUsdt.compareTo(BigDecimal.ZERO))
        val older = read(legacy)
        assertEquals("LEGACY_UNVERIFIED", older.status)
        assertNull(older.recordedInitialUsdt)
        assertNull(older.expectedAvailableUsdt)
        assertEquals(0, older.availableUsdt!!.compareTo(BigDecimal("123")))

        // A destructive or fraudulent balance modification must be reported,
        // not silently interpreted as a different funding amount.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                "UPDATE paper_balances SET available=available+5 WHERE wallet_id=? AND asset='USDT'"
            ).use { ps ->
                ps.setObject(1, wallet)
                assertEquals(1, ps.executeUpdate())
            }
            assertThrows(java.sql.SQLException::class.java) {
                conn.prepareStatement(
                    "UPDATE paper_wallet_funding SET initial_amount=999 WHERE wallet_id=?"
                ).use { ps ->
                    ps.setObject(1, wallet)
                    ps.executeUpdate()
                }
            }
            assertThrows(java.sql.SQLException::class.java) {
                conn.prepareStatement(
                    "DELETE FROM paper_wallet_funding WHERE wallet_id=?"
                ).use { ps ->
                    ps.setObject(1, wallet)
                    ps.executeUpdate()
                }
            }
        }
        val mismatch = read(wallet)
        assertEquals("MISMATCH", mismatch.status)
        assertEquals(0, mismatch.recordedInitialUsdt!!.compareTo(BigDecimal("250")))
        assertEquals(0, mismatch.availableUsdt!!.compareTo(BigDecimal("255")))
        assertEquals(0, mismatch.expectedAvailableUsdt!!.compareTo(BigDecimal("250")))
    }
}
