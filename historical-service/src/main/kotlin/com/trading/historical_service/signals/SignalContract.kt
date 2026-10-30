package com.trading.historical_service.signals

import java.time.Instant

/** M5 contract: research signals only, never executable trade instructions. */
enum class SignalDirection { UP, DOWN, NO_TRADE }

data class DirectionalSignal(
    val symbol: String,
    val horizonHours: Int,
    val signalVersion: String,
    val featureVersion: String,
    val featureCandleCloseMs: Long,
    val featureAvailableAtMs: Long,
    val generatedAtMs: Long,
    val expiresAtMs: Long,
    val probabilityUp: Double?,
    val direction: SignalDirection,
    val rationale: String
) {
    init {
        require(symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(horizonHours == 4)
        require(signalVersion.isNotBlank() && featureVersion.isNotBlank())
        require(featureCandleCloseMs <= featureAvailableAtMs)
        require(featureAvailableAtMs <= generatedAtMs)
        require(expiresAtMs > generatedAtMs)
        require(probabilityUp == null || (probabilityUp.isFinite() && probabilityUp in 0.0..1.0))
        require(direction == SignalDirection.NO_TRADE || probabilityUp != null)
        require(rationale.isNotBlank())
    }
}

/** A deterministic safety baseline, not a trained or calibrated model. */
object AbstentionBaseline {
    const val VERSION = "no-trade-v1"
    fun predict(
        symbol: String,
        featureVersion: String,
        candleCloseMs: Long,
        availableAtMs: Long,
        generatedAtMs: Long
    ): DirectionalSignal {
        val duration = 4L * 60 * 60 * 1000
        require(generatedAtMs <= Long.MAX_VALUE - duration)
        return DirectionalSignal(
            symbol = symbol, horizonHours = 4,
            signalVersion = VERSION, featureVersion = featureVersion,
            featureCandleCloseMs = candleCloseMs,
            featureAvailableAtMs = availableAtMs,
            generatedAtMs = generatedAtMs,
            expiresAtMs = generatedAtMs + duration,
            probabilityUp = null,
            direction = SignalDirection.NO_TRADE,
            rationale = "Baseline abstains; no calibrated directional evidence."
        )
    }
}
