package com.trading.historical_service.signals

import com.trading.historical_service.features.FeatureSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WalkForwardRunnerTest {
    private val interval = 14_400_000L
    private fun example(i: Int): EvaluationExample {
        val at = (i + 1L) * interval
        val f = FeatureSnapshot(
            "BTCUSDT", "4h", 4, "technical-4h-v1", at, at,
            100.0 + i % 4, 100.0, 40.0 + i % 20,
            0.0, 0.0, (i % 7 - 3).toDouble(), 0.01, 1.0 + (i % 3) * 0.1
        )
        return EvaluationExample(f.symbol, f.featureVersion, at, at + interval,
            f, if (i % 2 == 0) 0.01 else -0.01, i % 2 == 0)
    }

    @Test fun `sparse real-life coverage refuses evaluation`() {
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardRunner.evaluate(listOf(example(0)))
        }
    }

    @Test fun `synthetic expanding holdout yields bounded metrics`() {
        val report = WalkForwardRunner.evaluate((0..61).map(::example))
        assertEquals(22, report.metrics.count)
        assertTrue(report.metrics.accuracy in 0.0..1.0)
        assertTrue(report.metrics.brier in 0.0..1.0)
        assertTrue(report.metrics.logLoss.isFinite())
        assertEquals(41L * interval, report.firstTestAtMs)
        assertEquals(62L * interval, report.lastTestAtMs)
    }

    @Test fun `future label availability is rejected`() {
        val bad = example(0).copy(labelAvailableAtMs = example(0).predictionTimeMs)
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardRunner.evaluate(listOf(bad))
        }
    }

    @Test fun `out of order predictions are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardRunner.evaluate(listOf(example(1), example(0)))
        }
    }
}
