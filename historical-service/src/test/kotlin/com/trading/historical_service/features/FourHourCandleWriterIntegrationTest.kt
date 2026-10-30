package com.trading.historical_service.features

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration

@Testcontainers
class FourHourCandleWriterIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("trades")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test
    fun `closed four-hour candle is upserted and recomputed for late trades without duplicates`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { sql ->
                sql.execute("""
                    CREATE TABLE trades (
                        trade_id VARCHAR(64) PRIMARY KEY,
                        symbol VARCHAR(16) NOT NULL,
                        exchange VARCHAR(32),
                        price NUMERIC(18,8) NOT NULL,
                        volume NUMERIC(18,8) NOT NULL,
                        trade_timestamp BIGINT NOT NULL,
                        side VARCHAR(4),
                        processed_at BIGINT NOT NULL,
                        latency_ms BIGINT NOT NULL
                    )
                """.trimIndent())
                sql.execute("""
                    CREATE TABLE candles (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        symbol VARCHAR(16) NOT NULL,
                        interval_trade VARCHAR(10) NOT NULL,
                        open_time BIGINT NOT NULL,
                        close_time BIGINT NOT NULL,
                        open NUMERIC(18,8) NOT NULL,
                        high NUMERIC(18,8) NOT NULL,
                        low NUMERIC(18,8) NOT NULL,
                        close NUMERIC(18,8) NOT NULL,
                        volume NUMERIC(18,8) NOT NULL,
                        trades INTEGER NOT NULL,
                        buy_volume NUMERIC(18,8) NOT NULL,
                        notional_value NUMERIC(18,8) NOT NULL,
                        UNIQUE (symbol, interval_trade, open_time)
                    )
                """.trimIndent())
            }
        }
        val factory = PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName)
                .username(postgres.username).password(postgres.password).build()
        )
        val db = DatabaseClient.create(factory)
        val interval = CandleSeries.FOUR_HOURS_MS
        fun insert(id: String, timestamp: Long, price: Int) {
            db.sql("""
                INSERT INTO trades (trade_id, symbol, exchange, price, volume,
                    trade_timestamp, side, processed_at, latency_ms)
                VALUES (:id, 'BTCUSDT', 'BINANCE', :price, 2, :timestamp, 'BUY', :timestamp, 0)
            """.trimIndent())
                .bind("id", id).bind("price", price).bind("timestamp", timestamp)
                .fetch().rowsUpdated().block(Duration.ofSeconds(5))
        }
        fun count(): Long = db.sql("SELECT COUNT(*) AS n FROM candles")
            .map { row, _ -> (row.get("n") as Number).toLong() }
            .one().block(Duration.ofSeconds(5))!!
        fun candle(name: String): Double = db.sql(
            "SELECT $name AS value FROM candles WHERE symbol = 'BTCUSDT' AND open_time = 0"
        ).map { row, _ -> (row.get("value") as Number).toDouble() }
            .one().block(Duration.ofSeconds(5))!!

        insert("trade-a", 1000, 100)
        insert("trade-b", 2000, 110)
        insert("still-open", interval + 1000, 900)
        val writer = FourHourCandleWriter(db)

        writer.refresh("BTCUSDT", interval + 1000)
        assertEquals(1L, count())
        assertEquals(100.0, candle("open"))
        assertEquals(110.0, candle("close"))
        assertEquals(4.0, candle("volume"))

        insert("late", 1500, 120)
        writer.refresh("BTCUSDT", interval + 1000)
        writer.refresh("BTCUSDT", interval + 1000)
        assertEquals(1L, count(), "Refresh must upsert, not duplicate")
        assertEquals(120.0, candle("high"))
        assertEquals(110.0, candle("close"))
        assertEquals(6.0, candle("volume"))
        assertEquals(3.0, candle("trades"))
    }
}
