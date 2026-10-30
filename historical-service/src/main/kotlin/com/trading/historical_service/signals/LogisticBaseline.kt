package com.trading.historical_service.signals

import kotlin.math.exp
import kotlin.math.ln

/** Deterministic numeric baseline; fit only on chronological training rows. */
data class LogisticModel(val coefficients: List<Double>, val intercept: Double, val version: String) {
    fun probabilityUp(values: List<Double>): Double {
        require(values.size == coefficients.size && values.all(Double::isFinite))
        val z = intercept + values.zip(coefficients).sumOf { (v, w) -> v * w }
        return if (z >= 0) 1.0 / (1.0 + exp(-z))
        else exp(z) / (1.0 + exp(z))
    }
}

/**
 * Standardization must be fit on training only and reused unchanged for test.
 * This implementation intentionally refuses tiny datasets.
 */
object LogisticBaseline {
    const val VERSION = "logistic-baseline-v1"
    const val MIN_TRAIN_ROWS = 40

    fun vector(example: EvaluationExample): List<Double> {
        val f = example.features
        require(f.ema26 > 0)
        return listOf(
            (f.ema12 - f.ema26) / f.ema26,
            f.rsi14 / 100.0,
            f.macdHistogram / f.ema26,
            f.volatility20,
            f.volumeRatio20
        ).also { require(it.all(Double::isFinite)) }
    }

    data class Fitted(val model: LogisticModel, val means: List<Double>, val scales: List<Double>) {
        fun predict(example: EvaluationExample): Double {
            val v = vector(example)
            return model.probabilityUp(v.indices.map { (v[it] - means[it]) / scales[it] })
        }
    }

    fun fit(train: List<EvaluationExample>, epochs: Int = 500, learningRate: Double = 0.05): Fitted {
        require(train.size >= MIN_TRAIN_ROWS) { "Insufficient observed labeled rows for training" }
        require(epochs in 1..10_000 && learningRate > 0.0 && learningRate.isFinite())
        require(train.zipWithNext().all { (a, b) -> a.predictionTimeMs <= b.predictionTimeMs })
        val vectors = train.map(::vector)
        val dimensions = vectors.first().size
        val means = (0 until dimensions).map { j -> vectors.map { it[j] }.average() }
        val scales = (0 until dimensions).map { j ->
            kotlin.math.sqrt(vectors.map { (it[j] - means[j]) * (it[j] - means[j]) }.average())
                .coerceAtLeast(1e-9)
        }
        val samples = vectors.map { v -> v.indices.map { (v[it] - means[it]) / scales[it] } }
        val weights = MutableList(dimensions) { 0.0 }
        var bias = 0.0
        repeat(epochs) {
            val grad = DoubleArray(dimensions)
            var gradBias = 0.0
            for (i in samples.indices) {
                val z = bias + samples[i].indices.sumOf { j -> samples[i][j] * weights[j] }
                val p = if (z >= 0) 1.0 / (1.0 + exp(-z)) else exp(z) / (1.0 + exp(z))
                val delta = p - if (train[i].up) 1.0 else 0.0
                gradBias += delta
                for (j in grad.indices) grad[j] += delta * samples[i][j]
            }
            val n = samples.size.toDouble()
            bias -= learningRate * gradBias / n
            for (j in weights.indices) weights[j] -= learningRate * grad[j] / n
        }
        return Fitted(LogisticModel(weights.toList(), bias, VERSION), means, scales)
    }

    fun logLoss(probs: List<Double>, labels: List<Boolean>): Double {
        require(probs.isNotEmpty() && probs.size == labels.size)
        return probs.indices.map { i ->
            val p = probs[i].coerceIn(1e-12, 1.0 - 1e-12)
            if (labels[i]) -ln(p) else -ln(1.0 - p)
        }.average()
    }
}
