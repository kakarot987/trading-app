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

    @Test fun `realized inventory must reconcile with activity and never becomes equity return`() {
        val position = PersistedRealizedInventory(
            "BTC", 1, BigDecimal("1"), BigDecimal("100.1"), BigDecimal.ZERO,
            BigDecimal("-100.1")
        )
        val report = PaperEvaluationReports.assemble(activity(), realizedInventory = listOf(position))
        assertTrue(report.evidence.contains(EvaluationEvidence.PERSISTED_REALIZED_INVENTORY))
        assertNull(report.equity)
        assertNull(report.benchmark)
        assertNull(report.inventory)
        assertEquals(listOf(position), report.realizedInventory)

        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), realizedInventory = listOf(
                position.copy(netQuoteMovementUsdt = BigDecimal("-99"))
            ))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), realizedInventory = emptyList())
        }
    }

    @Test fun `benchmark must match an explicit equity window`() {
        val equity = PaperPerformanceSummary(2, BigDecimal("100"), BigDecimal("110"),
            BigDecimal("10"), BigDecimal.ZERO, BigDecimal("0.1"))
        val risk = PaperRiskSummary(1, BigDecimal("0.1"), null,
            BigDecimal.ZERO, 1, 0)
        val benchmark = BenchmarkComparison("BTC", 1, 2, BigDecimal("0.1"),
            BigDecimal("0.2"), BigDecimal("-10"))
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), equity = equity,
                intervalRisk = risk, benchmark = benchmark)
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), equity = equity,
                intervalRisk = risk, benchmark = benchmark,
                equityWindow = EvaluationTimeWindow(1, 3))
        }
        val valid = PaperEvaluationReports.assemble(activity(), equity = equity,
            intervalRisk = risk, benchmark = benchmark,
            equityWindow = EvaluationTimeWindow(1, 2))
        assertEquals(benchmark, valid.benchmark)
        assertTrue(valid.evidence.contains(EvaluationEvidence.HYPOTHETICAL_BENCHMARK))
    }

    @Test fun `equity and interval risk must be supplied together`() {
        val equity = PaperPerformanceSummary(2, BigDecimal("100"), BigDecimal("110"),
            BigDecimal("10"), BigDecimal.ZERO, BigDecimal("0.1"))
        assertThrows(IllegalArgumentException::class.java) {
            PaperEvaluationReports.assemble(activity(), equity = equity)
        }
    }
}
