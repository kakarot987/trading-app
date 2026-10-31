package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import kotlin.math.sqrt

/**
 * Descriptive per-observation risk statistics for completed equity marks.
 * Not annualized, not a Sharpe-ratio performance claim, and not forecast skill.
 */
data class PaperRiskSummary(
    val returnIntervals: Int,
    val meanIntervalReturn: BigDecimal,
    val sampleIntervalVolatility: BigDecimal?,
    val downsideDeviation: BigDecimal?,
    val positiveIntervals: Int,
    val negativeIntervals: Int
)

object PaperRiskMetrics {
    private val mc = MathContext.DECIMAL128
    private fun eight(v: BigDecimal) = v.setScale(8, RoundingMode.HALF_UP)

    fun summarize(points: List<PaperEquityPoint>): PaperRiskSummary {
        require(points.size >= 2) { "At least two observations required" }
        require(points.zipWithNext().all { (a, b) -> a.observedAtMs < b.observedAtMs })
        require(points.all { it.equityUsdt.signum() > 0 && it.observedAtMs >= 0 })
        require(points.all { it.netExternalFlowUsdt.signum() == 0 }) {
            "Capital flows require flow-adjusted return observations"
        }
        val returns = points.zipWithNext().map { (a, b) ->
            b.equityUsdt.subtract(a.equityUsdt).divide(a.equityUsdt, mc)
        }
        val mean = returns.fold(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal(returns.size), mc)
        val vol = if (returns.size < 2) null else {
            val variance = returns.fold(BigDecimal.ZERO) { sum, value ->
                val deviation = value - mean
                sum + deviation.multiply(deviation)
            }.divide(BigDecimal(returns.size - 1), mc)
            eight(BigDecimal.valueOf(sqrt(variance.toDouble())))
        }
        // Zero target per observation, population-root mean squared shortfall.
        val downsideSum = returns.fold(BigDecimal.ZERO) { sum, value ->
            if (value.signum() >= 0) sum else sum + value.multiply(value)
        }
        val downside = eight(BigDecimal.valueOf(sqrt(
            downsideSum.divide(BigDecimal(returns.size), mc).toDouble())))
        return PaperRiskSummary(returns.size, eight(mean), vol, downside,
            returns.count { it.signum() > 0 }, returns.count { it.signum() < 0 })
    }
}
