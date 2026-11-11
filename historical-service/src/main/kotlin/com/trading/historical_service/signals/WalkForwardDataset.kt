package com.trading.historical_service.signals

import com.trading.historical_service.features.CandleSeries
import com.trading.historical_service.features.FeatureSnapshot

/**
 * Each row predicts the next completed 4h candle close from features already
 * observable at the prediction cutoff. Never backdate newly computed features.
 */
data class EvaluationExample(
    val symbol: String,
    val featureVersion: String,
    val predictionTimeMs: Long,
    val labelAvailableAtMs: Long,
    val features: FeatureSnapshot,
    val nextReturn: Double,
    val up: Boolean
)

object WalkForwardDataset {
    private const val FOUR_HOURS = CandleSeries.FOUR_HOURS_MS

    fun build(
        snapshots: List<FeatureSnapshot>,
        candles: List<CandleSeries.Bar>,
        // Optional observed-time map for production data. Empty is permitted only
        // for existing in-memory fixtures; database callers must supply provenance.
        candleObservedAtMs: Map<Long, Long> = emptyMap()
    ): List<EvaluationExample> {
        val closeByTime = candles.associateBy { it.closeTimeMs }
        if (candleObservedAtMs.isNotEmpty()) {
            require(candles.all { candleObservedAtMs.containsKey(it.closeTimeMs) }) {
                "Every market candle needs known observation provenance"
            }
        }
        require(closeByTime.size == candles.size) { "Duplicate candle close times" }
        return snapshots.mapNotNull { feature ->
            require(feature.interval == "4h" && feature.horizonHours == 4)
            require(feature.candleCloseTimeMs <= feature.availableAtMs)
            // Only observations captured at the close can be used to predict
            // the immediately following bar. Late backfills are not backdated.
            if (feature.availableAtMs >= feature.candleCloseTimeMs + FOUR_HOURS) return@mapNotNull null
            val previous = closeByTime[feature.candleCloseTimeMs] ?: return@mapNotNull null
            val next = closeByTime[feature.candleCloseTimeMs + FOUR_HOURS] ?: return@mapNotNull null
            require(feature.symbol in CandleSeries.spotSymbols)
            // A revised current candle seen only after feature availability cannot
            // recreate the price the model saw. A late next candle likewise cannot
            // be used as a label at its original close; exclude rather than backdate.
            if (candleObservedAtMs.isNotEmpty()) {
                if (requireNotNull(candleObservedAtMs[previous.closeTimeMs]) > feature.availableAtMs)
                    return@mapNotNull null
                if (requireNotNull(candleObservedAtMs[next.closeTimeMs]) > next.closeTimeMs)
                    return@mapNotNull null
            }
            require(previous.close.isFinite() && previous.close > 0.0)
            require(previous.closeTimeMs + FOUR_HOURS == next.closeTimeMs)
            require(feature.availableAtMs < next.closeTimeMs)
            require(next.close.isFinite() && next.close > 0.0)
            val ret = next.close / previous.close - 1.0
            EvaluationExample(
                symbol = feature.symbol,
                featureVersion = feature.featureVersion,
                predictionTimeMs = feature.availableAtMs,
                labelAvailableAtMs = next.closeTimeMs,
                features = feature,
                nextReturn = ret,
                up = ret > 0.0
            )
        }.sortedWith(compareBy<EvaluationExample> { it.predictionTimeMs }.thenBy { it.symbol })
    }

    /** Purged chronological holdout: labels from training must predate test predictions. */
    data class Split(val train: List<EvaluationExample>, val test: List<EvaluationExample>)

    fun splitChronologically(rows: List<EvaluationExample>, testStartMs: Long): Split {
        require(rows.zipWithNext().all { (a, b) -> a.predictionTimeMs <= b.predictionTimeMs }) {
            "Rows must be chronological"
        }
        val train = rows.filter { it.predictionTimeMs < testStartMs && it.labelAvailableAtMs <= testStartMs }
        val test = rows.filter { it.predictionTimeMs >= testStartMs }
        require(train.isNotEmpty() && test.isNotEmpty()) { "Both partitions must have observations" }
        return Split(train, test)
    }
}
