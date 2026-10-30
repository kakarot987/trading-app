package com.trading.historical_service.signals

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.time.Instant

/** Read-only research endpoint; never triggers predictions or trade orders. */
@RestController
@RequestMapping("/api/v1/research/signals")
class ResearchSignalController(private val store: ResearchSignalStore) {
    @GetMapping
    fun list(
        @RequestParam symbol: String,
        @RequestParam asOf: Instant,
        @RequestParam(defaultValue = "20") limit: Int
    ): Flux<DirectionalSignal> {
        if (symbol !in setOf("BTCUSDT", "ETHUSDT")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported symbol")
        }
        if (limit !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be 1..100")
        }
        if (asOf.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Future asOf is forbidden")
        }
        return store.list(symbol, asOf.toEpochMilli(), limit)
    }
}
