package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperBenchmarkTest {
    private fun d(s: String) = BigDecimal(s)
    private fun window() = BenchmarkWindow(
        startMs = 100, endMs = 200,
        startingEquityUsdt = d("100"), endingEquityUsdt = d("110"),
        startAssetPriceUsdt = d("50"), endAssetPriceUsdt = d("60"),
        asset = "BTC"
    )

    @Test fun `compares identical window returns and percentage point spread`() {
        val comparison = PaperBenchmark.compare(window())
        assertEquals(d("0.10000000"), comparison.strategyReturnFraction)
        assertEquals(d("0.20000000"), comparison.buyHoldReturnFraction)
        assertEquals(d("-10.00000000"), comparison.excessReturnPercentagePoints)
        assertEquals(100, comparison.startMs)
        assertEquals(200, comparison.endMs)
    }

    @Test fun `rejects inconsistent intervals and deposits`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperBenchmark.compare(window().copy(endMs = 100))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperBenchmark.compare(window().copy(netExternalFlowUsdt = d("20")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperBenchmark.compare(window().copy(startAssetPriceUsdt = d("0")))
        }
    }
}
