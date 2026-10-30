package com.trading.historical_service.signals

import com.trading.historical_service.features.FeatureSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CalibratedWalkForwardTest {
    private val h = 14_400_000L
    private fun sample(i: Int): EvaluationExample {
        val t = (i + 1L) * h
        val f = FeatureSnapshot("BTCUSDT", "4h", 4, "technical-4h-v1",
            t, t, 100.0 + i % 4, 100.0, 40.0 + i % 20,
            0.0, 0.0, (i % 7 - 3).toDouble(), 0.01, 1.0 + i % 3 * 0.1)
        return EvaluationExample(f.symbol, f.featureVersion, t, t + h,
            f, if (i % 2 == 0) 0.01 else -0.01, i % 2 == 0)
    }

    @Test fun `distinct chronological windows produce independent holdout metrics`() {
        val examples = (0..109).map(::sample)
        val report = CalibratedWalkForward.evaluate(examples, 42 * h, 85 * h)
        assertEquals(42, report.calibrationSize)
        assertEquals(26, report.raw.count)
        assertEquals(26, report.calibrated.count)
        assertTrue(report.raw.brier.isFinite())
        assertTrue(report.calibrated.logLoss.isFinite())
    }

    @Test fun `sparse observations refuse calibrated evaluation`() {
        assertThrows(IllegalArgumentException::class.java) {
            CalibratedWalkForward.evaluate(listOf(sample(0)), 42 * h, 85 * h)
        }
    }

    @Test fun `overlapping chronological windows fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            CalibratedWalkForward.evaluate((0..109).map(::sample), 85 * h, 85 * h)
        }
    }
}
