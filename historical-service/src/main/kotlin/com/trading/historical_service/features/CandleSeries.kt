package com.trading.historical_service.features

import java.lang.Math.floorDiv

/** UTC epoch-millisecond candle boundaries; end is exclusive. */
object CandleSeries {
    const val FOUR_HOURS_MS: Long = 4L * 60 * 60 * 1000
    val spotSymbols = setOf("BTCUSDT", "ETHUSDT")

    data class Trade(
        val id: String,
        val timestampMs: Long,
        val price: Double,
        val volume: Double
    )

    data class Bar(
        val openTimeMs: Long,
        val closeTimeMs: Long,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
        val tradeCount: Int
    )

    /** Only complete UTC-aligned bars are eligible; missing intervals remain missing. */
    fun aggregateClosed(
        trades: List<Trade>,
        intervalMs: Long = FOUR_HOURS_MS,
        observedAtMs: Long
    ): List<Bar> {
        require(intervalMs > 0) { "Interval must be positive" }
        val cutoff = floorDiv(observedAtMs, intervalMs) * intervalMs
        require(trades.all { it.price.isFinite() && it.price > 0.0 &&
            it.volume.isFinite() && it.volume >= 0.0 && it.id.isNotBlank() }) {
            "Invalid trade"
        }
        return trades
            .asSequence()
            .filter { it.timestampMs >= 0 && it.timestampMs < cutoff }
            .distinctBy { it.id }
            .groupBy { floorDiv(it.timestampMs, intervalMs) * intervalMs }
            .toSortedMap()
            .map { (start, bucket) ->
                // Trade ID provides a deterministic tie break for equal timestamps.
                val ordered = bucket.sortedWith(compareBy<Trade> { it.timestampMs }.thenBy { it.id })
                Bar(
                    openTimeMs = start,
                    closeTimeMs = start + intervalMs,
                    open = ordered.first().price,
                    high = ordered.maxOf { it.price },
                    low = ordered.minOf { it.price },
                    close = ordered.last().price,
                    volume = ordered.sumOf { it.volume },
                    tradeCount = ordered.size
                )
            }
    }

    /** Explicit gaps prevent indicator computation over silently imputed bars. */
    fun missingOpenTimes(bars: List<Bar>, intervalMs: Long = FOUR_HOURS_MS): List<Long> {
        require(intervalMs > 0) { "Interval must be positive" }
        if (bars.size < 2) return emptyList()
        val ordered = bars.sortedBy { it.openTimeMs }
        require(ordered.zipWithNext().all { (a, b) -> b.openTimeMs > a.openTimeMs }) {
            "Duplicate or overlapping bars"
        }
        return ordered.zipWithNext().flatMap { (a, b) ->
            generateSequence(a.openTimeMs + intervalMs) { it + intervalMs }
                .takeWhile { it < b.openTimeMs }.toList()
        }
    }
}
