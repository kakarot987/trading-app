package com.trading.historical_service.signals

import kotlin.math.ln

/**
 * Holdout metrics for *out-of-sample* predictions. Insufficient samples are
 * explicitly unevaluable; no synthetic results should be reported as live.
 */
data class HoldoutMetrics(
    val count: Int,
    val accuracy: Double,
    val brier: Double,
    val logLoss: Double,
    val positives: Int,
    val modelVersion: String
)

data class ScoredObservation(val probabilityUp: Double, val actualUp: Boolean)

object WalkForwardMetrics {
    const val MIN_TEST_ROWS = 20

    fun evaluate(
        modelVersion: String,
        observations: List<ScoredObservation>,
        minTestRows: Int = MIN_TEST_ROWS
    ): HoldoutMetrics {
        require(modelVersion.isNotBlank())
        require(minTestRows >= MIN_TEST_ROWS) { "Cannot lower minimum evidence threshold" }
        require(observations.size >= minTestRows) {
            "Insufficient labeled holdout observations: ${observations.size} < $minTestRows"
        }
        require(observations.all { it.probabilityUp.isFinite() && it.probabilityUp in 0.0..1.0 })
        val n = observations.size.toDouble()
        val positives = observations.count { it.actualUp }
        return HoldoutMetrics(
            count = observations.size,
            accuracy = observations.count { (it.probabilityUp >= 0.5) == it.actualUp } / n,
            brier = observations.sumOf {
                val error = it.probabilityUp - if (it.actualUp) 1.0 else 0.0
                error * error
            } / n,
            logLoss = observations.sumOf {
                val p = it.probabilityUp.coerceIn(1e-12, 1.0 - 1e-12)
                if (it.actualUp) -ln(p) else -ln(1.0 - p)
            } / n,
            positives = positives,
            modelVersion = modelVersion
        )
    }
}
