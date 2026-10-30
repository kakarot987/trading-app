package com.trading.historical_service.paper

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * DB-side paper fill posting is atomic, wallet-serialized and idempotent.
 * Inputs are deterministic simulated fills, never exchange instructions.
 */
@Repository
class PaperFillRepository(private val db: DatabaseClient) {
    fun post(orderId: UUID, fill: PaperFill): Mono<Boolean> {
        require(fill.filledAtMs >= 0L)
        require(fill.price.signum() > 0)
        require(fill.grossQuote.signum() > 0 && fill.feeQuote.signum() >= 0)
        return db.sql(
            """
            SELECT paper_post_fill(:orderId, :tickTime, :price, :gross, :fee) AS inserted
            """.trimIndent()
        ).bind("orderId", orderId)
            .bind("tickTime", fill.filledAtMs)
            .bind("price", fill.price)
            .bind("gross", fill.grossQuote)
            .bind("fee", fill.feeQuote)
            .map { row, _ -> requireNotNull(row.get("inserted", java.lang.Boolean::class.java)).booleanValue() }
            .one()
    }
}
