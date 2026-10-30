package com.trading.historical_service.news

/** Headline-level sentiment evaluation, not a forecast of price direction. */
enum class HeadlineSentiment { POSITIVE, NEGATIVE, NEUTRAL }

data class LabeledHeadline(val headline: String, val expected: HeadlineSentiment)

data class BenchmarkResult(
    val total: Int,
    val correct: Int,
    val accuracy: Double,
    val macroF1: Double,
    val confusion: Map<HeadlineSentiment, Map<HeadlineSentiment, Int>>
)

/**
 * Transparent deterministic baseline only. The word lists are deliberately
 * small: passing toy fixtures does not establish production accuracy.
 */
object KeywordSentimentBaseline {
    private val positive = setOf("approval", "approved", "recovery", "secured", "upgrade", "adoption", "partnership")
    private val negative = setOf("hack", "hacked", "exploit", "breach", "lawsuit", "outage", "stolen", "ban")

    fun predict(headline: String): HeadlineSentiment {
        val words = Regex("[a-z]+").findAll(headline.lowercase()).map { it.value }.toSet()
        val pos = positive.count { it in words }
        val neg = negative.count { it in words }
        return when {
            pos > neg -> HeadlineSentiment.POSITIVE
            neg > pos -> HeadlineSentiment.NEGATIVE
            else -> HeadlineSentiment.NEUTRAL
        }
    }
}

object SentimentBenchmark {
    fun evaluate(
        examples: List<LabeledHeadline>,
        classifier: (String) -> HeadlineSentiment = KeywordSentimentBaseline::predict
    ): BenchmarkResult {
        require(examples.isNotEmpty()) { "A nonempty labeled benchmark is required" }
        val labels = HeadlineSentiment.entries
        val counts = labels.associateWith { actual ->
            labels.associateWith { predicted ->
                examples.count { it.expected == actual && classifier(it.headline) == predicted }
            }
        }
        val correct = labels.sumOf { counts.getValue(it).getValue(it) }
        val f1 = labels.map { label ->
            val tp = counts.getValue(label).getValue(label).toDouble()
            val fp = labels.sumOf { counts.getValue(it).getValue(label) } - tp
            val fn = counts.getValue(label).values.sum() - tp
            if (tp == 0.0) 0.0 else 2 * tp / (2 * tp + fp + fn)
        }.average()
        return BenchmarkResult(examples.size, correct, correct.toDouble() / examples.size, f1, counts)
    }
}
