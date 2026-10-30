package com.trading.historical_service.signals

import com.trading.historical_service.features.CandleSeries
import com.trading.historical_service.features.FeatureSnapshot
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.time.Duration

/**
 * Loads only real recorded feature observations (never backdates historical
 * candle backfills). Results may be too sparse to train or test a model.
 */
@Repository
class HistoricalEvaluationRepository(private val db: DatabaseClient) {
    fun load(symbol: String, asOfMs: Long): Mono<List<EvaluationExample>> {
        require(symbol in CandleSeries.spotSymbols)
        val snapshots = db.sql(
            """
            SELECT symbol, interval_trade, horizon_hours, feature_version,
                   candle_close_time_ms, available_at_ms, ema12, ema26, rsi14,
                   macd_line, macd_signal, macd_histogram, volatility20, volume_ratio20
            FROM technical_feature_snapshots
            WHERE symbol = :symbol AND available_at_ms <= :asOf
            ORDER BY available_at_ms, candle_close_time_ms
            """.trimIndent()
        ).bind("symbol", symbol).bind("asOf", asOfMs)
            .map { row, _ ->
                fun number(name: String) = (requireNotNull(row.get(name)) as Number)
                FeatureSnapshot(
                    symbol = requireNotNull(row.get("symbol", String::class.java)),
                    interval = requireNotNull(row.get("interval_trade", String::class.java)),
                    horizonHours = number("horizon_hours").toInt(),
                    featureVersion = requireNotNull(row.get("feature_version", String::class.java)),
                    candleCloseTimeMs = number("candle_close_time_ms").toLong(),
                    availableAtMs = number("available_at_ms").toLong(),
                    ema12 = number("ema12").toDouble(),
                    ema26 = number("ema26").toDouble(),
                    rsi14 = number("rsi14").toDouble(),
                    macdLine = number("macd_line").toDouble(),
                    macdSignal = number("macd_signal").toDouble(),
                    macdHistogram = number("macd_histogram").toDouble(),
                    volatility20 = number("volatility20").toDouble(),
                    volumeRatio20 = number("volume_ratio20").toDouble()
                )
            }.all().collectList()
        val candles = db.sql(
            """
            SELECT open_time, close_time, open, high, low, close, volume, trades
            FROM candles
            WHERE symbol = :symbol AND interval_trade = '240m'
              AND close_time <= :asOf
            ORDER BY close_time
            """.trimIndent()
        ).bind("symbol", symbol).bind("asOf", asOfMs)
            .map { row, _ ->
                fun number(name: String) = (requireNotNull(row.get(name)) as Number)
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
            }.all().collectList()
        return Mono.zip(snapshots, candles)
            .map { pair -> WalkForwardDataset.build(pair.t1, pair.t2) }
    }

    fun loadBlocking(symbol: String, asOfMs: Long): List<EvaluationExample> =
        load(symbol, asOfMs).block(Duration.ofSeconds(20)) ?: emptyList()
}
