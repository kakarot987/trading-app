package com.trading.historical_service.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.concurrent.atomic.AtomicInteger

class PaperApiAccessFilterTest {
    private val token = "a".repeat(40)

    private fun request(filter: PaperApiAccessFilter, path: String, auth: String? = null): Pair<HttpStatus?, Int> {
        val builder = MockServerHttpRequest.get(path)
        if (auth != null) builder.header("Authorization", auth)
        val exchange = MockServerWebExchange.from(builder)
        val calls = AtomicInteger()
        val chain = WebFilterChain { calls.incrementAndGet(); Mono.empty() }
        filter.filter(exchange, chain).block()
        return Pair(exchange.response.statusCode as? HttpStatus, calls.get())
    }

    @Test fun `paper endpoints denied by default`() {
        val filter = PaperApiAccessFilter(false, "")
        val (status, calls) = request(filter, "/api/v1/paper/wallets/123/balances")
        assertEquals(HttpStatus.UNAUTHORIZED, status)
        assertEquals(0, calls)
    }

    @Test fun `valid bearer grants access and invalid token fails`() {
        val filter = PaperApiAccessFilter(false, token)
        assertEquals(1, request(filter, "/api/v1/paper/wallets", "Bearer $token").second)
        assertEquals(HttpStatus.UNAUTHORIZED, request(filter, "/api/v1/paper/wallets", "Bearer wrong").first)
    }

    @Test fun `unrelated routes pass and local override is explicit`() {
        assertEquals(1, request(PaperApiAccessFilter(false, ""), "/actuator/health").second)
        assertEquals(1, request(PaperApiAccessFilter(true, ""), "/api/v1/paper/wallets").second)
    }
}
