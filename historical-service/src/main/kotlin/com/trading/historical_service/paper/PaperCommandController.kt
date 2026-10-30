package com.trading.historical_service.paper

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import java.math.BigDecimal
import java.util.UUID

data class CreatePaperWalletRequest(val initialUsdt: String = "10000.00000000")
data class CreatePaperWalletResponse(val walletId: UUID)
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
    @Value("\${M6_PAPER_COMMANDS_ENABLED:false}") private val enabled: Boolean
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
        // Single statement atomically creates wallet and initial USDT.
        return db.sql(
            """
            WITH wallet AS (INSERT INTO paper_wallets DEFAULT VALUES RETURNING wallet_id),
            initial AS (
              INSERT INTO paper_balances(wallet_id,asset,available)
              SELECT wallet_id,'USDT',:initial FROM wallet RETURNING wallet_id
            )
            SELECT wallet_id FROM initial
            """.trimIndent()
        ).bind("initial", initial)
            .map { row, _ -> CreatePaperWalletResponse(requireNotNull(row.get("wallet_id", UUID::class.java))) }
            .one()
    }

    @PostMapping("/wallets/{walletId}/orders")
    fun submit(
        @PathVariable walletId: UUID,
        @RequestBody request: SubmitPaperOrderRequest
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
        // Idempotent client key: replay returns existing status, never overwrites payload.
        return db.sql(
            """
            INSERT INTO paper_orders(wallet_id,client_order_id,symbol,side,quantity,submitted_at_ms)
            VALUES(:wallet,:client,:symbol,:side,:quantity,:submitted)
            ON CONFLICT(wallet_id,client_order_id) DO NOTHING
            RETURNING order_id,status
            """.trimIndent()
        ).bind("wallet", walletId).bind("client", order.clientOrderId)
            .bind("symbol", order.symbol).bind("side", order.side.name)
            .bind("quantity", order.quantity).bind("submitted", request.submittedAtMs)
            .map { row, _ -> SubmitPaperOrderResponse(
                requireNotNull(row.get("order_id", UUID::class.java)),
                requireNotNull(row.get("status", String::class.java))
            ) }.one().switchIfEmpty(
                db.sql(
                    """
                    SELECT order_id,status,symbol,side,quantity,submitted_at_ms
                    FROM paper_orders
                    WHERE wallet_id=:wallet AND client_order_id=:client
                    """.trimIndent()
                ).bind("wallet", walletId).bind("client", order.clientOrderId)
                    .map { row, _ ->
                        val same = row.get("symbol", String::class.java)==order.symbol &&
                            row.get("side", String::class.java)==order.side.name &&
                            row.get("quantity", BigDecimal::class.java)?.compareTo(order.quantity)==0 &&
                            (row.get("submitted_at_ms") as Number).toLong()==request.submittedAtMs
                        if (!same) throw ResponseStatusException(HttpStatus.CONFLICT, "Client order key reused with different payload")
                        SubmitPaperOrderResponse(
                            requireNotNull(row.get("order_id", UUID::class.java)),
                            requireNotNull(row.get("status", String::class.java))
                        )
                    }.one()
            )
    }
}
