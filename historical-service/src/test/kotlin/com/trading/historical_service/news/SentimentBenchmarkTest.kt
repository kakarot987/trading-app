package com.trading.historical_service.news

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SentimentBenchmarkTest {
    private val examples = listOf(
        LabeledHeadline("Network upgrade completed", HeadlineSentiment.POSITIVE),
        LabeledHeadline("Exchange hacked in security breach", HeadlineSentiment.NEGATIVE),
        LabeledHeadline("Market data published for October", HeadlineSentiment.NEUTRAL),
        LabeledHeadline("ETF approval announced", HeadlineSentiment.POSITIVE),
        LabeledHeadline("Exchange reports outage", HeadlineSentiment.NEGATIVE),
        LabeledHeadline("Quarterly report issued", HeadlineSentiment.NEUTRAL)
    )

    @Test
    fun `benchmark computes confusion counts and macro F1`() {
        val result = SentimentBenchmark.evaluate(examples)
        assertEquals(6, result.total)
        assertEquals(6, result.correct)
        assertEquals(1.0, result.accuracy)
        assertEquals(1.0, result.macroF1)
        assertEquals(2, result.confusion.getValue(HeadlineSentiment.POSITIVE)
            .getValue(HeadlineSentiment.POSITIVE))
    }

    @Test
    fun `neutral-only classifier has measured failures, not fabricated accuracy`() {
        val result = SentimentBenchmark.evaluate(examples) { HeadlineSentiment.NEUTRAL }
        assertEquals(2, result.correct)
        assertEquals(1.0 / 3.0, result.accuracy, 1e-9)
        assertEquals(1.0 / 6.0, result.macroF1, 1e-9)
    }

    @Test
    fun `classifier runs once per example and confusion matrix stays coherent`() {
        var calls = 0
        val result = SentimentBenchmark.evaluate(examples) { headline ->
            calls++
            KeywordSentimentBaseline.predict(headline)
        }
        assertEquals(examples.size, calls, "One inference per headline avoids inconsistent metrics")
        assertEquals(result.total, result.confusion.values.sumOf { row -> row.values.sum() })
        assertEquals(result.correct, HeadlineSentiment.entries.sumOf { label ->
            result.confusion.getValue(label).getValue(label)
        })
    }

    @Test
    fun `empty benchmark is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SentimentBenchmark.evaluate(emptyList())
        }
    }
}
