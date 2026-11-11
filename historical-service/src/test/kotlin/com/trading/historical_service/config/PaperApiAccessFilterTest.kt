package com.trading.historical_service.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test fun `paper responses disable caching in both secure and demo modes`() {
        for (localDemo in listOf(false, true)) {
            val filter = PaperApiAccessFilter(localDemo, token)
            val exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/paper/wallets")
            )
            filter.filter(exchange, WebFilterChain { Mono.empty() }).block()
            assertEquals("no-store", exchange.response.headers.getFirst("Cache-Control"))
            assertEquals("no-cache", exchange.response.headers.getFirst("Pragma"))
            assertEquals("nosniff", exchange.response.headers.getFirst("X-Content-Type-Options"))
            assertTrue(exchange.response.headers["Vary"].orEmpty().contains("Authorization"))
        }
    }

    @Test fun `duplicate or malformed authorization does not bypass guard`() {
        val filter = PaperApiAccessFilter(false, token)
        val headers = listOf(
            listOf("Bearer $token", "Bearer wrong"),
            listOf("bearer $token"),
            listOf("Bearer  $token"),
            listOf("Bearer $token ")
        )
        for (values in headers) {
            val builder = MockServerHttpRequest.get("/api/v1/paper/wallets")
            builder.header("Authorization", *values.toTypedArray())
            val exchange = MockServerWebExchange.from(builder)
            val calls = AtomicInteger()
            filter.filter(exchange, WebFilterChain { calls.incrementAndGet(); Mono.empty() }).block()
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
            assertEquals(0, calls.get())
        }
    }

    @Test fun `unrelated routes pass and local override is explicit`() {
        assertEquals(1, request(PaperApiAccessFilter(false, ""), "/actuator/health").second)
        assertEquals(1, request(PaperApiAccessFilter(true, ""), "/api/v1/paper/wallets").second)
    }
}
