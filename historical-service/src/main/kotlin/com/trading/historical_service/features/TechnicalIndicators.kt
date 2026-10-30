package com.trading.historical_service.features

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Pure, deterministic technical indicators. Input closes must be chronological
 * (oldest first); null means insufficient history rather than an invented value.
 */
object TechnicalIndicators {
    fun ema(values: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "EMA period must be positive" }
        require(values.all(Double::isFinite)) { "Non-finite price" }
        val result = MutableList<Double?>(values.size) { null }
        if (values.size < period) return result
        var previous = values.take(period).average()
        result[period - 1] = previous
        val alpha = 2.0 / (period + 1)
        for (i in period until values.size) {
            previous += alpha * (values[i] - previous)
            result[i] = previous
        }
        return result
    }

    /** Wilder RSI; neutral price movement yields 50, gains-only yields 100. */
    fun rsi(closes: List<Double>, period: Int = 14): List<Double?> {
        require(period > 0) { "RSI period must be positive" }
        require(closes.all { it.isFinite() && it > 0.0 }) { "Invalid closing price" }
        val result = MutableList<Double?>(closes.size) { null }
        if (closes.size <= period) return result
        var gains = 0.0
        var losses = 0.0
        for (i in 1..period) {
            val delta = closes[i] - closes[i - 1]
            gains += maxOf(delta, 0.0)
            losses += maxOf(-delta, 0.0)
        }
        var avgGain = gains / period
        var avgLoss = losses / period
        fun value(): Double = when {
            avgLoss == 0.0 && avgGain == 0.0 -> 50.0
            avgLoss == 0.0 -> 100.0
            else -> 100.0 - 100.0 / (1.0 + avgGain / avgLoss)
        }
        result[period] = value()
        for (i in period + 1 until closes.size) {
            val delta = closes[i] - closes[i - 1]
            avgGain = (avgGain * (period - 1) + maxOf(delta, 0.0)) / period
            avgLoss = (avgLoss * (period - 1) + maxOf(-delta, 0.0)) / period
            result[i] = value()
        }
        return result
    }

    data class MacdPoint(val line: Double?, val signal: Double?, val histogram: Double?)

    fun macd(closes: List<Double>, fast: Int = 12, slow: Int = 26, signal: Int = 9): List<MacdPoint> {
        require(fast > 0 && slow > fast && signal > 0) { "Invalid MACD periods" }
        val fastEma = ema(closes, fast)
        val slowEma = ema(closes, slow)
        val line = closes.indices.map { i ->
            if (fastEma[i] != null && slowEma[i] != null) fastEma[i]!! - slowEma[i]!! else null
        }
        val validLines = line.filterNotNull()
        val signals = ema(validLines, signal)
        val firstValid = slow - 1
        return closes.indices.map { i ->
            val l = line[i]
            val s = if (i >= firstValid) signals.getOrNull(i - firstValid) else null
            MacdPoint(l, s, if (l != null && s != null) l - s else null)
        }
    }

    /** Population standard deviation of log returns over the trailing window. */
    fun logReturnVolatility(closes: List<Double>, window: Int = 20): List<Double?> {
        require(window > 1) { "Volatility window must exceed one" }
        require(closes.all { it.isFinite() && it > 0.0 }) { "Invalid closing price" }
        val returns = closes.zipWithNext { a, b -> ln(b / a) }
        return closes.indices.map { i ->
            if (i < window) null else {
                val sample = returns.subList(i - window, i)
                val mean = sample.average()
                sqrt(sample.sumOf { (it - mean) * (it - mean) } / window)
            }
        }
    }

    fun volumeRatio(volumes: List<Double>, window: Int = 20): List<Double?> {
        require(window > 0) { "Volume window must be positive" }
        require(volumes.all { it.isFinite() && it >= 0.0 }) { "Invalid volume" }
        return volumes.indices.map { i ->
            if (i < window - 1) null else {
                val average = volumes.subList(i - window + 1, i + 1).average()
                if (average == 0.0) null else volumes[i] / average
            }
        }
    }
}
