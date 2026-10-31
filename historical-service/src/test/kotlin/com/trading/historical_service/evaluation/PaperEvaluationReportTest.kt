package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperEvaluationReportTest {
    private fun activity() = PersistedPaperActivity(1, BigDecimal("100"), BigDecimal("0.1"), BigDecimal("-100.1"))

    @Test fun `fills alone never become invented returns or marked inventory`() {
        val report = PaperEvaluationReports.assemble(activity())
        assertNull(report.equity)
        assertNull(report.inventory)
        assertNull(report.benchmark)
        assertEquals(setOf(EvaluationEvidence.PERSISTED_PAPER_FILLS), report.evidence)
        assertTrue(report.limitations.any { it.contains("Returns and drawdown unavailable") })
    }

    @Test fun `benchmark without equity observations is rejected`() {
        val benchmark = BenchmarkComparison("BTC", 1, 2, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), benchmark = benchmark)
        }
    }

    @Test fun `equity and interval risk must be supplied together`() {
        val equity = PaperPerformanceSummary(2, BigDecimal("100"), BigDecimal("110"),
            BigDecimal("10"), BigDecimal.ZERO, BigDecimal("0.1"))
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), equity = equity)
        }
    }
}
