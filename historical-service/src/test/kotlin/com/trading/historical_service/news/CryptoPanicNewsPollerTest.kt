package com.trading.historical_service.news

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.web.reactive.function.client.WebClient
import java.time.Instant

/** Offline fixture tests: never make a provider request or write database rows. */
class CryptoPanicNewsPollerTest {
    private val mapper = ObjectMapper()
    private val cutoff = Instant.parse("2026-10-30T10:00:00Z")
    private val poller = CryptoPanicNewsPoller(
        mock(NewsArticleRepository::class.java),
        mapper,
        WebClient.builder(),
        false,
        "",
        "developer"
    )

    @Test
    fun `parses source provenance, publication time and BTC ETH associations`() {
        val fixture = """
            {
              "id": 12345,
              "title": "Exchange publishes network update",
              "original_url": "https://publisher.example/news/network?utm_campaign=feed",
              "url": "https://cryptopanic.com/news/12345",
              "published_at": "2026-10-30T09:30:00Z",
              "source": {"title": "Original Publisher"},
              "instruments": [{"code": "BTC"}, {"code": "ETH"}, {"code": "SOL"}]
            }
        """.trimIndent()
        val article = poller.parseArticle(mapper.readTree(fixture), cutoff)
        assertNotNull(article)
        assertEquals("cryptopanic", article!!.provider)
        assertEquals("12345", article.providerArticleId)
        assertEquals("https://publisher.example/news/network?utm_campaign=feed", article.canonicalUrl)
        assertEquals("Original Publisher", article.sourceName)
        assertEquals(setOf("BTCUSDT", "ETHUSDT"), article.symbols)
        assertEquals(Instant.parse("2026-10-30T09:30:00Z"), article.publishedAt)
        assertEquals(cutoff, article.observedAt)
        assertEquals(NewsEventType.OTHER, article.eventType)
    }

    @Test
    fun `rejects future publications, unsupported currencies and unsafe urls`() {
        fun parse(published: String, url: String, instrument: String) =
            poller.parseArticle(mapper.readTree("""
                {
                  "id": 6, "title": "Headline",
                  "original_url": "$url",
                  "published_at": "$published",
                  "source": {"title": "Publisher"},
                  "instruments": [{"code":"$instrument"}]
                }
            """.trimIndent()), cutoff)

        assertNull(parse("2026-10-30T10:00:01Z", "https://example.org/item", "BTC"))
        assertNull(parse("2026-10-30T09:00:00Z", "https://example.org/item", "DOGE"))
        assertNull(parse("2026-10-30T09:00:00Z", "http://example.org/item", "BTC"))
        assertNull(parse("invalid timestamp", "https://example.org/item", "BTC"))
    }

    @Test
    fun `falls back to provider URL when original URL is missing`() {
        val article = poller.parseArticle(mapper.readTree("""
            {
              "id": 8, "title": "ETF item",
              "url": "https://cryptopanic.com/news/8",
              "published_at": "2026-10-30T09:00:00Z",
              "instruments": [{"code":"BTC"}]
            }
        """.trimIndent()), cutoff)
        assertEquals("https://cryptopanic.com/news/8", article!!.canonicalUrl)
        assertEquals(setOf("BTCUSDT"), article.symbols)
    }

    @Test
    fun `disabled poller does not attempt network or persistence`() {
        assertDoesNotThrow { poller.poll() }
    }
}
