package com.trading.historical_service.paper

import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.math.BigDecimal
import java.util.UUID

data class PaperBalanceView(val asset: String, val available: BigDecimal)
data class PaperOrderView(
    val orderId: UUID, val clientOrderId: String, val symbol: String,
    val side: String, val quantity: BigDecimal, val submittedAtMs: Long,
    val status: String, val rejectionReason: String?
)

/**
 * Read-only, simulated portfolio inspection. Wallet IDs are opaque IDs, NOT
 * authorization tokens. Do not expose this controller to untrusted networks
 * until wallet ownership/authentication is implemented.
 */
@RestController
@RequestMapping("/api/v1/paper/wallets")
class PaperPortfolioController(private val db: DatabaseClient) {
    @GetMapping("/{walletId}/balances")
    fun balances(@PathVariable walletId: UUID): Flux<PaperBalanceView> =
        db.sql(
            """
            SELECT asset, available FROM paper_balances
            WHERE wallet_id = :walletId ORDER BY asset
            """.trimIndent()
        ).bind("walletId", walletId)
            .map { row, _ ->
                PaperBalanceView(
                    requireNotNull(row.get("asset", String::class.java)),
                    requireNotNull(row.get("available", BigDecimal::class.java))
                )
            }.all()

    @GetMapping("/{walletId}/orders")
    fun orders(
        @PathVariable walletId: UUID,
        @RequestParam(defaultValue = "20") limit: Int
    ): Flux<PaperOrderView> {
        if (limit !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1..100")
        }
        return db.sql(
            """
            SELECT order_id, client_order_id, symbol, side, quantity,
                   submitted_at_ms, status, rejection_reason
            FROM paper_orders WHERE wallet_id = :walletId
            ORDER BY submitted_at_ms DESC, order_id DESC LIMIT :limit
            """.trimIndent()
        ).bind("walletId", walletId).bind("limit", limit)
            .map { row, _ ->
                PaperOrderView(
                    orderId = requireNotNull(row.get("order_id", UUID::class.java)),
                    clientOrderId = requireNotNull(row.get("client_order_id", String::class.java)),
                    symbol = requireNotNull(row.get("symbol", String::class.java)),
                    side = requireNotNull(row.get("side", String::class.java)),
                    quantity = requireNotNull(row.get("quantity", BigDecimal::class.java)),
                    submittedAtMs = (requireNotNull(row.get("submitted_at_ms")) as Number).toLong(),
                    status = requireNotNull(row.get("status", String::class.java)),
                    rejectionReason = row.get("rejection_reason", String::class.java)
                )
            }.all()
    }
}
