package com.trading.historical_service.news

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URI
import java.time.Instant
import java.time.Duration

/**
 * Opt-in metadata-only CryptoPanic developer API ingestion.
 *
 * Disabled by default; requires explicit plan/credential and permission to
 * store/use article metadata. Never scrape publisher article text. The
 * developer tier supports currencies filtering, but has plan-specific caps.
 */
@Component
class CryptoPanicNewsPoller(
    private val repository: NewsArticleRepository,
    private val objectMapper: ObjectMapper,
    private val clientBuilder: WebClient.Builder,
    @Value("\${m4.news.enabled:false}") private val enabled: Boolean,
    @Value("\${m4.news.auth-token:}") private val token: String,
    @Value("\${m4.news.plan:developer}") private val plan: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val http by lazy { clientBuilder.build() }
    private val maxPerPoll = 20

    @Scheduled(fixedDelayString = "\${m4.news.poll-ms:900000}", initialDelayString = "\${m4.news.initial-delay-ms:60000}")
    fun poll() {
        if (!enabled) return
        if (token.isBlank() || plan !in setOf("developer", "growth", "enterprise")) {
            log.warn("M4 news poll disabled: missing credential or invalid plan")
            return
        }
        try {
            val uri = UriComponentsBuilder
                .fromUriString("https://cryptopanic.com/api/$plan/v2/posts/")
                .queryParam("auth_token", token)
                .queryParam("public", "true")
                .queryParam("currencies", "BTC,ETH")
                .queryParam("kind", "news")
                .build().encode().toUri()
            val json = http.get().uri(uri).retrieve()
                .bodyToMono(String::class.java).block(Duration.ofSeconds(15))
                ?: return
            val items = objectMapper.readTree(json).path("results")
            require(items.isArray) { "CryptoPanic response missing results array" }
            var inserted = 0
            for (item in items.take(maxPerPoll)) {
                val article = parseArticle(item, Instant.now()) ?: continue
                if (repository.insert(article).block(Duration.ofSeconds(10)) == true) inserted++
            }
            log.info("M4 news poll: received={}, inserted={}", items.size(), inserted)
        } catch (e: Exception) {
            // Do not expose request URI: it includes the API token.
            log.warn("M4 news poll failed (status/type={}): {}",
                e.javaClass.simpleName, e.message?.replace(token, "[REDACTED]"))
        }
    }

    internal fun parseArticle(item: JsonNode, observedAt: Instant): NewsArticle? {
        val title = item.path("title").asText("").trim()
        val url = item.path("original_url").asText("").ifBlank { item.path("url").asText("") }.trim()
        val publishedText = item.path("published_at").asText("")
        val published = runCatching { Instant.parse(publishedText) }.getOrNull() ?: return null
        if (title.isEmpty() || title.length > 1000 || published > observedAt) return null
        if (runCatching { URI(url).scheme == "https" }.getOrDefault(false).not()) return null
        val symbols = item.path("instruments").mapNotNull {
            when (it.path("code").asText("").uppercase()) {
                "BTC" -> "BTCUSDT"
                "ETH" -> "ETHUSDT"
                else -> null
            }
        }.toSet()
        if (symbols.isEmpty()) return null
        return runCatching {
            NewsArticle(
                provider = "cryptopanic",
                providerArticleId = item.path("id").asText("").ifBlank { null },
                canonicalUrl = url,
                headline = title,
                publishedAt = published,
                observedAt = observedAt,
                symbols = symbols,
                eventType = NewsEventType.OTHER,
                sourceName = item.path("source").path("title").asText("CryptoPanic")
            )
        }.getOrNull()
    }
}
