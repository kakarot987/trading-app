package com.trading.historical_service.news

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import java.time.Instant
import java.util.UUID

/**
 * Atomic, duplicate-safe article ingestion. Duplicate URLs retain the first
 * observation timestamp: a replay must not rewrite what was known at the time.
 */
@Repository
class NewsArticleRepository(
    private val databaseClient: DatabaseClient,
    private val transactions: TransactionalOperator
) {
    fun insert(article: NewsArticle): Mono<Boolean> {
        val key = article.deduplicationKey()
        val insert = databaseClient.sql(
            """
            INSERT INTO news_articles (
                deduplication_key, provider, provider_article_id, canonical_url,
                headline, source_name, event_type, published_at, observed_at
            ) VALUES (
                :key, :provider, :providerId, :url, :headline, :source,
                :eventType, :published, :observed
            )
            ON CONFLICT (deduplication_key) DO NOTHING
            RETURNING id
            """.trimIndent()
        ).bind("key", key)
            .bind("provider", article.provider)
            .bind("url", article.canonicalUrl)
            .bind("headline", article.headline)
            .bind("source", article.sourceName)
            .bind("eventType", article.eventType.name)
            .bind("published", article.publishedAt)
            .bind("observed", article.observedAt)
        val query = if (article.providerArticleId == null) insert.bindNull("providerId", String::class.java)
            else insert.bind("providerId", article.providerArticleId)

        val write = query.map { row, _ -> requireNotNull(row.get("id", UUID::class.java)) }
            .one()
            .flatMap { id ->
                val associations = article.symbols.sorted().map { symbol ->
                    databaseClient.sql(
                        """
                        INSERT INTO news_article_symbols(article_id, symbol)
                        VALUES (:id, :symbol)
                        ON CONFLICT DO NOTHING
                        """.trimIndent()
                    ).bind("id", id).bind("symbol", symbol).fetch().rowsUpdated().then()
                }
                Mono.`when`(associations).thenReturn(true)
            }
            .defaultIfEmpty(false)
        return transactions.transactional(write)
    }

    /** As-of query refuses articles not yet observed by the cutoff. */
    fun countObservedBy(symbol: String, cutoff: Instant): Mono<Long> {
        require(symbol in setOf("BTCUSDT", "ETHUSDT"))
        return databaseClient.sql(
            """
            SELECT count(*) AS total
            FROM news_articles a
            JOIN news_article_symbols s ON s.article_id = a.id
            WHERE s.symbol = :symbol AND a.observed_at <= :cutoff
            """.trimIndent()
        ).bind("symbol", symbol).bind("cutoff", cutoff)
            .map { row, _ -> (requireNotNull(row.get("total")) as Number).toLong() }
            .one()
    }
}
