package com.trading.historical_service.config

import com.trading.historical_service.paper.PaperWalletAccessStore
import com.trading.historical_service.paper.PaperWalletTokens
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

/**
 * Explicit private-demo capability boundary, not user authentication.
 *
 * The shared M8 token can ONLY provision wallets (when M6 commands are
 * separately enabled). Reads/writes against /wallets/{id} need that wallet's
 * individual, unrevoked capability. Wallet IDs are never credentials.
 *
 * Existing wallets without a V12 access record remain inaccessible in secure
 * mode, and database lookup failures deny access. No identity integration or
 * public multi-user deployment is implied.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaperApiAccessFilter(
    @Value("\${M8_PAPER_LOCAL_INSECURE_ENABLED:false}") private val localInsecure: Boolean,
    @Value("\${M8_PAPER_API_TOKEN:}") private val configuredToken: String,
    private val walletAccess: PaperWalletAccessStore
) : WebFilter {
    private val walletPath = Regex("^/api/v1/paper/wallets/([0-9a-fA-F-]{36})(?:/.*)?$")

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (path != "/api/v1/paper" && !path.startsWith("/api/v1/paper/")) {
            return chain.filter(exchange)
        }
        exchange.response.headers.set("Cache-Control", "no-store")
        exchange.response.headers.set("Pragma", "no-cache")
        exchange.response.headers.add("Vary", "Authorization")
        exchange.response.headers.set("X-Content-Type-Options", "nosniff")

        // The bypass is deliberately explicit, only for isolated local stacks.
        if (localInsecure) return chain.filter(exchange)

        // Exactly one well-formed Authorization header; never accept a shared
        // admin/provisioning secret as an individual wallet credential.
        val auth = exchange.request.headers["Authorization"].orEmpty()
        val header = auth.singleOrNull().orEmpty()
        val token = if (header.startsWith("Bearer ") &&
            header.length > 7 && !header.substring(7).contains(' ')) {
            header.substring(7)
        } else ""
        if (path == "/api/v1/paper/wallets" &&
            exchange.request.method?.name() == "POST") {
            val expected = configuredToken.toByteArray(StandardCharsets.UTF_8)
            val provided = token.toByteArray(StandardCharsets.UTF_8)
            if (expected.size >= 32 && expected.size == provided.size &&
                MessageDigest.isEqual(expected, provided)) {
                return chain.filter(exchange)
            }
            return unauthorized(exchange)
        }

        val match = walletPath.matchEntire(path) ?: return unauthorized(exchange)
        val wallet = runCatching { UUID.fromString(match.groupValues[1]) }.getOrNull()
            ?: return unauthorized(exchange)
        if (!PaperWalletTokens.isWellFormed(token)) return unauthorized(exchange)
        return Mono.defer { walletAccess.authorized(wallet, token) }
            .onErrorReturn(false)
            .flatMap { permitted ->
                if (permitted) chain.filter(exchange) else unauthorized(exchange)
            }
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
