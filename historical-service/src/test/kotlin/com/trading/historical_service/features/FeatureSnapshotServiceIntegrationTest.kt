package com.trading.historical_service.features

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager

/**
 * As-of reads must exclude future observations and foreign-source candles.
 * The disposable database uses the actual candle/provenance Flyway DDL.
 */
@Testcontainers
class FeatureSnapshotServiceIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("features_asof")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    private val interval = CandleSeries.FOUR_HOURS_MS

    @Test
    fun `as-of features only use observed local closed bars with no gaps`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { statement ->
                for (migration in listOf(
                    "/db/migration/V2__create_candles_table.sql",
                    "/db/migration/V6__candle_data_provenance.sql"
                )) {
                    val sql = requireNotNull(javaClass.getResourceAsStream(migration)) {
                        "Missing test migration $migration"
                    }.bufferedReader().use { it.readText() }
                    statement.execute(sql)
                }
            }
            connection.prepareStatement(
                """
                INSERT INTO candles (symbol, interval_trade, open_time, close_time,
                    open, high, low, close, volume, trades, buy_volume, notional_value,
                    data_source, source_observed_at_ms)
                VALUES (?, '240m', ?, ?, ?, ?, ?, ?, 10, 1, 5, 1000, ?, ?)
                """.trimIndent()
            ).use { statement ->
                fun add(symbol: String, index: Int, source: String, observedAt: Long?) {
                    val price = 100.0 + index
                    statement.setString(1, symbol)
                    statement.setLong(2, index * interval)
                    statement.setLong(3, (index + 1) * interval)
                    for (column in 4..7) statement.setDouble(column, price)
                    statement.setString(8, source)
                    if (observedAt == null) statement.setNull(9, java.sql.Types.BIGINT)
                    else statement.setLong(9, observedAt)
                    statement.addBatch()
                }
                // Contiguous, timely BTC bars. A later source revision must not
                // rewrite the earlier as-of feature calculation.
                for (i in 0 until 40) add("BTCUSDT", i, "local_trades", (i + 1) * interval)
                // A gap in observed ETH bars means indicators must not be emitted.
                for (i in 0 until 40) if (i != 20)
                    add("ETHUSDT", i, "local_trades", (i + 1) * interval)
                // External bars are never spliced into a local-trades history.
                add("ETHUSDT", 20, "binance_spot_klines", 21 * interval)
                // Missing observation provenance is unsafe for as-of evaluation.
                add("BTCUSDT", 40, "local_trades", null)
                statement.executeBatch()
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        )
        val service = FeatureSnapshotService(DatabaseClient.create(factory))
        val cutoff = 40 * interval
        val btc = service.computeLatest("BTCUSDT", cutoff)
        assertNotNull(btc, "Expected 40 historically observed contiguous candles")
        assertEquals(cutoff, btc!!.candleCloseTimeMs)
        assertEquals(cutoff, btc.availableAtMs)
        assertThrows(IllegalArgumentException::class.java) {
            service.computeLatest("ETHUSDT", cutoff)
        }
        // A candle with a close time after the cutoff and unknown provenance
        // may not contribute to an earlier calculation.
        assertEquals(btc, service.computeLatest("BTCUSDT", cutoff))

        // A late revision of a previously usable bar is no longer eligible
        // for the OLD as-of cutoff: future revisions must not leak backward.
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.prepareStatement(
                "UPDATE candles SET source_observed_at_ms = ? WHERE symbol = 'BTCUSDT' AND open_time = ?"
            ).use { statement ->
                statement.setLong(1, cutoff + 60_000)
                statement.setLong(2, 15 * interval)
                assertEquals(1, statement.executeUpdate())
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            service.computeLatest("BTCUSDT", cutoff)
        }

        // The revised observation becomes eligible only at its observation time;
        // no historical trade was fabricated to bridge the gap.
        val laterSnapshot = service.computeLatest("BTCUSDT", cutoff + 60_000)
        assertNotNull(laterSnapshot)
        assertEquals(cutoff, laterSnapshot!!.candleCloseTimeMs)
        assertEquals(cutoff + 60_000, laterSnapshot.availableAtMs)
    }
}
