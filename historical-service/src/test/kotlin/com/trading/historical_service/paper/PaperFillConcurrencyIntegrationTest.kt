package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Testcontainers
class PaperFillConcurrencyIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_concurrency")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun connection() = DriverManager.getConnection(
        postgres.jdbcUrl, postgres.username, postgres.password
    )

    private fun initialize(): Pair<UUID, UUID> {
        connection().use { conn ->
            conn.createStatement().use { stmt ->
                for (migration in listOf(
                    "V9__create_paper_trading_ledger.sql",
                    "V10__atomic_paper_fill_posting.sql"
                )) {
                    stmt.execute(javaClass.getResourceAsStream("/db/migration/$migration")!!
                        .bufferedReader().readText())
                }
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use { rs ->
                    rs.next()
                    val wallet = rs.getObject(1, UUID::class.java)
                    conn.prepareStatement(
                        "INSERT INTO paper_balances(wallet_id,asset,available) VALUES (?,'USDT',15)"
                    ).use { it.setObject(1, wallet); it.executeUpdate() }
                    conn.prepareStatement(
                        """
                        INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
                        VALUES (?,'concurrent-one','BTCUSDT','BUY',0.10000000,1000),
                               (?,'concurrent-two','BTCUSDT','BUY',0.10000000,1000)
                        RETURNING order_id
                        """.trimIndent()
                    ).use { ps ->
                        ps.setObject(1, wallet); ps.setObject(2, wallet)
                        ps.executeQuery().use { orders ->
                            orders.next(); val one = orders.getObject(1, UUID::class.java)
                            orders.next(); val two = orders.getObject(1, UUID::class.java)
                            return Pair(one, two)
                        }
                    }
                }
            }
        }
    }

    private fun post(orderId: UUID): Boolean = connection().use { conn ->
        conn.prepareStatement(
            "SELECT paper_post_fill(?,2000,100.04000000,10.00400000,0.01000400)"
        ).use { stmt ->
            stmt.setObject(1, orderId)
            stmt.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }
    }

    private fun count(table: String): Long = connection().use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT count(*) FROM $table").use {
                it.next(); it.getLong(1)
            }
        }
    }

    @Test
    fun `simultaneous paper orders cannot overspend wallet or duplicate ledger`() {
        val (one, two) = initialize()
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val tasks = listOf(one, two).map { id ->
                pool.submit(Callable {
                    gate.await()
                    try { post(id) } catch (_: SQLException) { false }
                })
            }
            gate.countDown()
            val results = tasks.map { it.get(30, TimeUnit.SECONDS) }
            assertEquals(1, results.count { it })
            assertEquals(1L, count("paper_fills"))
            assertEquals(2L, count("paper_ledger_entries"))
            assertFalse(post(if (results[0]) one else two))
            assertEquals(1L, count("paper_fills"))
        } finally {
            pool.shutdownNow()
        }
    }
}
