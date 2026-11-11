package com.trading.historical_service.paper

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Single-statement atomic CAS: only the current token may rotate/revoke.
 *
 * Audit insertion is in the SAME PostgreSQL statement as the state change.
 * If either fails, neither commits. No token, token digest, request header,
 * caller-supplied identity, or secret is written into the audit record.
 *
 * This is private-demo credential lifecycle, not identity authorization.
 */
@Repository
class PaperWalletCredentialLifecycle(private val db: DatabaseClient) {
    fun rotate(walletId: UUID, currentToken: String): Mono<String> = Mono.defer {
        if (!PaperWalletTokens.isWellFormed(currentToken)) return@defer Mono.empty<String>()
        val newToken = PaperWalletTokens.generate()
        val oldHash = PaperWalletTokens.sha256(currentToken)
        val newHash = PaperWalletTokens.sha256(newToken)
        // A no-op CAS on an outdated/revoked token has no audit side effects.
        db.sql(
            """
            WITH changed AS (
              UPDATE paper_wallet_access
              SET token_hash = :newHash,
                  credential_version = credential_version + 1
              WHERE wallet_id = :wallet
                AND token_hash = :oldHash
                AND revoked_at IS NULL
              RETURNING wallet_id, credential_version
            ), audited AS (
              INSERT INTO paper_wallet_credential_events (
                  wallet_id, event_type, credential_version
              )
              SELECT wallet_id, 'ROTATED', credential_version FROM changed
              RETURNING event_id
            )
            SELECT event_id FROM audited
            """.trimIndent()
        ).bind("wallet", walletId)
            .bind("oldHash", oldHash)
            .bind("newHash", newHash)
            .map { row, _ -> requireNotNull(row.get("event_id")) }
            .one().map { newToken }
    }

    fun revoke(walletId: UUID, currentToken: String): Mono<Boolean> = Mono.defer {
        if (!PaperWalletTokens.isWellFormed(currentToken)) return@defer Mono.just(false)
        db.sql(
            """
            WITH changed AS (
              UPDATE paper_wallet_access
              SET revoked_at = clock_timestamp(),
                  credential_version = credential_version + 1
              WHERE wallet_id = :wallet
                AND token_hash = :oldHash
                AND revoked_at IS NULL
              RETURNING wallet_id, credential_version
            ), audited AS (
              INSERT INTO paper_wallet_credential_events (
                  wallet_id, event_type, credential_version
              )
              SELECT wallet_id, 'REVOKED', credential_version FROM changed
              RETURNING event_id
            )
            SELECT event_id FROM audited
            """.trimIndent()
        ).bind("wallet", walletId)
            .bind("oldHash", PaperWalletTokens.sha256(currentToken))
            .map { row, _ -> requireNotNull(row.get("event_id")) }
            .one().map { true }.defaultIfEmpty(false)
    }
}
