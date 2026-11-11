package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.util.UUID

/**
 * Weighted-average cost basis from committed paper fills only.
 * No price marks are accepted here: realized P&L is not equity return and
 * remaining cost basis is NOT an estimate of unrealized P&L.
 */
data class PersistedInventoryFill(
    val fillId: UUID,
    val tickTimeMs: Long,
    val symbol: String,
    val side: String,
    val quantity: BigDecimal,
    val grossQuoteUsdt: BigDecimal,
    val feeQuoteUsdt: BigDecimal
)

data class PersistedRealizedInventory(
    val asset: String,
    val fillCount: Int,
    val remainingQuantity: BigDecimal,
    val remainingCostBasisUsdt: BigDecimal,
    val realizedPnlUsdt: BigDecimal,
    val netQuoteMovementUsdt: BigDecimal
)

object PaperRealizedInventory {
    private val mc = MathContext.DECIMAL128
    private fun eight(value: BigDecimal) = value.setScale(8, RoundingMode.HALF_UP)

    /** Input must be an entire chronological fill history for this wallet. */
    fun summarize(fills: List<PersistedInventoryFill>): List<PersistedRealizedInventory> {
        require(fills.map { it.fillId }.distinct().size == fills.size) { "Duplicate fill identifier" }
        require(fills.zipWithNext().all { (a, b) -> a.tickTimeMs <= b.tickTimeMs }) {
            "Paper fills must be chronological"
        }
        require(fills.all {
            it.tickTimeMs >= 0 && it.symbol in setOf("BTCUSDT", "ETHUSDT") &&
                it.side in setOf("BUY", "SELL") && it.quantity.signum() > 0 &&
                it.grossQuoteUsdt.signum() > 0 && it.feeQuoteUsdt.signum() >= 0 &&
                it.feeQuoteUsdt <= it.grossQuoteUsdt
        })
        return fills.groupBy { it.symbol.removeSuffix("USDT") }.toSortedMap().map { (asset, history) ->
            // Same-timestamp opposite-side fills have no verifiable ordering.
            // Refuse an order-dependent realized P&L instead of using UUID order.
            require(history.zipWithNext().none { (a, b) ->
                a.tickTimeMs == b.tickTimeMs && a.side != b.side
            }) { "Ambiguous buy/sell order at the same market timestamp" }
            var quantity = BigDecimal.ZERO
            var basis = BigDecimal.ZERO
            var realized = BigDecimal.ZERO
            var cashDelta = BigDecimal.ZERO
            for (fill in history) {
                if (fill.side == "BUY") {
                    quantity += fill.quantity
                    basis += fill.grossQuoteUsdt + fill.feeQuoteUsdt
                    cashDelta -= fill.grossQuoteUsdt + fill.feeQuoteUsdt
                } else {
                    require(fill.quantity <= quantity) {
                        "Sell lacks recorded prior inventory"
                    }
                    val releasedBasis = if (fill.quantity == quantity) basis
                    else basis.multiply(fill.quantity.divide(quantity, mc), mc)
                    realized += fill.grossQuoteUsdt - fill.feeQuoteUsdt - releasedBasis
                    cashDelta += fill.grossQuoteUsdt - fill.feeQuoteUsdt
                    quantity -= fill.quantity
                    basis -= releasedBasis
                }
            }
            PersistedRealizedInventory(
                asset, history.size, eight(quantity), eight(basis),
                eight(realized), eight(cashDelta)
            )
        }
    }
}
