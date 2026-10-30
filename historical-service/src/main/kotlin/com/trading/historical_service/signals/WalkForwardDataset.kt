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
        candles: List<CandleSeries.Bar>
    ): List<EvaluationExample> {
        val closeByTime = candles.associateBy { it.closeTimeMs }
        require(closeByTime.size == candles.size) { "Duplicate candle close times" }
        return snapshots.mapNotNull { feature ->
            require(feature.interval == "4h" && feature.horizonHours == 4)
            require(feature.candleCloseTimeMs <= feature.availableAtMs)
            // Only observations captured at the close can be used to predict
            // the immediately following bar. Late backfills are not backdated.
            if (feature.availableAtMs >= feature.candleCloseTimeMs + FOUR_HOURS) return@mapNotNull null
            val previous = closeByTime[feature.candleCloseTimeMs] ?: return@mapNotNull null
            val next = closeByTime[feature.candleCloseTimeMs + FOUR_HOURS] ?: return@mapNotNull null
            require(previous.close.isFinite() && previous.close > 0.0)
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
