package com.trading.historical_service.paper

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * Transaction-bound read admission for private paper wallets.
 *
 * A valid capability must still match the wallet when PostgreSQL admits
 * each read. The FOR SHARE lock is held until the read transaction completes,
 * serializing it with M11's credential UPDATE for rotation and revocation.
 * No API token is persisted or included in read response bodies.
 *
 * This is not real-world user authentication or PostgreSQL RLS. Code that
 * bypasses this guard is outside its protection boundary.
 */
@Component
class PaperWalletReadGuard(
    private val db: DatabaseClient,
    transactionManager: ReactiveTransactionManager,
    @Value("\${M8_PAPER_LOCAL_INSECURE_ENABLED:false}")
    private val localInsecure: Boolean
) {
    private val transactions = TransactionalOperator.create(transactionManager)

    private fun lockCredential(walletId: UUID, authorization: String): Mono<Void> = Mono.defer {
        val token = if (authorization.startsWith("Bearer ")) {
            authorization.removePrefix("Bearer ")
        } else ""
        if (!PaperWalletTokens.isWellFormed(token)) {
            Mono.error<Void>(ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Wallet capability required"
            ))
        } else {
            db.sql(
                """
                SELECT wallet_id FROM paper_wallet_access
                WHERE wallet_id=:wallet
                  AND token_hash=:hash
                  AND revoked_at IS NULL
                FOR SHARE
                """.trimIndent()
            ).bind("wallet", walletId)
                .bind("hash", PaperWalletTokens.sha256(token))
                .map { row, _ -> requireNotNull(row.get("wallet_id", UUID::class.java)) }
                .one()
                .switchIfEmpty(Mono.error(ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Wallet capability is no longer active"
                )))
                .then()
        }
    }

    /** Protect one aggregate/query with a single read transaction. */
    fun <T : Any> mono(walletId: UUID, authorization: String, query: Mono<T>): Mono<T> =
        if (localInsecure) query
        else transactions.transactional(lockCredential(walletId, authorization).then(query))

    /** Protect a result stream until it completes or is cancelled. */
    fun <T : Any> flux(walletId: UUID, authorization: String, query: Flux<T>): Flux<T> =
        if (localInsecure) query
        else transactions.transactional(lockCredential(walletId, authorization).thenMany(query))
}
