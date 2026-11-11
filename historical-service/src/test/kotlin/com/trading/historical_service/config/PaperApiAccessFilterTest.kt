package com.trading.historical_service.config

import com.trading.historical_service.paper.PaperWalletAccessStore
import com.trading.historical_service.paper.PaperWalletTokens
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PaperApiAccessFilterTest {
    private val bootstrap = "a".repeat(40)
    private val wallet = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherWallet = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val walletToken = PaperWalletTokens.generate()
    private val store = Mockito.mock(PaperWalletAccessStore::class.java)

    private fun request(
        filter: PaperApiAccessFilter,
        path: String,
        method: String = "GET",
        headers: List<String> = emptyList()
    ): Pair<MockServerWebExchange, Int> {
        val builder = if (method == "POST") MockServerHttpRequest.post(path)
            else MockServerHttpRequest.get(path)
        if (headers.isNotEmpty()) builder.header("Authorization", *headers.toTypedArray())
        val exchange = MockServerWebExchange.from(builder)
        val calls = AtomicInteger()
        filter.filter(exchange, WebFilterChain {
            calls.incrementAndGet()
            Mono.empty()
        }).block()
        return exchange to calls.get()
    }

    @Test fun `paper API denies missing, malformed and repeated credentials`() {
        val filter = PaperApiAccessFilter(false, bootstrap, store)
        val path = "/api/v1/paper/wallets/$wallet/balances"
        for (headers in listOf(
            emptyList(), listOf("Bearer $bootstrap"),
            listOf("bearer $walletToken"), listOf("Bearer $walletToken "),
            listOf("Bearer $walletToken", "Bearer $walletToken")
        )) {
            val (exchange, calls) = request(filter, path, headers = headers)
            assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
            assertEquals(0, calls)
            assertEquals("no-store", exchange.response.headers.getFirst("Cache-Control"))
            assertEquals("no-cache", exchange.response.headers.getFirst("Pragma"))
            assertEquals("nosniff", exchange.response.headers.getFirst("X-Content-Type-Options"))
        }
    }

    @Test fun `bootstrap credential provisions only, never reads a wallet`() {
        val filter = PaperApiAccessFilter(false, bootstrap, store)
        assertEquals(1, request(filter, "/api/v1/paper/wallets",
            method = "POST", headers = listOf("Bearer $bootstrap")).second)
        assertEquals(HttpStatus.UNAUTHORIZED, request(filter, "/api/v1/paper/wallets",
            method = "POST", headers = listOf("Bearer wrong")).first.response.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, request(filter, "/api/v1/paper/wallets",
            headers = listOf("Bearer $bootstrap")).first.response.statusCode)
        assertEquals(HttpStatus.UNAUTHORIZED, request(filter,
            "/api/v1/paper/wallets/$wallet/orders", method = "POST",
            headers = listOf("Bearer $bootstrap")).first.response.statusCode)
    }

    @Test fun `scoped token permits only matching wallet across read and write routes`() {
        Mockito.`when`(store.authorized(wallet, walletToken)).thenReturn(Mono.just(true))
        Mockito.`when`(store.authorized(otherWallet, walletToken)).thenReturn(Mono.just(false))
        val filter = PaperApiAccessFilter(false, bootstrap, store)
        for (path in listOf(
            "/api/v1/paper/wallets/$wallet/balances",
            "/api/v1/paper/wallets/$wallet/orders",
            "/api/v1/paper/wallets/$wallet/evaluation"
        )) {
            assertEquals(1, request(filter, path, headers = listOf("Bearer $walletToken")).second)
        }
        assertEquals(1, request(filter, "/api/v1/paper/wallets/$wallet/orders",
            method = "POST", headers = listOf("Bearer $walletToken")).second)
        val (other, calls) = request(filter,
            "/api/v1/paper/wallets/$otherWallet/balances",
            headers = listOf("Bearer $walletToken"))
        assertEquals(HttpStatus.UNAUTHORIZED, other.response.statusCode)
        assertEquals(0, calls)
    }

    @Test fun `missing DB credentials and read failures deny by default`() {
        Mockito.`when`(store.authorized(wallet, walletToken))
            .thenReturn(Mono.error(IllegalStateException("database offline")))
        val filter = PaperApiAccessFilter(false, bootstrap, store)
        val (denied, calls) = request(filter,
            "/api/v1/paper/wallets/$wallet/evaluation",
            headers = listOf("Bearer $walletToken"))
        assertEquals(HttpStatus.UNAUTHORIZED, denied.response.statusCode)
        assertEquals(0, calls)
        val (missing, missingCalls) = request(filter,
            "/api/v1/paper/wallets/unknown/balances",
            headers = listOf("Bearer $walletToken"))
        assertEquals(HttpStatus.UNAUTHORIZED, missing.response.statusCode)
        assertEquals(0, missingCalls)
    }

    @Test fun `non paper URLs pass and unsafe demo bypass remains explicit`() {
        assertEquals(1, request(PaperApiAccessFilter(false, "", store),
            "/actuator/health").second)
        assertEquals(1, request(PaperApiAccessFilter(true, "", store),
            "/api/v1/paper/wallets/$wallet/balances").second)
    }
}
