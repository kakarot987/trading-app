package com.trading.historical_service.news

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Small, manually authored stress cases for a *headline* baseline. These are
 * not sampled real news, independent human labels or out-of-sample validation.
 */
class SentimentEvaluationDatasetTest {
    private val dataset = javaClass.getResourceAsStream("/news/m4-sentiment-evaluation.tsv")!!
        .bufferedReader().use { reader ->
            reader.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
                val parts = line.split('\t', limit = 2)
                require(parts.size == 2)
                LabeledHeadline(parts[1], HeadlineSentiment.valueOf(parts[0]))
            }.toList()
        }

    @Test
    fun `fixture dataset is balanced across labels and contains challenge examples`() {
        assertEquals(24, dataset.size)
        assertTrue(HeadlineSentiment.entries.all { label ->
            dataset.count { it.expected == label } >= 6
        })
        assertTrue(dataset.map { it.headline }.distinct().size == dataset.size)
    }

    @Test
    fun `benchmark reports actual accuracy confusion and failure cases`() {
        val result = SentimentBenchmark.evaluate(dataset)
        assertEquals(24, result.total)
        assertEquals(result.correct.toDouble() / result.total, result.accuracy, 1e-10)
        assertTrue(result.macroF1 in 0.0..1.0)
        val failures = dataset.filter { KeywordSentimentBaseline.predict(it.headline) != it.expected }
        assertEquals(dataset.size - result.correct, failures.size)
        println("M4 stress-fixture accuracy=${result.accuracy}, macroF1=${result.macroF1}")
        failures.forEach { println("MISCLASSIFIED expected=${it.expected} predicted=${KeywordSentimentBaseline.predict(it.headline)} headline=${it.headline}") }
        // Unlike the earlier toy set, this fixture intentionally contains
        // negation and ambiguity failures; a perfect result would be suspect.
        assertTrue(failures.isNotEmpty())
    }
}
