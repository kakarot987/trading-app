package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.DriverManager
import java.util.UUID

/**
 * Simulates an application restart by closing all JDBC connections between
 * posting and replay. The PostgreSQL container stays up, retaining committed
 * state. This does not claim a full container-crash recovery test.
 */
@Testcontainers
class PaperFillReconnectIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_reconnect")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test fun `committed fill survives reconnect and replay cannot double debit`() {
        var walletId: UUID? = null
        var orderId: UUID? = null
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { stmt ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V10__atomic_paper_fill_posting.sql"
                )) {
                    stmt.execute(javaClass.getResourceAsStream("/db/migration/$migration")!!
                        .bufferedReader().readText())
                }
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use {
                    it.next(); walletId = it.getObject(1, UUID::class.java)
                }
            }
            conn.prepareStatement(
                "INSERT INTO paper_balances(wallet_id,asset,available) VALUES (?,'USDT',100)"
            ).use { it.setObject(1, walletId); it.executeUpdate() }
            conn.prepareStatement(
                """
                INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
                VALUES (?,'restart-key','BTCUSDT','BUY',0.10000000,1000)
                RETURNING order_id
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, walletId)
                ps.executeQuery().use { it.next(); orderId = it.getObject(1, UUID::class.java) }
            }
            conn.prepareStatement(
                "SELECT paper_post_fill(?,2000,100.04000000,10.00400000,0.01000400)"
            ).use { ps ->
                ps.setObject(1, orderId)
                ps.executeQuery().use { it.next(); assertTrue(it.getBoolean(1)) }
            }
        }

        // Fresh connection represents a restarted service observing durable DB state.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.prepareStatement(
                "SELECT paper_post_fill(?,2000,100.04000000,10.00400000,0.01000400)"
            ).use { ps ->
                ps.setObject(1, orderId)
                ps.executeQuery().use { it.next(); assertFalse(it.getBoolean(1)) }
            }
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT available FROM paper_balances WHERE wallet_id='$walletId' AND asset='USDT'").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getBigDecimal(1).compareTo(BigDecimal("89.98599600")))
                }
                stmt.executeQuery("SELECT count(*) FROM paper_fills WHERE order_id='$orderId'").use {
                    it.next(); assertEquals(1L, it.getLong(1))
                }
                stmt.executeQuery(
                    "SELECT count(*) FROM paper_ledger_entries WHERE wallet_id='$walletId'"
                ).use { it.next(); assertEquals(2L, it.getLong(1)) }
            }
        }
    }
}
