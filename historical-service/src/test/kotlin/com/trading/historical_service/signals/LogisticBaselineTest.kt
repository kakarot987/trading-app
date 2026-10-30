package com.trading.historical_service.signals

import com.trading.historical_service.features.FeatureSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LogisticBaselineTest {
    private val h = 14_400_000L
    private fun example(i: Int): EvaluationExample {
        val close = (i + 1L) * h
        val f = FeatureSnapshot(
            "BTCUSDT", "4h", 4, "technical-4h-v1",
            close, close, 100.0 + i % 3, 100.0, 40.0 + i % 20,
            0.0, 0.0, (i % 7 - 3).toDouble(), 0.01, 1.0 + i % 5 / 10.0
        )
        return EvaluationExample("BTCUSDT", f.featureVersion, close, close + h, f,
            if (i % 2 == 0) 0.01 else -0.01, i % 2 == 0)
    }

    @Test
    fun `training refuses sparse production observations`() {
        assertThrows(IllegalArgumentException::class.java) {
            LogisticBaseline.fit(listOf(example(0)))
        }
    }

    @Test
    fun `synthetic deterministic fixture produces bounded finite scores`() {
        val train = (0..59).map(::example)
        val fitted = LogisticBaseline.fit(train)
        val scores = (60..65).map { fitted.predict(example(it)) }
        assertTrue(scores.all { it.isFinite() && it in 0.0..1.0 })
        assertEquals(scores, (60..65).map { fitted.predict(example(it)) })
        assertTrue(LogisticBaseline.logLoss(scores, (60..65).map { it % 2 == 0 }).isFinite())
    }

    @Test
    fun `feature vector requires valid EMA normalization`() {
        assertThrows(IllegalArgumentException::class.java) {
            LogisticBaseline.vector(example(0).copy(
                features = example(0).features.copy(ema26 = 0.0)
            ))
        }
    }
}
