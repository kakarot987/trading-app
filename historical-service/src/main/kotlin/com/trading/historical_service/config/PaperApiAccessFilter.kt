package com.trading.historical_service.config

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

/**
 * Single-user development access boundary, NOT wallet ownership or a public
 * authentication solution. Paper APIs are denied by default.
 *
 * To enable local debugging only set M8_PAPER_LOCAL_INSECURE_ENABLED=true,
 * or supply a high-entropy M8_PAPER_API_TOKEN for bearer-token access.
 * Never enable the insecure bypass on a publicly reachable service.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class PaperApiAccessFilter(
    @Value("\${M8_PAPER_LOCAL_INSECURE_ENABLED:false}") private val localInsecure: Boolean,
    @Value("\${M8_PAPER_API_TOKEN:}") private val configuredToken: String
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.pathWithinApplication().value()
        if (path != "/api/v1/paper" && !path.startsWith("/api/v1/paper/")) {
            return chain.filter(exchange)
        }
        // Wallet balances, order history and evaluations must never be cached
        // by a browser/proxy, even when an explicit private demo bypass is on.
        exchange.response.headers.set("Cache-Control", "no-store")
        exchange.response.headers.set("Pragma", "no-cache")
        exchange.response.headers.add("Vary", "Authorization")
        exchange.response.headers.set("X-Content-Type-Options", "nosniff")
        if (localInsecure) return chain.filter(exchange)

        // Reject duplicate/malformed headers; getFirst() alone would accept
        // a valid first Authorization even when a second is also present.
        val auth = exchange.request.headers["Authorization"].orEmpty()
        val header = auth.singleOrNull().orEmpty()
        val token = if (header.startsWith("Bearer ") &&
            header.length > 7 && !header.substring(7).contains(' ')) {
            header.substring(7)
        } else ""
        val expected = configuredToken.toByteArray(StandardCharsets.UTF_8)
        val provided = token.toByteArray(StandardCharsets.UTF_8)
        if (expected.size >= 32 && provided.size == expected.size &&
            MessageDigest.isEqual(expected, provided)) {
            return chain.filter(exchange)
        }
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
