package com.trading.historical_service.paper

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

/**
 * Reconciles a real persisted simulated funding record with subsequent
 * committed quote ledger movements. No external deposits or market marks
 * are invented by this read-only audit.
 */
data class PaperFundingAudit(
    val walletId: UUID,
    val status: String,
    val recordedInitialUsdt: BigDecimal?,
    val availableUsdt: BigDecimal?,
    val quoteLedgerNetUsdt: BigDecimal,
    val expectedAvailableUsdt: BigDecimal?
)

@Repository
class PaperFundingRepository(private val db: DatabaseClient) {
    fun audit(walletId: UUID): Mono<PaperFundingAudit> =
        db.sql(
            """
            SELECT w.wallet_id,
                   f.initial_amount AS initial_usdt,
                   b.available AS available_usdt,
                   COALESCE((
                     SELECT SUM(l.amount)
                     FROM paper_ledger_entries l
                     WHERE l.wallet_id = w.wallet_id AND l.asset = 'USDT'
                   ), 0) AS quote_net
            FROM paper_wallets w
            LEFT JOIN paper_wallet_funding f ON f.wallet_id = w.wallet_id
            LEFT JOIN paper_balances b
              ON b.wallet_id = w.wallet_id AND b.asset = 'USDT'
            WHERE w.wallet_id = :wallet
            """.trimIndent()
        ).bind("wallet", walletId)
            .map { row, _ ->
                val seed = row.get("initial_usdt", BigDecimal::class.java)
                val available = row.get("available_usdt", BigDecimal::class.java)
                val movements = requireNotNull(row.get("quote_net", BigDecimal::class.java))
                val expected = seed?.add(movements)
                val status = when {
                    seed == null -> "LEGACY_UNVERIFIED"
                    available != null && expected != null &&
                        expected.compareTo(available) == 0 -> "RECONCILED"
                    else -> "MISMATCH"
                }
                PaperFundingAudit(
                    requireNotNull(row.get("wallet_id", UUID::class.java)),
                    status, seed, available, movements, expected
                )
            }.one()
}
