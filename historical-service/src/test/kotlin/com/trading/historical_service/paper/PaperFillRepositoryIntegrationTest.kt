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

/**
 * End-to-end repository to SQL function acceptance on disposable PostgreSQL.
 * No actual exchange routing or external market data.
 */
@Testcontainers
class PaperFillRepositoryIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_repository")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test
    fun `typed fill matches stored order and replay must be identical`() {
        lateinit var wallet: UUID
        lateinit var orderId: UUID
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V10__atomic_paper_fill_posting.sql",
                    "V11__reject_conflicting_paper_fill_replay.sql"
                )) {
                    stmt.execute(requireNotNull(javaClass.getResourceAsStream("/db/migration/$migration"))
                        .bufferedReader().use { it.readText() })
                }
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use {
                    assertTrue(it.next())
                    wallet = it.getObject(1, UUID::class.java)
                }
            }
            conn.prepareStatement(
                "INSERT INTO paper_balances(wallet_id, asset, available) VALUES (?, 'USDT', 100)"
            ).use { ps ->
                ps.setObject(1, wallet)
                assertEquals(1, ps.executeUpdate())
            }
            conn.prepareStatement(
                """INSERT INTO paper_orders(wallet_id, client_order_id, symbol, side, quantity, submitted_at_ms)
                   VALUES (?, 'repository-fill', 'BTCUSDT', 'BUY', 0.10000000, 1000)
                   RETURNING order_id"""
            ).use { ps ->
                ps.setObject(1, wallet)
                ps.executeQuery().use {
                    assertTrue(it.next())
                    orderId = it.getObject(1, UUID::class.java)
                }
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val repository = PaperFillRepository(DatabaseClient.create(factory))
        val request = PaperOrderRequest("repository-fill", "BTCUSDT", PaperOrderSide.BUY,
            PaperDecimal.positive("0.10000000"))
        val fill = PaperFillEngine.fill(request, 1000,
            PaperMarketTick("BTCUSDT", 2000, BigDecimal("100")))
        fun post(candidate: PaperFill): Boolean =
            requireNotNull(repository.post(orderId, candidate).block(Duration.ofSeconds(10)))

        assertThrows(IllegalArgumentException::class.java) {
            repository.post(orderId, fill.copy(netQuoteDebit = PaperDecimal.ZERO))
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.post(orderId, fill.copy(grossQuote = PaperDecimal.exact("1")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            post(fill.copy(clientOrderId = "different-client"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            post(fill.copy(symbol = "ETHUSDT"))
        }
        assertTrue(post(fill))
        assertFalse(post(fill))
        assertThrows(RuntimeException::class.java) {
            post(fill.copy(filledAtMs = 2001))
        }
        // Reconnect to independently verify the committed balance and ledger.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT available FROM paper_balances WHERE wallet_id='$wallet' AND asset='USDT'").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getBigDecimal(1).compareTo(BigDecimal("89.98599600")))
                }
                stmt.executeQuery("SELECT count(*) FROM paper_fills WHERE order_id='$orderId'").use {
                    assertTrue(it.next())
                    assertEquals(1L, it.getLong(1))
                }
                stmt.executeQuery("SELECT count(*) FROM paper_ledger_entries WHERE wallet_id='$wallet'").use {
                    assertTrue(it.next())
                    assertEquals(2L, it.getLong(1))
                }
            }
        }
    }
}
