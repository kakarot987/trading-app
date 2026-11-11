package com.trading.historical_service.paper

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID
import java.math.RoundingMode

/**
 * DB-side paper fill posting is atomic, wallet-serialized and idempotent.
 * Inputs are deterministic simulated fills, never exchange instructions.
 */
@Repository
class PaperFillRepository(private val db: DatabaseClient) {
    fun post(orderId: UUID, fill: PaperFill): Mono<Boolean> {
        require(fill.filledAtMs >= 0L)
        require(fill.clientOrderId.isNotBlank() && fill.clientOrderId.length <= 128)
        require(fill.symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(fill.quantity.signum() > 0 && fill.quantity.scale() == PaperDecimal.SCALE)
        require(fill.price.signum() > 0 && fill.price.scale() == PaperDecimal.SCALE)
        require(fill.grossQuote.signum() > 0 && fill.grossQuote.scale() == PaperDecimal.SCALE)
        require(fill.feeQuote.signum() >= 0 && fill.feeQuote.scale() == PaperDecimal.SCALE)
        require(fill.feeQuote <= fill.grossQuote)
        require(fill.grossQuote == (fill.quantity * fill.price)
            .setScale(PaperDecimal.SCALE, RoundingMode.HALF_UP))
        val expectedDebit = if (fill.side == PaperOrderSide.BUY)
            fill.grossQuote + fill.feeQuote else fill.feeQuote - fill.grossQuote
        require(fill.netQuoteDebit == expectedDebit) {
            "Fill quote debit must equal gross and fee movements"
        }
        return db.sql(
            """
            SELECT paper_post_fill(:orderId, :tickTime, :price, :gross, :fee) AS inserted
            FROM paper_orders
            WHERE order_id = :orderId AND client_order_id = :clientOrderId
              AND symbol = :symbol AND side = :side AND quantity = :quantity
            """.trimIndent()
        ).bind("orderId", orderId)
            .bind("clientOrderId", fill.clientOrderId)
            .bind("symbol", fill.symbol)
            .bind("side", fill.side.name)
            .bind("quantity", fill.quantity)
            .bind("tickTime", fill.filledAtMs)
            .bind("price", fill.price)
            .bind("gross", fill.grossQuote)
            .bind("fee", fill.feeQuote)
            .map { row, _ -> requireNotNull(row.get("inserted", java.lang.Boolean::class.java)).booleanValue() }
            .one()
            .switchIfEmpty(Mono.error(IllegalArgumentException(
                "Simulated fill does not match the persisted paper order"
            )))
    }
}
