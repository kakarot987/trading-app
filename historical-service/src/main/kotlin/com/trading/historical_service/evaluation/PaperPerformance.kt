package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Mark-to-market paper performance only; not an independently verified trading
 * strategy. Snapshots must be chronological, positive equity observations.
 */
data class PaperEquityPoint(
    val observedAtMs: Long,
    val equityUsdt: BigDecimal,
    val netExternalFlowUsdt: BigDecimal = BigDecimal.ZERO
)

data class PaperPerformanceSummary(
    val observations: Int,
    val startEquityUsdt: BigDecimal,
    val endEquityUsdt: BigDecimal,
    val absoluteChangeUsdt: BigDecimal,
    val maxDrawdownFraction: BigDecimal,
    val netReturnFraction: BigDecimal
)

object PaperPerformance {
    private val precision = MathContext.DECIMAL64

    /**
     * Reject capital deposits/withdrawals for now: simple equity returns are
     * misleading when external flows occur. Flow-adjusted TWR comes later.
     */
    fun summarize(points: List<PaperEquityPoint>): PaperPerformanceSummary {
        require(points.size >= 2) { "At least two marked equity observations required" }
        require(points.zipWithNext().all { (a,b) -> a.observedAtMs < b.observedAtMs }) {
            "Equity observation times must strictly increase"
        }
        require(points.all { it.observedAtMs >= 0 && it.equityUsdt.signum() > 0 }) {
            "Equity observations must be positive"
        }
        require(points.all { it.netExternalFlowUsdt.signum() == 0 }) {
            "External capital flows require flow-adjusted evaluation"
        }
        var peak = points.first().equityUsdt
        var maxDrawdown = BigDecimal.ZERO
        for (point in points) {
            if (point.equityUsdt > peak) peak = point.equityUsdt
            val drawdown = (peak - point.equityUsdt).divide(peak, precision)
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }
        val start = points.first().equityUsdt
        val end = points.last().equityUsdt
        return PaperPerformanceSummary(
            observations = points.size,
            startEquityUsdt = start,
            endEquityUsdt = end,
            absoluteChangeUsdt = end - start,
            maxDrawdownFraction = maxDrawdown.setScale(8, RoundingMode.HALF_UP),
            netReturnFraction = ((end - start).divide(start, precision)).setScale(8, RoundingMode.HALF_UP)
        )
    }
}
