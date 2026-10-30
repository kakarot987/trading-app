package com.trading.historical_service.signals

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProbabilityCalibrationTest {
    private val h = 14_400_000L
    private fun sample(i: Int) = CalibrationExample(
        predictedAtMs = (i + 1L) * h,
        labelAvailableAtMs = (i + 2L) * h,
        rawProbabilityUp = if (i % 2 == 0) 0.7 else 0.3,
        actualUp = i % 2 == 0
    )

    @Test fun `sparse calibration data fails closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbabilityCalibration.fit(listOf(sample(0)), holdoutStartMs = 100 * h)
        }
    }

    @Test fun `calibration window cannot use holdout labels`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbabilityCalibration.fit((0 until 40).map(::sample), holdoutStartMs = 40 * h)
        }
    }

    @Test fun `synthetic chronological calibration yields bounded deterministic scores`() {
        val data = (0 until 60).map(::sample)
        val first = ProbabilityCalibration.fit(data, holdoutStartMs = 62 * h)
        val second = ProbabilityCalibration.fit(data, holdoutStartMs = 62 * h)
        assertEquals(first, second)
        assertTrue(first.transform(0.7) > first.transform(0.3))
        assertTrue(first.transform(0.7) in 0.0..1.0)
        assertThrows(IllegalArgumentException::class.java) { first.transform(Double.NaN) }
    }

    @Test fun `calibration requires both classes`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbabilityCalibration.fit((0 until 40).map { sample(it).copy(actualUp = true) }, 42 * h)
        }
    }
}
