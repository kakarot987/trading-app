package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

@Testcontainers
class PaperFillDatabaseIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun connection(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.sql(statement: String) {
        createStatement().use { it.execute(statement) }
    }

    private fun setup(conn: Connection) {
        // Isolated Testcontainers database: reset fixture schema between tests.
        conn.sql("DROP SCHEMA public CASCADE; CREATE SCHEMA public")
        for (migration in listOf(
            "V9__create_paper_trading_ledger.sql",
            "V10__atomic_paper_fill_posting.sql"
        )) {
            val ddl = javaClass.getResourceAsStream("/db/migration/$migration")!!
                .bufferedReader().readText()
            conn.sql(ddl)
        }
    }

    private fun Connection.number(sql: String): Long =
        createStatement().use { stmt ->
            stmt.executeQuery(sql).use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }

    private fun Connection.order(wallet: UUID, key: String, side: String = "BUY"): UUID {
        prepareStatement(
            """
            INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
            VALUES (?,?,'BTCUSDT',?,0.10000000,1000) RETURNING order_id
            """.trimIndent()
        ).use { stmt ->
            stmt.setObject(1, wallet)
            stmt.setString(2, key)
            stmt.setString(3, side)
            stmt.executeQuery().use { rs -> rs.next(); return rs.getObject(1, UUID::class.java) }
        }
    }

    private fun Connection.wallet(): UUID =
        createStatement().use { stmt ->
            stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use {
                it.next(); it.getObject(1, UUID::class.java)
            }
        }

    private fun Connection.fill(orderId: UUID): Boolean =
        prepareStatement("SELECT paper_post_fill(?,2000,100.04000000,10.00400000,0.01000400)")
            .use { stmt ->
                stmt.setObject(1, orderId)
                stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
            }

    @Test
    fun `atomic fill is idempotent and ledger cannot be modified`() {
        connection().use { conn ->
            setup(conn)
            val wallet = conn.wallet()
            conn.prepareStatement(
                "INSERT INTO paper_balances(wallet_id,asset,available) VALUES (?,'USDT',100)"
            ).use { it.setObject(1, wallet); it.executeUpdate() }
            val orderId = conn.order(wallet, "once")
            assertTrue(conn.fill(orderId))
            assertFalse(conn.fill(orderId))
            assertEquals(1L, conn.number("SELECT count(*) FROM paper_fills"))
            assertEquals(2L, conn.number("SELECT count(*) FROM paper_ledger_entries"))
            assertEquals(1L, conn.number("SELECT count(*) FROM paper_orders WHERE status='FILLED'"))
            conn.createStatement().use { stmt ->
                stmt.executeQuery("SELECT available FROM paper_balances WHERE asset='USDT'").use {
                    assertTrue(it.next())
                    assertEquals(0, it.getBigDecimal(1).compareTo(BigDecimal("89.98599600")))
                }
            }
            assertThrows(java.sql.SQLException::class.java) {
                conn.sql("UPDATE paper_ledger_entries SET amount=1")
            }
        }
    }

    @Test
    fun `insufficient funds rolls back fill and leaves order pending`() {
        connection().use { conn ->
            setup(conn)
            val wallet = conn.wallet()
            val orderId = conn.order(wallet, "insufficient")
            assertThrows(java.sql.SQLException::class.java) { conn.fill(orderId) }
            assertEquals(0L, conn.number("SELECT count(*) FROM paper_fills"))
            assertEquals(0L, conn.number("SELECT count(*) FROM paper_ledger_entries"))
            assertEquals(1L, conn.number("SELECT count(*) FROM paper_orders WHERE status='NEW'"))
            assertThrows(java.sql.SQLException::class.java) {
                conn.order(wallet, "insufficient")
            }
        }
    }
}
