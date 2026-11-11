package com.trading.historical_service.evaluation

import java.math.BigDecimal

/**
 * Evaluation provenance is explicit: a record of fills is not a time series of
 * independently marked portfolio equity, and neither is a live performance result.
 */
enum class EvaluationEvidence {
    PERSISTED_PAPER_FILLS, PERSISTED_REALIZED_INVENTORY,
    MARKED_PAPER_EQUITY, HYPOTHETICAL_BENCHMARK
}

data class EvaluationTimeWindow(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs > startMs) }
}

data class PaperEvaluationReport(
    val evidence: Set<EvaluationEvidence>,
    val activity: PersistedPaperActivity,
    val inventory: List<InventoryPnlResult>?,
    val equity: PaperPerformanceSummary?,
    val intervalRisk: PaperRiskSummary?,
    val benchmark: BenchmarkComparison?,
    val limitations: List<String>,
    val realizedInventory: List<PersistedRealizedInventory>? = null
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
        benchmark: BenchmarkComparison? = null,
        realizedInventory: List<PersistedRealizedInventory>? = null,
        equityWindow: EvaluationTimeWindow? = null
    ): PaperEvaluationReport {
        require(activity.fillCount >= 0 && activity.tradedNotionalUsdt >= BigDecimal.ZERO &&
            activity.paidFeesUsdt >= BigDecimal.ZERO)
        require((equity == null) == (intervalRisk == null)) {
            "Equity and risk must use the same validated observation window"
        }
        require(benchmark == null || equity != null) {
            "Benchmark requires observed strategy equity over the same interval"
        }
        require(benchmark == null || (equityWindow != null &&
            equityWindow.startMs == benchmark.startMs &&
            equityWindow.endMs == benchmark.endMs)) {
            "Benchmark and strategy equity must share a documented observation window"
        }
        require(realizedInventory == null || (
            realizedInventory.sumOf { it.fillCount.toLong() } == activity.fillCount &&
            realizedInventory.sumOf { it.netQuoteMovementUsdt }
                .compareTo(activity.quoteLedgerNetUsdt) == 0 &&
            realizedInventory.map { it.asset }.distinct().size == realizedInventory.size &&
            realizedInventory.all {
                it.asset in setOf("BTC", "ETH") &&
                    it.remainingQuantity.signum() >= 0 &&
                    it.remainingCostBasisUsdt.signum() >= 0 &&
                    it.fillCount > 0
            }
        )) { "Persisted inventory does not reconcile with committed paper activity" }
        val evidence = mutableSetOf(EvaluationEvidence.PERSISTED_PAPER_FILLS)
        if (!realizedInventory.isNullOrEmpty())
            evidence += EvaluationEvidence.PERSISTED_REALIZED_INVENTORY
        if (equity != null) evidence += EvaluationEvidence.MARKED_PAPER_EQUITY
        if (benchmark != null) evidence += EvaluationEvidence.HYPOTHETICAL_BENCHMARK
        val limitations = mutableListOf(
            "Paper trades only; no live execution or externally verified performance.",
            "USDT ledger movements are not profit or return."
        )
        if (inventory == null) limitations +=
            "Unrealized inventory P&L unavailable: contemporaneous observed marks required."
        if (realizedInventory == null) limitations +=
            "Realized inventory cost basis unavailable: persisted complete fill history required."
        else limitations += "Realized paper inventory is not marked equity or strategy return."
        if (equity == null) limitations += "Returns and drawdown unavailable: validated chronological equity marks required."
        if (benchmark == null) limitations += "Buy-and-hold comparison unavailable: matching start/end prices required."
        return PaperEvaluationReport(evidence, activity, inventory, equity, intervalRisk,
            benchmark, limitations, realizedInventory)
    }
}
