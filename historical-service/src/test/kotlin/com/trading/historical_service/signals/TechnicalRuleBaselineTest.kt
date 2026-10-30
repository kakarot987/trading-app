package com.trading.historical_service.signals

import com.trading.historical_service.features.FeatureSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class TechnicalRuleBaselineTest {
    private val close = 1_793_347_200_000L
    private fun feature(ema12: Double, ema26: Double, macd: Double, rsi: Double) =
        FeatureSnapshot(
            symbol = "BTCUSDT", interval = "4h", horizonHours = 4,
            featureVersion = "technical-4h-v1", candleCloseTimeMs = close,
            availableAtMs = close + 1_000, ema12 = ema12, ema26 = ema26,
            rsi14 = rsi, macdLine = macd, macdSignal = 0.0,
            macdHistogram = macd, volatility20 = 0.02, volumeRatio20 = 1.0
        )

    @Test fun `bullish agreement generates research up score`() {
        val signal = TechnicalRuleBaseline.predict(feature(110.0, 100.0, 2.0, 60.0), close + 2_000)
        assertEquals(SignalDirection.UP, signal.direction)
        assertEquals(0.60, signal.probabilityUp)
        assertEquals(close + 14_400_000L, signal.expiresAtMs)
        assertEquals("technical-rule-v1", signal.signalVersion)
    }

    @Test fun `bearish agreement generates research down score`() {
        val signal = TechnicalRuleBaseline.predict(feature(90.0, 100.0, -2.0, 40.0), close + 2_000)
        assertEquals(SignalDirection.DOWN, signal.direction)
        assertEquals(0.40, signal.probabilityUp)
    }

    @Test fun `conflicting indicators abstain`() {
        val signal = TechnicalRuleBaseline.predict(feature(110.0, 100.0, -2.0, 60.0), close + 2_000)
        assertEquals(SignalDirection.NO_TRADE, signal.direction)
        assertNull(signal.probabilityUp)
    }

    @Test fun `future data and stale features are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            TechnicalRuleBaseline.predict(feature(110.0, 100.0, 2.0, 60.0), close)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TechnicalRuleBaseline.predict(feature(110.0, 100.0, 2.0, 60.0), close + 14_400_000L)
        }
    }
}
