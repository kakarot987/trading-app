package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperRiskMetricsTest {
    private fun p(t: Long, v: String, flow: String = "0") =
        PaperEquityPoint(t, BigDecimal(v), BigDecimal(flow))

    @Test fun `flat equity has zero interval risk`() {
        val result = PaperRiskMetrics.summarize(listOf(p(1, "100"), p(2, "100"), p(3, "100")))
        assertEquals(BigDecimal("0.00000000"), result.meanIntervalReturn)
        assertEquals(BigDecimal("0.00000000"), result.sampleIntervalVolatility)
        assertEquals(BigDecimal("0.00000000"), result.downsideDeviation)
        assertEquals(0, result.positiveIntervals)
        assertEquals(0, result.negativeIntervals)
    }

    @Test fun `single interval has no sample volatility`() {
        val result = PaperRiskMetrics.summarize(listOf(p(1, "100"), p(2, "110")))
        assertEquals(BigDecimal("0.10000000"), result.meanIntervalReturn)
        assertNull(result.sampleIntervalVolatility)
        assertEquals(1, result.positiveIntervals)
    }

    @Test fun `rejects deposit and repeated timestamps`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperRiskMetrics.summarize(listOf(p(1, "100"), p(2, "120", "20")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperRiskMetrics.summarize(listOf(p(1, "100"), p(1, "101")))
        }
    }
}
