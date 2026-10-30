package com.trading.historical_service.signals

import com.trading.historical_service.features.FeatureSnapshot

/**
 * Deterministic research benchmark. The 0.60 / 0.40 outputs are fixed
 * heuristic scores, NOT empirically calibrated probabilities.
 * Never use as actionable trading advice.
 */
object TechnicalRuleBaseline {
    const val VERSION = "technical-rule-v1"
    private const val HORIZON_MS = 4L * 60 * 60 * 1000

    fun predict(features: FeatureSnapshot, generatedAtMs: Long): DirectionalSignal {
        require(features.symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(features.horizonHours == 4 && features.interval == "4h")
        require(features.availableAtMs <= generatedAtMs) { "Future feature observation is not available yet" }
        require(features.candleCloseTimeMs <= features.availableAtMs)
        require(generatedAtMs <= Long.MAX_VALUE - HORIZON_MS)
        require(generatedAtMs < features.candleCloseTimeMs + HORIZON_MS) {
            "Stale features cannot generate a current 4h directional signal"
        }
        val inputs = listOf(features.ema12, features.ema26, features.rsi14,
            features.macdHistogram, features.volatility20, features.volumeRatio20)
        require(inputs.all { it.isFinite() })
        require(features.rsi14 in 0.0..100.0 && features.volatility20 >= 0.0 &&
            features.volumeRatio20 >= 0.0)

        val bullish = features.ema12 > features.ema26 &&
            features.macdHistogram > 0.0 &&
            features.rsi14 in 50.0..70.0
        val bearish = features.ema12 < features.ema26 &&
            features.macdHistogram < 0.0 &&
            features.rsi14 in 30.0..50.0
        val direction = when {
            bullish -> SignalDirection.UP
            bearish -> SignalDirection.DOWN
            else -> SignalDirection.NO_TRADE
        }
        val score = when (direction) {
            SignalDirection.UP -> 0.60
            SignalDirection.DOWN -> 0.40
            SignalDirection.NO_TRADE -> null
        }
        return DirectionalSignal(
            symbol = features.symbol,
            horizonHours = 4,
            signalVersion = VERSION,
            featureVersion = features.featureVersion,
            featureCandleCloseMs = features.candleCloseTimeMs,
            featureAvailableAtMs = features.availableAtMs,
            generatedAtMs = generatedAtMs,
            expiresAtMs = features.candleCloseTimeMs + HORIZON_MS,
            probabilityUp = score,
            direction = direction,
            rationale = "Uncalibrated technical-rule research baseline; EMA/MACD/RSI agreement."
        )
    }
}
