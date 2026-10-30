package com.trading.historical_service.paper

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * M6 spot-only simulation money model. No exchange connections or order routing.
 * USD(T) cash is kept to 8 decimals; BTC/ETH quantities to 8 decimals.
 * Reject silently rounded amounts and negative balances.
 */
object PaperDecimal {
    const val SCALE = 8
    val ZERO: BigDecimal = BigDecimal.ZERO.setScale(SCALE)

    fun exact(value: String): BigDecimal {
        val amount = try { BigDecimal(value).setScale(SCALE, RoundingMode.UNNECESSARY) }
            catch (e: ArithmeticException) { throw IllegalArgumentException("Maximum 8 decimal places", e) }
        require(amount.signum() >= 0) { "Negative paper balances are forbidden" }
        return amount
    }

    fun positive(value: String): BigDecimal = exact(value).also {
        require(it.signum() > 0) { "Paper order amount must be positive" }
    }

    /** Debit arithmetic is exact; an overdraft is rejected instead of clipped. */
    fun debit(balance: BigDecimal, amount: BigDecimal): BigDecimal {
        require(balance.scale() == SCALE && amount.scale() == SCALE)
        require(amount.signum() >= 0)
        return (balance - amount).also { require(it.signum() >= 0) { "Insufficient paper funds" } }
    }

    fun credit(balance: BigDecimal, amount: BigDecimal): BigDecimal {
        require(balance.scale() == SCALE && amount.scale() == SCALE)
        require(amount.signum() >= 0)
        return balance + amount
    }
}

enum class PaperOrderSide { BUY, SELL }
enum class PaperOrderStatus { NEW, FILLED, REJECTED, CANCELLED }

/** A simulation request, deliberately disconnected from live order execution. */
data class PaperOrderRequest(
    val clientOrderId: String,
    val symbol: String,
    val side: PaperOrderSide,
    val quantity: BigDecimal
) {
    init {
        require(clientOrderId.isNotBlank() && clientOrderId.length <= 128)
        require(symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(quantity.scale() == PaperDecimal.SCALE && quantity.signum() > 0)
    }
}
