package com.trading.historical_service.paper

import java.math.BigDecimal

/** Deterministic two-asset spot fill movements; caller must persist atomically. */
data class PaperMovement(val asset: String, val amount: BigDecimal)

object PaperLedgerPosting {
    fun movements(fill: PaperFill): List<PaperMovement> {
        require(fill.symbol in setOf("BTCUSDT", "ETHUSDT"))
        val base = fill.symbol.removeSuffix("USDT")
        val baseAmount = if (fill.side == PaperOrderSide.BUY) fill.quantity else fill.quantity.negate()
        val quoteAmount = fill.netQuoteDebit.negate()
        require(baseAmount.signum() != 0 && quoteAmount.signum() != 0)
        require(baseAmount.scale() == PaperDecimal.SCALE && quoteAmount.scale() == PaperDecimal.SCALE)
        return listOf(PaperMovement(base, baseAmount), PaperMovement("USDT", quoteAmount))
    }

    fun apply(
        balances: Map<String, BigDecimal>,
        movements: List<PaperMovement>
    ): Map<String, BigDecimal> {
        require(movements.map { it.asset }.distinct().size == movements.size)
        require(movements.all { it.asset in setOf("BTC", "ETH", "USDT") })
        val result = balances.toMutableMap()
        for (movement in movements) {
            val current = result[movement.asset] ?: PaperDecimal.ZERO
            require(current.scale() == PaperDecimal.SCALE)
            result[movement.asset] = if (movement.amount.signum() < 0)
                PaperDecimal.debit(current, movement.amount.negate())
            else PaperDecimal.credit(current, movement.amount)
        }
        return result.toMap()
    }
}
