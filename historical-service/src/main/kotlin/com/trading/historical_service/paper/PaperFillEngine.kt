package com.trading.historical_service.paper

import java.math.BigDecimal
import java.math.RoundingMode

/** Fixed configuration for deterministic simulation; rates are fractions. */
data class PaperExecutionCosts(
    val feeRate: BigDecimal = BigDecimal("0.001"),
    val spreadRate: BigDecimal = BigDecimal("0.0002"),
    val slippageRate: BigDecimal = BigDecimal("0.0003")
) {
    init {
        require(listOf(feeRate, spreadRate, slippageRate).all {
            it.signum() >= 0 && it < BigDecimal.ONE
        })
    }
}

data class PaperMarketTick(val symbol: String, val timestampMs: Long, val midPrice: BigDecimal) {
    init {
        require(symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(timestampMs >= 0)
        require(midPrice.signum() > 0)
    }
}

data class PaperFill(
    val clientOrderId: String,
    val symbol: String,
    val side: PaperOrderSide,
    val filledAtMs: Long,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val grossQuote: BigDecimal,
    val feeQuote: BigDecimal,
    val netQuoteDebit: BigDecimal
)

/**
 * Spot-only research fill; cannot use a market tick at/before the order time.
 * Fees are paid in quote currency on both sides. Debits include fees on buys;
 * sells credit grossQuote minus feeQuote (negative netQuoteDebit).
 */
object PaperFillEngine {
    private val TWO = BigDecimal("2")
    private val ONE = BigDecimal.ONE

    fun fill(
        order: PaperOrderRequest,
        submittedAtMs: Long,
        tick: PaperMarketTick,
        costs: PaperExecutionCosts = PaperExecutionCosts()
    ): PaperFill {
        require(submittedAtMs >= 0 && tick.timestampMs > submittedAtMs) {
            "Fill tick must occur strictly after order submission"
        }
        require(tick.symbol == order.symbol)
        val adverseRate = costs.spreadRate.divide(TWO).add(costs.slippageRate)
        val multiplier = if (order.side == PaperOrderSide.BUY) ONE + adverseRate else ONE - adverseRate
        require(multiplier.signum() > 0)
        val price = (tick.midPrice * multiplier).setScale(PaperDecimal.SCALE, RoundingMode.HALF_UP)
        require(price.signum() > 0)
        val gross = (order.quantity * price).setScale(PaperDecimal.SCALE, RoundingMode.HALF_UP)
        val fee = (gross * costs.feeRate).setScale(PaperDecimal.SCALE, RoundingMode.HALF_UP)
        val debit = if (order.side == PaperOrderSide.BUY) gross + fee else fee - gross
        return PaperFill(order.clientOrderId, order.symbol, order.side,
            tick.timestampMs, order.quantity, price, gross, fee, debit)
    }

    fun fillAtFirstFutureTick(
        order: PaperOrderRequest,
        submittedAtMs: Long,
        ticks: List<PaperMarketTick>,
        costs: PaperExecutionCosts = PaperExecutionCosts()
    ): PaperFill? = ticks.asSequence()
        .filter { it.symbol == order.symbol && it.timestampMs > submittedAtMs }
        .sortedWith(compareBy<PaperMarketTick> { it.timestampMs }.thenBy { it.midPrice })
        .firstOrNull()?.let { fill(order, submittedAtMs, it, costs) }
}
