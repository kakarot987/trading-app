package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperPerformanceTest {
    private fun point(time: Long, equity: String, flow: String = "0") =
        PaperEquityPoint(time, BigDecimal(equity), BigDecimal(flow))

    @Test fun `peak-to-trough drawdown and net return use exact equity`() {
        val result = PaperPerformance.summarize(listOf(
            point(1, "100"), point(2, "120"), point(3, "90"), point(4, "110")
        ))
        assertEquals(BigDecimal("0.25000000"), result.maxDrawdownFraction)
        assertEquals(BigDecimal("0.10000000"), result.netReturnFraction)
        assertEquals(BigDecimal("10"), result.absoluteChangeUsdt)
    }

    @Test fun `rejects deposits stale marks and insufficient history`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperPerformance.summarize(listOf(point(1, "100")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperPerformance.summarize(listOf(point(1, "100"), point(1, "101")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperPerformance.summarize(listOf(point(1, "100"), point(2, "200", "100")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperPerformance.summarize(listOf(point(1, "100"), point(2, "0")))
        }
    }
}
