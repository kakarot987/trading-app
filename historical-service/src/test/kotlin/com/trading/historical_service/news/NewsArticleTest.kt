package com.trading.historical_service.news

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Instant

class NewsArticleTest {
    private val published = Instant.parse("2026-10-30T08:00:00Z")
    private val observed = Instant.parse("2026-10-30T08:02:00Z")

    private fun article(url: String = "https://example.org/articles/42?utm_source=feed") =
        NewsArticle("demo-feed", "42", url, "Sample exchange announcement",
            published, observed, setOf("BTCUSDT"), NewsEventType.EXCHANGE, "Publisher")

    @Test
    fun `tracking query and fragment do not affect stable article identity`() {
        assertEquals(article().deduplicationKey(),
            article("https://EXAMPLE.ORG/articles/42?utm_source=other#heading").deduplicationKey())
        assertNotEquals(article().deduplicationKey(), article("https://example.org/articles/43").deduplicationKey())
    }

    @Test
    fun `rejects impossible publication chronology and unsafe urls`() {
        assertThrows(IllegalArgumentException::class.java) {
            article().copy(observedAt = published.minusSeconds(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            article("http://example.org/articles/42")
        }
        assertThrows(IllegalArgumentException::class.java) {
            article().copy(symbols = setOf("UNSUPPORTED"))
        }
    }
}
