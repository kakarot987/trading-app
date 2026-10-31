package com.trading.historical_service.evaluation

import java.math.BigDecimal

/**
 * Evaluation provenance is explicit: a record of fills is not a time series of
 * independently marked portfolio equity, and neither is a live performance result.
 */
enum class EvaluationEvidence { PERSISTED_PAPER_FILLS, MARKED_PAPER_EQUITY, HYPOTHETICAL_BENCHMARK }

data class PaperEvaluationReport(
    val evidence: Set<EvaluationEvidence>,
    val activity: PersistedPaperActivity,
    val inventory: List<InventoryPnlResult>?,
    val equity: PaperPerformanceSummary?,
    val intervalRisk: PaperRiskSummary?,
    val benchmark: BenchmarkComparison?,
    val limitations: List<String>
)

/**
 * Collects precomputed quantities, deliberately refusing to synthesize
 * missing marks, infer returns from cash ledger movements, or silently mix
 * hypothetical and persisted data.
 */
object PaperEvaluationReports {
    fun assemble(
        activity: PersistedPaperActivity,
        inventory: List<InventoryPnlResult>? = null,
        equity: PaperPerformanceSummary? = null,
        intervalRisk: PaperRiskSummary? = null,
        benchmark: BenchmarkComparison? = null
    ): PaperEvaluationReport {
        require(activity.fillCount >= 0 && activity.tradedNotionalUsdt >= BigDecimal.ZERO &&
            activity.paidFeesUsdt >= BigDecimal.ZERO)
        require((equity == null) == (intervalRisk == null)) {
            "Equity and risk must use the same validated observation window"
        }
        require(benchmark == null || equity != null) {
            "Benchmark requires observed strategy equity over the same interval"
        }
        val evidence = mutableSetOf(EvaluationEvidence.PERSISTED_PAPER_FILLS)
        if (equity != null) evidence += EvaluationEvidence.MARKED_PAPER_EQUITY
        if (benchmark != null) evidence += EvaluationEvidence.HYPOTHETICAL_BENCHMARK
        val limitations = mutableListOf(
            "Paper trades only; no live execution or externally verified performance.",
            "USDT ledger movements are not profit or return."
        )
        if (inventory == null) limitations += "Inventory P&L unavailable: complete fill history and marks required."
        if (equity == null) limitations += "Returns and drawdown unavailable: validated chronological equity marks required."
        if (benchmark == null) limitations += "Buy-and-hold comparison unavailable: matching start/end prices required."
        return PaperEvaluationReport(evidence, activity, inventory, equity, intervalRisk, benchmark, limitations)
    }
}
