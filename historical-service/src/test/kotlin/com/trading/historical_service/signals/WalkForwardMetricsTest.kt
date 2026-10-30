package com.trading.historical_service.signals

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WalkForwardMetricsTest {
    @Test fun `refuses sparse holdout evidence`() {
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardMetrics.evaluate("logistic-baseline-v1", listOf(ScoredObservation(0.5, true)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardMetrics.evaluate("baseline", List(10) { ScoredObservation(0.5, true) }, minTestRows = 1)
        }
    }

    @Test fun `calculates accuracy brier and log loss on synthetic holdout`() {
        val fixture = List(20) { i -> ScoredObservation(if (i % 2 == 0) 0.8 else 0.2, i % 2 == 0) }
        val result = WalkForwardMetrics.evaluate("synthetic-fixture", fixture)
        assertEquals(20, result.count)
        assertEquals(10, result.positives)
        assertEquals(1.0, result.accuracy, 1e-10)
        assertEquals(0.04, result.brier, 1e-10)
        assertEquals(-kotlin.math.ln(0.8), result.logLoss, 1e-10)
    }

    @Test fun `invalid probabilities are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WalkForwardMetrics.evaluate("fixture", List(20) { ScoredObservation(Double.NaN, true) })
        }
    }
}
