package com.trading.historical_service.signals

import kotlin.math.exp
import kotlin.math.ln

/**
 * Platt-style sigmoid calibration. Fit only on a distinct chronological
 * calibration set; evaluate only on later, untouched holdout observations.
 * This is a research component, not evidence of calibrated live forecasts.
 */
data class ProbabilityCalibrator(val slope: Double, val intercept: Double) {
    fun transform(rawProbability: Double): Double {
        require(rawProbability.isFinite() && rawProbability in 0.0..1.0)
        val x = ln(rawProbability.coerceIn(1e-6, 1.0 - 1e-6) /
            (1.0 - rawProbability.coerceIn(1e-6, 1.0 - 1e-6)))
        val z = slope * x + intercept
        return if (z >= 0.0) 1.0 / (1.0 + exp(-z))
        else exp(z) / (1.0 + exp(z))
    }
}

data class CalibrationExample(
    val predictedAtMs: Long,
    val labelAvailableAtMs: Long,
    val rawProbabilityUp: Double,
    val actualUp: Boolean
)

object ProbabilityCalibration {
    const val MIN_CALIBRATION_ROWS = 40

    fun fit(
        calibration: List<CalibrationExample>,
        holdoutStartMs: Long,
        epochs: Int = 600,
        learningRate: Double = 0.01
    ): ProbabilityCalibrator {
        require(calibration.size >= MIN_CALIBRATION_ROWS) { "Insufficient calibration observations" }
        require(epochs in 1..10_000 && learningRate.isFinite() && learningRate > 0.0)
        require(calibration.zipWithNext().all { (a, b) -> a.predictedAtMs <= b.predictedAtMs })
        require(calibration.all {
            it.predictedAtMs < it.labelAvailableAtMs &&
                it.labelAvailableAtMs <= holdoutStartMs &&
                it.rawProbabilityUp.isFinite() &&
                it.rawProbabilityUp in 0.0..1.0
        }) { "Calibration labels overlap holdout or contain invalid data" }
        val positives = calibration.count { it.actualUp }
        require(positives in 1 until calibration.size) { "Both label classes are required" }

        fun logit(p: Double): Double {
            val clipped = p.coerceIn(1e-6, 1.0 - 1e-6)
            return ln(clipped / (1.0 - clipped))
        }
        val features = calibration.map { logit(it.rawProbabilityUp) }
        var slope = 1.0
        var intercept = 0.0
        repeat(epochs) {
            var gradSlope = 0.0
            var gradIntercept = 0.0
            for (i in calibration.indices) {
                val z = slope * features[i] + intercept
                val p = if (z >= 0.0) 1.0 / (1.0 + exp(-z)) else exp(z) / (1.0 + exp(z))
                val delta = p - if (calibration[i].actualUp) 1.0 else 0.0
                gradSlope += delta * features[i]
                gradIntercept += delta
            }
            slope -= learningRate * gradSlope / calibration.size
            intercept -= learningRate * gradIntercept / calibration.size
        }
        return ProbabilityCalibrator(slope, intercept)
    }
}
