package com.trading.historical_service.evaluation

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Reads the complete committed paper fill history and checks the two immutable
 * ledger movements per fill before calculating a realized-only position.
 * This is a read-only audit, not a market-marking service.
 */
@Repository
class PersistedPaperInventoryRepository(private val db: DatabaseClient) {
    fun summarize(walletId: UUID): Mono<List<PersistedRealizedInventory>> =
        db.sql(
            """
            SELECT f.fill_id, f.tick_time_ms, f.quantity, f.execution_price,
                   f.gross_quote, f.fee_quote, o.symbol, o.side, o.status,
                   o.submitted_at_ms,
                   base.amount AS base_delta, quote.amount AS quote_delta,
                   (SELECT count(*) FROM paper_ledger_entries l
                    WHERE l.fill_id = f.fill_id) AS ledger_count
            FROM paper_fills f
            JOIN paper_orders o ON o.order_id = f.order_id
            LEFT JOIN paper_ledger_entries base
              ON base.fill_id = f.fill_id
             AND base.wallet_id = o.wallet_id
             AND base.asset = replace(o.symbol, 'USDT', '')
            LEFT JOIN paper_ledger_entries quote
              ON quote.fill_id = f.fill_id
             AND quote.wallet_id = o.wallet_id
             AND quote.asset = 'USDT'
            WHERE o.wallet_id = :wallet
            ORDER BY f.tick_time_ms ASC, f.fill_id ASC
            """.trimIndent()
        ).bind("wallet", walletId)
            .map { row, _ ->
                fun dec(column: String) = requireNotNull(row.get(column, BigDecimal::class.java)) {
                    "Missing paper ledger field: $column"
                }
                val symbol = requireNotNull(row.get("symbol", String::class.java))
                val side = requireNotNull(row.get("side", String::class.java))
                val quantity = dec("quantity")
                val gross = dec("gross_quote")
                val fee = dec("fee_quote")
                val price = dec("execution_price")
                val submittedAt = (requireNotNull(row.get("submitted_at_ms")) as Number).toLong()
                val tick = (requireNotNull(row.get("tick_time_ms")) as Number).toLong()
                val ledgerCount = (requireNotNull(row.get("ledger_count")) as Number).toLong()
                require(row.get("status", String::class.java) == "FILLED" &&
                    ledgerCount == 2L && tick > submittedAt) {
                    "Unreconciled paper fill status, timing or ledger count"
                }
                require(price.signum() > 0 && gross ==
                    quantity.multiply(price).setScale(8, RoundingMode.HALF_UP)) {
                    "Paper fill gross does not match recorded execution price"
                }
                val expectedBase = if (side == "BUY") quantity else quantity.negate()
                val expectedQuote = if (side == "BUY") gross.negate() - fee else gross - fee
                require(dec("base_delta").compareTo(expectedBase) == 0 &&
                    dec("quote_delta").compareTo(expectedQuote) == 0) {
                    "Committed ledger movements do not match paper fill"
                }
                PersistedInventoryFill(
                    requireNotNull(row.get("fill_id", UUID::class.java)), tick, symbol,
                    side, quantity, gross, fee
                )
            }.all().collectList()
            .map(PaperRealizedInventory::summarize)
}
