package com.trading.historical_service.paper

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

data class CreatePaperWalletRequest(val initialUsdt: String = "10000.00000000")
data class CreatePaperWalletResponse(val walletId: UUID, val walletToken: String)
data class SubmitPaperOrderRequest(
    val clientOrderId: String, val symbol: String,
    val side: PaperOrderSide, val quantity: String, val submittedAtMs: Long
)
data class SubmitPaperOrderResponse(val orderId: UUID, val status: String)

/**
 * Local-only simulated order staging. No exchange routing or auto-fills.
 * Mutations are disabled unless M6_PAPER_COMMANDS_ENABLED=true.
 * Never expose on a public network without authentication/ownership checks.
 */
@RestController
@RequestMapping("/api/v1/paper")
class PaperCommandController(
    private val db: DatabaseClient,
    @Value("\${M6_PAPER_COMMANDS_ENABLED:false}") private val enabled: Boolean,
    @Value("\${M8_PAPER_LOCAL_INSECURE_ENABLED:false}") private val localInsecure: Boolean = false
) {
    private fun requireEnabled() {
        if (!enabled) throw ResponseStatusException(HttpStatus.FORBIDDEN, "Paper commands disabled")
    }

    @PostMapping("/wallets")
    fun createWallet(@RequestBody request: CreatePaperWalletRequest): Mono<CreatePaperWalletResponse> {
        requireEnabled()
        val initial = try { PaperDecimal.exact(request.initialUsdt) }
        catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
        // Provision a unique scoped secret. Only the digest is stored, and the
        // raw credential is returned exactly once with Cache-Control: no-store
        // supplied by the paper API filter. Never log walletToken.
        val walletToken = PaperWalletTokens.generate()
        return db.sql(
            """
            WITH wallet AS (INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id),
            initial AS (
              INSERT INTO paper_balances(wallet_id,asset,available)
              SELECT wallet_id,'USDT',:initial FROM wallet RETURNING wallet_id
            ), access AS (
              INSERT INTO paper_wallet_access(wallet_id,token_hash)
              SELECT wallet_id,:tokenHash FROM wallet RETURNING wallet_id
            )
            SELECT wallet_id FROM access
            """.trimIndent()
        ).bind("initial", initial)
            .bind("tokenHash", PaperWalletTokens.sha256(walletToken))
            .map { row, _ -> CreatePaperWalletResponse(
                requireNotNull(row.get("wallet_id", UUID::class.java)), walletToken
            ) }.one()
    }

    @PostMapping("/wallets/{walletId}/orders")
    fun submit(
        @PathVariable walletId: UUID,
        @RequestBody request: SubmitPaperOrderRequest,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false)
        authorization: String = ""
    ): Mono<SubmitPaperOrderResponse> {
        requireEnabled()
        val order = try {
            PaperOrderRequest(request.clientOrderId, request.symbol, request.side,
                PaperDecimal.positive(request.quantity))
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, e.message)
        }
        if (request.submittedAtMs < 0 || request.submittedAtMs > System.currentTimeMillis()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid submission time")
        }
        // A WebFilter check alone is not sufficient: another request may
        // revoke/rotate the credential between filtering and order creation.
        // The secure path verifies possession again INSIDE the insert statement
        // while holding a PostgreSQL FOR SHARE lock on that wallet's access row.
        // A concurrent lifecycle UPDATE must wait until this statement commits.
        val tokenHash = if (localInsecure) null else {
            val token = if (authorization.startsWith("Bearer ")) {
                authorization.removePrefix("Bearer ")
            } else ""
            if (!PaperWalletTokens.isWellFormed(token)) {
                throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wallet credential required")
            }
            PaperWalletTokens.sha256(token)
        }
        val accessSql = if (localInsecure) {
            "SELECT CAST(:wallet AS uuid) AS wallet_id"
        } else {
            """
            SELECT wallet_id FROM paper_wallet_access
            WHERE wallet_id=:wallet AND token_hash=:hash AND revoked_at IS NULL
            FOR SHARE
            """.trimIndent()
        }
        fun accessQuery(sql: String): DatabaseClient.GenericExecuteSpec {
            val spec = db.sql(sql).bind("wallet", walletId)
            return if (tokenHash == null) spec else spec.bind("hash", tokenHash)
        }

        // An old, revoked, cross-wallet or missing token must insert nothing.
        // The idempotent-conflict fallback also rechecks authorization rather
        // than leaking an existing order to a stale credential.
        return accessQuery(
            """
            WITH authorized AS MATERIALIZED ($accessSql)
            INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
            SELECT wallet_id,:client,:symbol,:side,:quantity,:submitted FROM authorized
            WHERE TRUE
            ON CONFLICT(wallet_id,client_order_id) DO NOTHING
            RETURNING order_id,status
            """.trimIndent()
        ).bind("client", order.clientOrderId)
            .bind("symbol", order.symbol).bind("side", order.side.name)
            .bind("quantity", order.quantity).bind("submitted", request.submittedAtMs)
            .map { row, _ -> SubmitPaperOrderResponse(
                requireNotNull(row.get("order_id", UUID::class.java)),
                requireNotNull(row.get("status", String::class.java))
            ) }.one().switchIfEmpty(
                accessQuery(
                    """
                    WITH authorized AS MATERIALIZED ($accessSql)
                    SELECT o.order_id,o.status,o.symbol,o.side,o.quantity,o.submitted_at_ms
                    FROM paper_orders o
                    JOIN authorized a ON a.wallet_id=o.wallet_id
                    WHERE o.client_order_id=:client
                    """.trimIndent()
                ).bind("client", order.clientOrderId)
                    .map { row, _ ->
                        val same = row.get("symbol", String::class.java)==order.symbol &&
                            row.get("side", String::class.java)==order.side.name &&
                            row.get("quantity", BigDecimal::class.java)?.compareTo(order.quantity)==0 &&
                            (row.get("submitted_at_ms") as Number).toLong()==request.submittedAtMs
                        if (!same) throw ResponseStatusException(
                            HttpStatus.CONFLICT, "Client order key reused with different payload"
                        )
                        SubmitPaperOrderResponse(
                            requireNotNull(row.get("order_id", UUID::class.java)),
                            requireNotNull(row.get("status", String::class.java))
                        )
                    }.one().switchIfEmpty(Mono.error(ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Wallet credential is no longer active"
                    )))
            )
    }
}
