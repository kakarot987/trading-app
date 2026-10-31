package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperCostAndExposureTest {
    private fun d(s: String) = BigDecimal(s)

    @Test fun `fees and adverse execution costs are separate`() {
        val result = PaperCostAndExposure.summarizeCosts(listOf(
            EvaluatedPaperFill(1000, "BTCUSDT", "BUY", d("0.1"), d("100.04"), d("100"), d("0.010004")),
            EvaluatedPaperFill(2000, "BTCUSDT", "SELL", d("0.1"), d("99.96"), d("100"), d("0.009996"))
        ))
        assertEquals(d("20.00000000"), result.grossTurnoverUsdt)
        assertEquals(d("0.02000000"), result.feesUsdt)
        assertEquals(d("0.00800000"), result.executionCostUsdt)
        assertEquals(d("0.02800000"), result.totalEstimatedCostUsdt)
    }

    @Test fun `spot exposure excludes cash and rejects missing price marks`() {
        val result = PaperCostAndExposure.exposure(
            2000, d("50"), mapOf("BTC" to d("0.5")), mapOf("BTC" to d("100")))
        assertEquals(d("100.00000000"), result.equityUsdt)
        assertEquals(d("50.00000000"), result.grossSpotExposureUsdt)
        assertEquals(d("0.50000000"), result.exposureFraction)
        assertThrows(IllegalArgumentException::class.java) {
            PaperCostAndExposure.exposure(2000, d("50"), mapOf("BTC" to d("0.5")), emptyMap())
        }
    }
}
