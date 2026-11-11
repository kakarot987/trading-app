package com.trading.historical_service.paper

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Fail-closed private wallet capability lookup. The shared bootstrap token
 * must never authorize requests to a particular wallet.
 *
 * DB outage / absent migration / revoked credential -> deny. No raw secret
 * is logged or sent to SQL. This is NOT an OIDC/JWT identity service.
 */
@Repository
class PaperWalletAccessStore(private val db: DatabaseClient) {
    fun authorized(walletId: UUID, token: String): Mono<Boolean> {
        if (!PaperWalletTokens.isWellFormed(token)) return Mono.just(false)
        return db.sql(
            """
            SELECT EXISTS (
                SELECT 1 FROM paper_wallet_access
                WHERE wallet_id = :wallet AND token_hash = :hash
                  AND revoked_at IS NULL
            ) AS permitted
            """.trimIndent()
        ).bind("wallet", walletId)
            .bind("hash", PaperWalletTokens.sha256(token))
            .map { row, _ ->
                java.lang.Boolean.TRUE.equals(row.get("permitted", java.lang.Boolean::class.java))
            }.one().defaultIfEmpty(false)
            .onErrorReturn(false)
    }
}
