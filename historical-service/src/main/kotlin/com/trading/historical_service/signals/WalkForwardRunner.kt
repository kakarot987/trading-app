package com.trading.historical_service.signals

/**
 * Expanding-window walk-forward evaluation. Every model is fit on training
 * observations whose labels are already known at its prediction timestamp.
 * The evaluation remains unavailable until sufficient real holdout labels exist.
 */
object WalkForwardRunner {
    const val MIN_HOLDOUT_ROWS = WalkForwardMetrics.MIN_TEST_ROWS

    data class Report(val metrics: HoldoutMetrics, val firstTestAtMs: Long, val lastTestAtMs: Long)

    fun evaluate(rows: List<EvaluationExample>): Report {
        require(rows.isNotEmpty())
        require(rows.zipWithNext().all { (a, b) ->
            a.predictionTimeMs <= b.predictionTimeMs
        }) { "Input examples must be chronological" }
        require(rows.all { it.labelAvailableAtMs > it.predictionTimeMs })
        val predictions = mutableListOf<ScoredObservation>()
        var firstTestAt: Long? = null
        var lastTestAt: Long? = null
        // A new training set is constructed for *each* test prediction. The
        // future label of another asset at the same timestamp stays excluded.
        for (test in rows) {
            val train = rows.filter {
                it.predictionTimeMs < test.predictionTimeMs &&
                    it.labelAvailableAtMs <= test.predictionTimeMs
            }
            if (train.size < LogisticBaseline.MIN_TRAIN_ROWS) continue
            val fitted = LogisticBaseline.fit(train)
            val p = fitted.predict(test)
            predictions.add(ScoredObservation(p, test.up))
            if (firstTestAt == null) firstTestAt = test.predictionTimeMs
            lastTestAt = test.predictionTimeMs
        }
        require(predictions.size >= MIN_HOLDOUT_ROWS) {
            "Insufficient walk-forward holdout observations: ${predictions.size} < $MIN_HOLDOUT_ROWS"
        }
        return Report(
            metrics = WalkForwardMetrics.evaluate(LogisticBaseline.VERSION, predictions),
            firstTestAtMs = requireNotNull(firstTestAt),
            lastTestAtMs = requireNotNull(lastTestAt)
        )
    }
}
