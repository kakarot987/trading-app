package com.trading.historical_service.evaluation

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/** Disposable DB proof: persisted fills -> posted ledger -> realized-only evaluation. */
@Testcontainers
class PersistedPaperInventoryRepositoryIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("paper_m7_evaluation")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private fun connect(): Connection =
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password)

    private fun Connection.migrate() {
        createStatement().use { stmt ->
            for (migration in listOf(
                "V9__create_paper_trading_ledger.sql",
                "V10__atomic_paper_fill_posting.sql",
                "V11__reject_conflicting_paper_fill_replay.sql"
            )) {
                stmt.execute(requireNotNull(javaClass.getResourceAsStream("/db/migration/$migration"))
                    .bufferedReader().use { it.readText() })
            }
        }
    }

    private fun Connection.order(wallet: UUID, client: String, side: String,
                                 submitted: Long): UUID =
        prepareStatement(
            """
            INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
            VALUES (?,?,'BTCUSDT',?,0.10000000,?) RETURNING order_id
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, wallet)
            ps.setString(2, client)
            ps.setString(3, side)
            ps.setLong(4, submitted)
            ps.executeQuery().use {
                assertTrue(it.next())
                it.getObject(1, UUID::class.java)
            }
        }

    private fun Connection.post(order: UUID, time: Long, price: String,
                                gross: String, fee: String) {
        prepareStatement("SELECT paper_post_fill(?,?,?,?,?)").use { ps ->
            ps.setObject(1, order)
            ps.setLong(2, time)
            ps.setBigDecimal(3, BigDecimal(price))
            ps.setBigDecimal(4, BigDecimal(gross))
            ps.setBigDecimal(5, BigDecimal(fee))
            ps.executeQuery().use {
                assertTrue(it.next())
                assertTrue(it.getBoolean(1))
            }
        }
    }

    @Test fun `actual paper buy sell reports realized amount without synthetic equity`() {
        lateinit var wallet: UUID
        lateinit var buy: UUID
        connect().use { conn ->
            conn.migrate()
            conn.createStatement().use { stmt ->
                stmt.executeQuery("INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id").use {
                    assertTrue(it.next())
                    wallet = it.getObject(1, UUID::class.java)
                }
            }
            conn.prepareStatement(
                "INSERT INTO paper_balances(wallet_id,asset,available) VALUES (?,'USDT',100)"
            ).use { ps ->
                ps.setObject(1, wallet)
                assertEquals(1, ps.executeUpdate())
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val inventory = PersistedPaperInventoryRepository(db)
        val controller = PaperEvaluationController(PersistedPaperActivityRepository(db), inventory)
        fun report(): PaperEvaluationReport = requireNotNull(
            controller.evaluation(wallet).block(Duration.ofSeconds(10))
        )

        val empty = report()
        assertEquals(0L, empty.activity.fillCount)
        assertEquals(emptyList<PersistedRealizedInventory>(), empty.realizedInventory)
        assertNull(empty.equity)
        connect().use { conn ->
            buy = conn.order(wallet, "m7-buy", "BUY", 1000)
            conn.post(buy, 2000, "100.04000000", "10.00400000", "0.01000400")
        }
        val open = report()
        assertEquals(1L, open.activity.fillCount)
        assertEquals(0, open.realizedInventory!!.single().realizedPnlUsdt.compareTo(BigDecimal.ZERO))
        assertEquals(0, open.realizedInventory!!.single().remainingCostBasisUsdt
            .compareTo(BigDecimal("10.01400400")))
        assertNull(open.equity)

        connect().use { conn ->
            val sell = conn.order(wallet, "m7-sell", "SELL", 3000)
            conn.post(sell, 4000, "110.00000000", "11.00000000", "0.01100000")
        }
        val closed = report()
        val position = closed.realizedInventory!!.single()
        assertEquals(2, position.fillCount)
        assertEquals(0, position.remainingQuantity.compareTo(BigDecimal.ZERO))
        assertEquals(0, position.remainingCostBasisUsdt.compareTo(BigDecimal.ZERO))
        assertEquals(0, position.realizedPnlUsdt.compareTo(BigDecimal("0.97499600")))
        assertEquals(0, closed.activity.quoteLedgerNetUsdt.compareTo(BigDecimal("0.97499600")))
        assertTrue(closed.evidence.contains(EvaluationEvidence.PERSISTED_REALIZED_INVENTORY))
        assertNull(closed.equity)
        assertNull(closed.intervalRisk)
        assertNull(closed.benchmark)
        assertTrue(closed.limitations.any { it.contains("contemporaneous observed marks") })

        // Append an unauthorized third ledger asset to a real fill: an audit
        // must fail, not silently assume a valid two-leg spot posting.
        connect().use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO paper_ledger_entries(wallet_id,fill_id,asset,amount)
                SELECT ?, fill_id, 'ETH', 0.01000000
                FROM paper_fills WHERE order_id=?
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, wallet)
                ps.setObject(2, buy)
                assertEquals(1, ps.executeUpdate())
            }
        }
        assertThrows(RuntimeException::class.java) {
            inventory.summarize(wallet).block(Duration.ofSeconds(10))
        }
    }
}
