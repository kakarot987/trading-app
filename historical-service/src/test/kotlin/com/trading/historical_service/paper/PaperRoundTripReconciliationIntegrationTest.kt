package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

/**
 * Integration-level buy/sell reconciliation after JDBC reconnect.
 * PostgreSQL remains running; this is NOT a process/container crash test.
 * All transactions are against an isolated disposable Testcontainers DB.
 */
@Testcontainers
class PaperRoundTripReconciliationIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_roundtrip")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.migrate() {
        for (migration in listOf(
            "V9__create_paper_trading_ledger.sql",
            "V10__atomic_paper_fill_posting.sql",
                    "V11__reject_conflicting_paper_fill_replay.sql"
        )) {
            val ddl = javaClass.getResourceAsStream("/db/migration/$migration")!!
                .bufferedReader().use { it.readText() }
            createStatement().use { it.execute(ddl) }
        }
    }

    private fun Connection.newOrder(wallet: UUID, clientKey: String, side: String, submittedAt: Long): UUID {
        prepareStatement(
            """
            INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
            VALUES (?,?,'BTCUSDT',?,0.10000000,?)
            RETURNING order_id
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, wallet)
            stmt.setString(2, clientKey)
            stmt.setString(3, side)
            stmt.setLong(4, submittedAt)
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                return rs.getObject(1, UUID::class.java)
            }
        }
    }

    private fun Connection.post(orderId: UUID, tickTime: Long, price: String, gross: String, fee: String): Boolean =
        prepareStatement("SELECT paper_post_fill(?,?,?,?,?)").use { stmt ->
            stmt.setObject(1, orderId)
            stmt.setLong(2, tickTime)
            stmt.setBigDecimal(3, BigDecimal(price))
            stmt.setBigDecimal(4, BigDecimal(gross))
            stmt.setBigDecimal(5, BigDecimal(fee))
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                rs.getBoolean(1)
            }
        }

    private fun Connection.amount(sql: String, wallet: UUID, asset: String): BigDecimal =
        prepareStatement(sql).use { stmt ->
            stmt.setObject(1, wallet)
            stmt.setString(2, asset)
            stmt.executeQuery().use { rs ->
                assertTrue(rs.next())
                rs.getBigDecimal(1)
            }
        }

    private fun assertAmount(expected: String, actual: BigDecimal) {
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    @Test
    fun `buy and sell reconcile to signed ledger after reconnect and replay`() {
        lateinit var wallet: UUID
        lateinit var buy: UUID
        lateinit var sell: UUID
        connection().use { conn ->
            conn.migrate()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use { rs ->
                    assertTrue(rs.next())
                    wallet = rs.getObject(1, UUID::class.java)
                }
            }
            conn.prepareStatement(
                "INSERT INTO paper_balances(wallet_id,asset,available) VALUES (?,'USDT',100.00000000)"
            ).use { stmt ->
                stmt.setObject(1, wallet)
                assertEquals(1, stmt.executeUpdate())
            }
            buy = conn.newOrder(wallet, "roundtrip-buy", "BUY", 1000)
            assertTrue(conn.post(buy, 2000, "100.04000000", "10.00400000", "0.01000400"))
        }

        // First new connection observes BUY state and submits a later SELL.
        connection().use { conn ->
            assertAmount(
                "0.10000000",
                conn.amount("SELECT available FROM paper_balances WHERE wallet_id=? AND asset=?", wallet, "BTC")
            )
            assertAmount(
                "89.98599600",
                conn.amount("SELECT available FROM paper_balances WHERE wallet_id=? AND asset=?", wallet, "USDT")
            )
            assertFalse(conn.post(buy, 2000, "100.04000000", "10.00400000", "0.01000400"))
            sell = conn.newOrder(wallet, "roundtrip-sell", "SELL", 3000)
            assertTrue(conn.post(sell, 4000, "110.00000000", "11.00000000", "0.01100000"))
        }

        // A second reconnect verifies persisted portfolio balances equal the
        // opening balances plus signed immutable ledger movements.
        connection().use { conn ->
            assertFalse(conn.post(buy, 2000, "100.04000000", "10.00400000", "0.01000400"))
            assertFalse(conn.post(sell, 4000, "110.00000000", "11.00000000", "0.01100000"))
            val balanceSql = "SELECT available FROM paper_balances WHERE wallet_id=? AND asset=?"
            val ledgerSql = """
                SELECT COALESCE(SUM(amount),0) FROM paper_ledger_entries
                WHERE wallet_id=? AND asset=?
            """.trimIndent()
            assertAmount("0.00000000", conn.amount(balanceSql, wallet, "BTC"))
            assertAmount("100.97499600", conn.amount(balanceSql, wallet, "USDT"))
            assertAmount("0.00000000", conn.amount(ledgerSql, wallet, "BTC"))
            assertAmount("0.97499600", conn.amount(ledgerSql, wallet, "USDT"))

            conn.prepareStatement(
                "SELECT count(*) FROM paper_orders WHERE wallet_id=? AND status='FILLED'"
            ).use { stmt ->
                stmt.setObject(1, wallet)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(2L, rs.getLong(1))
                }
            }
            conn.prepareStatement(
                """
                SELECT count(*) AS fill_count,
                       COALESCE(SUM(f.gross_quote),0) AS turnover,
                       COALESCE(SUM(f.fee_quote),0) AS fees
                FROM paper_fills f
                JOIN paper_orders o ON o.order_id=f.order_id
                WHERE o.wallet_id=?
                """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, wallet)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(2L, rs.getLong("fill_count"))
                    assertAmount("21.00400000", rs.getBigDecimal("turnover"))
                    assertAmount("0.02100400", rs.getBigDecimal("fees"))
                }
            }
            conn.prepareStatement(
                "SELECT count(*) FROM paper_ledger_entries WHERE wallet_id=?"
            ).use { stmt ->
                stmt.setObject(1, wallet)
                stmt.executeQuery().use { rs ->
                    assertTrue(rs.next())
                    assertEquals(4L, rs.getLong(1))
                }
            }
        }
    }
}
