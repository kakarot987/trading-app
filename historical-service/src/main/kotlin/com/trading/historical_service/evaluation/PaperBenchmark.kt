package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Static, identical-window comparison only; no future prices or annualization. */
data class BenchmarkWindow(
    val startMs: Long,
    val endMs: Long,
    val startingEquityUsdt: BigDecimal,
    val endingEquityUsdt: BigDecimal,
    val startAssetPriceUsdt: BigDecimal,
    val endAssetPriceUsdt: BigDecimal,
    val asset: String,
    val netExternalFlowUsdt: BigDecimal = BigDecimal.ZERO,
    val entryCostRate: BigDecimal = BigDecimal.ZERO,
    val exitCostRate: BigDecimal = BigDecimal.ZERO
)

data class BenchmarkComparison(
    val asset: String,
    val startMs: Long,
    val endMs: Long,
    val strategyReturnFraction: BigDecimal,
    val buyHoldReturnFraction: BigDecimal,
    val excessReturnPercentagePoints: BigDecimal,
    val benchmarkEntryCostRate: BigDecimal = BigDecimal.ZERO,
    val benchmarkExitCostRate: BigDecimal = BigDecimal.ZERO
)

object PaperBenchmark {
    private val mc = MathContext.DECIMAL128
    private fun eight(v: BigDecimal) = v.setScale(8, RoundingMode.HALF_UP)

    /**
     * Assumes zero external flows, identical starting capital and window,
     * and a fully invested buy-and-hold benchmark. Optional explicit entry
     * and exit costs are applied to buy-and-hold; do not confuse assumed
     * costs with independently verified historical execution.
     */
    fun compare(window: BenchmarkWindow): BenchmarkComparison {
        require(window.asset in setOf("BTC", "ETH"))
        require(window.startMs >= 0 && window.endMs > window.startMs)
        require(window.startingEquityUsdt.signum() > 0 && window.endingEquityUsdt.signum() >= 0)
        require(window.startAssetPriceUsdt.signum() > 0 && window.endAssetPriceUsdt.signum() > 0)
        require(window.netExternalFlowUsdt.signum() == 0) {
            "Capital flows require flow-adjusted strategy returns"
        }
        require(listOf(window.entryCostRate, window.exitCostRate).all {
            it >= BigDecimal.ZERO && it < BigDecimal.ONE
        }) { "Benchmark execution costs must be rates in [0,1)" }
        val strategy = window.endingEquityUsdt.subtract(window.startingEquityUsdt)
            .divide(window.startingEquityUsdt, mc)
        val buyHoldEnd = window.startingEquityUsdt
            .multiply(window.endAssetPriceUsdt, mc)
            .multiply(BigDecimal.ONE - window.exitCostRate, mc)
            .divide(window.startAssetPriceUsdt.multiply(BigDecimal.ONE + window.entryCostRate, mc), mc)
        val buyHold = buyHoldEnd.subtract(window.startingEquityUsdt)
            .divide(window.startingEquityUsdt, mc)
        return BenchmarkComparison(window.asset, window.startMs, window.endMs,
            eight(strategy), eight(buyHold),
            eight(strategy.subtract(buyHold).multiply(BigDecimal("100"))),
            window.entryCostRate, window.exitCostRate)
    }
}
