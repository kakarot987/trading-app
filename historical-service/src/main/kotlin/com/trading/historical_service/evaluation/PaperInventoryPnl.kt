
package com.trading.historical_service.evaluation

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

data class InventoryFill(
    val timeMs: Long,
    val symbol: String,
    val side: String,
    val quantity: BigDecimal,
    val grossUsdt: BigDecimal,
    val feeUsdt: BigDecimal
)

data class InventoryPnlResult(
    val asset: String,
    val remainingQuantity: BigDecimal,
    val costBasisUsdt: BigDecimal,
    val realizedPnlUsdt: BigDecimal,
    val unrealizedPnlUsdt: BigDecimal
)

object PaperInventoryPnl {
    private val mc = MathContext.DECIMAL128

    fun calculate(
        asset: String,
        fills: List<InventoryFill>,
        markPrice: BigDecimal
    ): InventoryPnlResult {
        require(asset in setOf("BTC", "ETH"))
        require(markPrice > BigDecimal.ZERO)
        require(fills.zipWithNext().all { (a, b) ->
            a.timeMs <= b.timeMs
        })

        var quantity = BigDecimal.ZERO
        var costBasis = BigDecimal.ZERO
        var realized = BigDecimal.ZERO

        for (fill in fills) {
            require(fill.symbol == "${asset}USDT")
            require(fill.side in setOf("BUY", "SELL"))
            require(fill.timeMs >= 0)
            require(fill.quantity > BigDecimal.ZERO)
            require(fill.grossUsdt > BigDecimal.ZERO)
            require(fill.feeUsdt >= BigDecimal.ZERO)

            if (fill.side == "BUY") {
                quantity += fill.quantity
                costBasis += fill.grossUsdt + fill.feeUsdt
            } else {
                require(fill.quantity <= quantity) {
                    "Insufficient recorded inventory"
                }

                val releasedCost =
                    if (fill.quantity == quantity) costBasis
                    else costBasis.multiply(
                        fill.quantity.divide(quantity, mc),
                        mc
                    )

                realized += fill.grossUsdt -
                        fill.feeUsdt - releasedCost

                quantity -= fill.quantity
                costBasis -= releasedCost
            }
        }

        val unrealized = quantity.multiply(markPrice) - costBasis

        fun round(value: BigDecimal) =
            value.setScale(8, RoundingMode.HALF_UP)

        return InventoryPnlResult(
            asset = asset,
            remainingQuantity = round(quantity),
            costBasisUsdt = round(costBasis),
            realizedPnlUsdt = round(realized),
            unrealizedPnlUsdt = round(unrealized)
        )
    }
}
