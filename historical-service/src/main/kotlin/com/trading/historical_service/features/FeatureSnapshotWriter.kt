package com.trading.historical_service.features

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Preserves each observed feature set for later as-of backtests.
 * Replaying the same observation is idempotent; a later observation is a new row.
 */
@Service
class FeatureSnapshotWriter(private val databaseClient: DatabaseClient) {
    fun save(snapshot: FeatureSnapshot): Long {
        require(snapshot.symbol in CandleSeries.spotSymbols)
        require(snapshot.availableAtMs >= snapshot.candleCloseTimeMs)
        return databaseClient.sql(
            """
            INSERT INTO technical_feature_snapshots (
                symbol, interval_trade, horizon_hours, feature_version,
                candle_close_time_ms, available_at_ms, ema12, ema26, rsi14,
                macd_line, macd_signal, macd_histogram, volatility20, volume_ratio20
            ) VALUES (
                :symbol, :interval, :horizon, :version,
                :closeTime, :availableAt, :ema12, :ema26, :rsi14,
                :macdLine, :macdSignal, :macdHistogram, :volatility, :volumeRatio
            )
            ON CONFLICT (symbol, interval_trade, candle_close_time_ms, feature_version, available_at_ms)
            DO NOTHING
            """.trimIndent()
        ).bind("symbol", snapshot.symbol)
            .bind("interval", snapshot.interval)
            .bind("horizon", snapshot.horizonHours)
            .bind("version", snapshot.featureVersion)
            .bind("closeTime", snapshot.candleCloseTimeMs)
            .bind("availableAt", snapshot.availableAtMs)
            .bind("ema12", snapshot.ema12)
            .bind("ema26", snapshot.ema26)
            .bind("rsi14", snapshot.rsi14)
            .bind("macdLine", snapshot.macdLine)
            .bind("macdSignal", snapshot.macdSignal)
            .bind("macdHistogram", snapshot.macdHistogram)
            .bind("volatility", snapshot.volatility20)
            .bind("volumeRatio", snapshot.volumeRatio20)
            .fetch().rowsUpdated().block(Duration.ofSeconds(10))
            ?: error("Feature snapshot write returned no result")
    }
}
