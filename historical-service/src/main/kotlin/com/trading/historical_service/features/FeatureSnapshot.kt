package com.trading.historical_service.features

/**
 * A feature is available only after its complete 4h candle closes. The
 * observed-at cutoff allows later backtests to exclude future information.
 */
data class FeatureSnapshot(
    val symbol: String,
    val interval: String,
    val horizonHours: Int,
    val featureVersion: String,
    val candleCloseTimeMs: Long,
    val availableAtMs: Long,
    val ema12: Double,
    val ema26: Double,
    val rsi14: Double,
    val macdLine: Double,
    val macdSignal: Double,
    val macdHistogram: Double,
    val volatility20: Double,
    val volumeRatio20: Double
)

object FeatureSnapshots {
    const val VERSION = "technical-4h-v1"

    /**
     * Refuses gaps: indicator rolling windows must never silently bridge
     * missing candle intervals. Late/revised candles can be recomputed and
     * produce a new snapshot with an updated availability timestamp.
     */
    fun latest(
        symbol: String,
        candles: List<CandleSeries.Bar>,
        observedAtMs: Long
    ): FeatureSnapshot? {
        require(symbol in CandleSeries.spotSymbols) { "Unsupported spot symbol: $symbol" }
        if (candles.isEmpty()) return null
        val bars = candles.sortedBy { it.openTimeMs }
        val interval = CandleSeries.FOUR_HOURS_MS
        require(bars.all {
            it.openTimeMs >= 0L &&
                it.openTimeMs % interval == 0L &&
                it.closeTimeMs == it.openTimeMs + interval &&
                it.close.isFinite() && it.close > 0 &&
                it.volume.isFinite() && it.volume >= 0
        }) { "Invalid or unaligned 4h candle" }
        require(bars.zipWithNext().all { (a, b) ->
            b.openTimeMs == a.closeTimeMs
        }) { "Missing or overlapping 4h candles" }
        // Complete history after the observation time must never be consumed.
        val closed = bars.takeWhile { it.closeTimeMs <= observedAtMs }
        if (closed.size < 35) return null
        val closes = closed.map { it.close }
        val volumes = closed.map { it.volume }
        val i = closed.lastIndex
        val macd = TechnicalIndicators.macd(closes)[i]
        return FeatureSnapshot(
            symbol = symbol,
            interval = "4h",
            horizonHours = 4,
            featureVersion = VERSION,
            candleCloseTimeMs = closed[i].closeTimeMs,
            availableAtMs = observedAtMs,
            ema12 = TechnicalIndicators.ema(closes, 12)[i] ?: return null,
            ema26 = TechnicalIndicators.ema(closes, 26)[i] ?: return null,
            rsi14 = TechnicalIndicators.rsi(closes)[i] ?: return null,
            macdLine = macd.line ?: return null,
            macdSignal = macd.signal ?: return null,
            macdHistogram = macd.histogram ?: return null,
            volatility20 = TechnicalIndicators.logReturnVolatility(closes)[i] ?: return null,
            volumeRatio20 = TechnicalIndicators.volumeRatio(volumes)[i] ?: return null
        )
    }
}
