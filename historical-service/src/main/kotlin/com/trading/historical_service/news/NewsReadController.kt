package com.trading.historical_service.news

import org.springframework.http.HttpStatus
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

data class NewsArticleView(
    val id: UUID,
    val title: String,
    val source: String,
    val canonicalUrl: String,
    val eventType: String,
    val publishedAt: Instant,
    val observedAt: Instant
)

/**
 * Read-only, leakage-safe news query. Does not generate scores or use a
 * provider API. An observation must exist before the caller's as-of cutoff.
 */
@RestController
@RequestMapping("/api/v1/news")
class NewsReadController(private val databaseClient: DatabaseClient) {
    @GetMapping
    fun list(
        @RequestParam symbol: String,
        @RequestParam asOf: Instant,
        @RequestParam(defaultValue = "20") limit: Int
    ): Flux<NewsArticleView> {
        if (symbol !in setOf("BTCUSDT", "ETHUSDT")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Supported symbols: BTCUSDT, ETHUSDT")
        }
        if (limit !in 1..100) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100")
        }
        if (asOf.isAfter(Instant.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "asOf must not be in the future")
        }
        return databaseClient.sql(
            """
            SELECT a.id, a.headline, a.source_name, a.canonical_url,
                   a.event_type, a.published_at, a.observed_at
            FROM news_articles a
            JOIN news_article_symbols s ON s.article_id = a.id
            WHERE s.symbol = :symbol AND a.observed_at <= :cutoff
            ORDER BY a.observed_at DESC, a.id DESC
            LIMIT :limit
            """.trimIndent()
        ).bind("symbol", symbol)
            .bind("cutoff", asOf)
            .bind("limit", limit)
            .map { row, _ ->
                NewsArticleView(
                    id = requireNotNull(row.get("id", UUID::class.java)),
                    title = requireNotNull(row.get("headline", String::class.java)),
                    source = requireNotNull(row.get("source_name", String::class.java)),
                    canonicalUrl = requireNotNull(row.get("canonical_url", String::class.java)),
                    eventType = requireNotNull(row.get("event_type", String::class.java)),
                    publishedAt = requireNotNull(row.get("published_at", OffsetDateTime::class.java)).toInstant(),
                    observedAt = requireNotNull(row.get("observed_at", OffsetDateTime::class.java)).toInstant()
                )
            }.all()
    }
}
