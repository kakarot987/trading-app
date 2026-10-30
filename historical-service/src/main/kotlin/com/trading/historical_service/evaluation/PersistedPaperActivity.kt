package com.trading.historical_service.evaluation

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

/**
 * Auditable realized activity from committed paper fills. Turnover and fees
 * are known; net profit is NOT inferable without inventory cost basis and
 * contemporaneous marks. Do not label these amounts as strategy P&L.
 */
data class PersistedPaperActivity(
    val fillCount: Long,
    val tradedNotionalUsdt: BigDecimal,
    val paidFeesUsdt: BigDecimal,
    val quoteLedgerNetUsdt: BigDecimal
)

@Repository
class PersistedPaperActivityRepository(private val db: DatabaseClient) {
    fun summarize(walletId: UUID): Mono<PersistedPaperActivity> =
        db.sql(
            """
            SELECT
              COUNT(f.fill_id) AS fill_count,
              COALESCE(SUM(f.gross_quote), 0) AS traded_notional,
              COALESCE(SUM(f.fee_quote), 0) AS paid_fees,
              COALESCE((
                SELECT SUM(l.amount)
                FROM paper_ledger_entries l
                WHERE l.wallet_id = :wallet AND l.asset = 'USDT'
              ), 0) AS quote_net
            FROM paper_fills f
            JOIN paper_orders o ON o.order_id = f.order_id
            WHERE o.wallet_id = :wallet
            """.trimIndent()
        ).bind("wallet", walletId)
            .map { row, _ ->
                PersistedPaperActivity(
                    fillCount = (requireNotNull(row.get("fill_count")) as Number).toLong(),
                    tradedNotionalUsdt = requireNotNull(row.get("traded_notional", BigDecimal::class.java)),
                    paidFeesUsdt = requireNotNull(row.get("paid_fees", BigDecimal::class.java)),
                    quoteLedgerNetUsdt = requireNotNull(row.get("quote_net", BigDecimal::class.java))
                )
            }.one()
}
