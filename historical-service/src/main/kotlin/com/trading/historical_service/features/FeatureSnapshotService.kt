package com.trading.historical_service.features

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Reads only completed, local-trade 4h candles from PostgreSQL and refuses gaps.\n * An as-of query must not ingest observations or revisions from the future.
 * No feature is persisted until a complete input history is available.
 */
@Service
class FeatureSnapshotService(private val databaseClient: DatabaseClient) {
    fun computeLatest(symbol: String, observedAtMs: Long = System.currentTimeMillis()): FeatureSnapshot? {
        require(symbol in CandleSeries.spotSymbols) { "Unsupported spot symbol: $symbol" }
        val interval = CandleSeries.FOUR_HOURS_MS
        val lastClose = Math.floorDiv(observedAtMs, interval) * interval
        val rows = databaseClient.sql(
            """
            SELECT open_time, close_time, open, high, low, close, volume, trades
            FROM candles
            WHERE symbol = :symbol
              AND interval_trade = '240m'
              AND data_source = 'local_trades'
              AND close_time <= :lastClose
              -- Exclude candles revised or first observed after this as-of
              -- timestamp; unknown legacy provenance is not safe for backtests.
              AND source_observed_at_ms IS NOT NULL
              AND source_observed_at_ms <= :observedAt
            ORDER BY open_time DESC
            LIMIT 120
            """.trimIndent()
        ).bind("symbol", symbol).bind("lastClose", lastClose)
            .bind("observedAt", observedAtMs)
            .map { row, _ ->
                fun number(name: String) = requireNotNull(row.get(name) as? Number) {
                    "Missing candle column: $name"
                }
                CandleSeries.Bar(
                    openTimeMs = number("open_time").toLong(),
                    closeTimeMs = number("close_time").toLong(),
                    open = number("open").toDouble(),
                    high = number("high").toDouble(),
                    low = number("low").toDouble(),
                    close = number("close").toDouble(),
                    volume = number("volume").toDouble(),
                    tradeCount = number("trades").toInt()
                )
            }.all().collectList().block(Duration.ofSeconds(10)) ?: return null
        return FeatureSnapshots.latest(symbol, rows.reversed(), observedAtMs)
    }
}
