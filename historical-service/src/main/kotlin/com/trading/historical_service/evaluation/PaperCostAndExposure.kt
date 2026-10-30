package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/** Evaluation of already-recorded simulated fills, not executable orders. */
data class EvaluatedPaperFill(
    val filledAtMs: Long,
    val symbol: String,
    val side: String,
    val quantity: BigDecimal,
    val executionPrice: BigDecimal,
    val referenceMidPrice: BigDecimal,
    val feeQuote: BigDecimal
)

data class PaperTradingCosts(
    val fills: Int,
    val grossTurnoverUsdt: BigDecimal,
    val feesUsdt: BigDecimal,
    val executionCostUsdt: BigDecimal,
    val totalEstimatedCostUsdt: BigDecimal
)

data class PaperExposure(
    val markedAtMs: Long,
    val equityUsdt: BigDecimal,
    val grossSpotExposureUsdt: BigDecimal,
    val exposureFraction: BigDecimal
)

object PaperCostAndExposure {
    private val context = MathContext.DECIMAL64
    private fun eight(value: BigDecimal) = value.setScale(8, RoundingMode.HALF_UP)

    /** Midpoint reference must be recorded at execution, not reconstructed from future prices. */
    fun summarizeCosts(fills: List<EvaluatedPaperFill>): PaperTradingCosts {
        require(fills.zipWithNext().all { (a, b) -> a.filledAtMs <= b.filledAtMs })
        require(fills.all {
            it.symbol in setOf("BTCUSDT", "ETHUSDT") &&
                it.side in setOf("BUY", "SELL") &&
                it.quantity.signum() > 0 && it.executionPrice.signum() > 0 &&
                it.referenceMidPrice.signum() > 0 && it.feeQuote.signum() >= 0
        })
        val turnover = fills.fold(BigDecimal.ZERO) { sum, fill ->
            sum + fill.quantity.multiply(fill.executionPrice)
        }
        val fees = fills.fold(BigDecimal.ZERO) { sum, fill -> sum + fill.feeQuote }
        val executionCost = fills.fold(BigDecimal.ZERO) { sum, fill ->
            val adversePriceDelta = if (fill.side == "BUY")
                fill.executionPrice - fill.referenceMidPrice
            else fill.referenceMidPrice - fill.executionPrice
            sum + adversePriceDelta.multiply(fill.quantity)
        }
        return PaperTradingCosts(fills.size, eight(turnover), eight(fees),
            eight(executionCost), eight(fees + executionCost))
    }

    /** Mark only spot positions with timestamp-appropriate observed marks. */
    fun exposure(markedAtMs: Long, cashUsdt: BigDecimal,
                 holdings: Map<String, BigDecimal>, marks: Map<String, BigDecimal>): PaperExposure {
        require(markedAtMs >= 0 && cashUsdt.signum() >= 0)
        require(holdings.keys.all { it in setOf("BTC", "ETH") } && holdings.values.all { it.signum() >= 0 })
        require(marks.keys.containsAll(holdings.filterValues { it.signum() > 0 }.keys))
        require(marks.values.all { it.signum() > 0 })
        val invested = holdings.entries.fold(BigDecimal.ZERO) { sum, (asset, quantity) ->
            sum + (if (quantity.signum() == 0) BigDecimal.ZERO
                   else quantity.multiply(requireNotNull(marks[asset])))
        }
        val equity = cashUsdt + invested
        require(equity.signum() > 0) { "Cannot calculate exposure with zero total equity" }
        return PaperExposure(markedAtMs, eight(equity), eight(invested),
            eight(invested.divide(equity, context)))
    }
}
