package com.trading.historical_service.news

import java.net.URI
import java.security.MessageDigest
import java.time.Instant

/**
 * Provenance-carrying news observation. Sentiment is deliberately absent until
 * an evaluated classifier is available: headlines do not imply a trade signal.
 */
data class NewsArticle(
    val provider: String,
    val providerArticleId: String?,
    val canonicalUrl: String,
    val headline: String,
    val publishedAt: Instant,
    val observedAt: Instant,
    val symbols: Set<String>,
    val eventType: NewsEventType,
    val sourceName: String
) {
    init {
        require(provider.isNotBlank() && sourceName.isNotBlank())
        require(headline.isNotBlank() && headline.length <= 1000)
        require(!publishedAt.isAfter(observedAt)) { "Article cannot be observed before publication" }
        require(symbols.all { it in setOf("BTCUSDT", "ETHUSDT") })
        val uri = URI(canonicalUrl)
        require(uri.scheme.equals("https", ignoreCase = true) &&
            !uri.host.isNullOrBlank()) { "Canonical URL must be HTTPS" }
    }

    /** Same canonical article is deduplicated across polling replays. */
    fun deduplicationKey(): String {
        val canonical = URI(canonicalUrl)
        val host = canonical.host.lowercase()
        val path = canonical.rawPath?.ifBlank { "/" } ?: "/"
        val normalized = "https://$host${if (canonical.port == -1) "" else ":${canonical.port}"}$path"
        val hash = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

enum class NewsEventType {
    REGULATORY, EXCHANGE, SECURITY, MACRO, NETWORK, ETF, OTHER
}
