package com.trading.historical_service.signals

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SignalContractTest {
    private val close = 1_793_347_200_000L

    @Test
    fun `abstention baseline emits versioned expiring no-trade signal`() {
        val signal = AbstentionBaseline.predict("BTCUSDT", "technical-4h-v1",
            close, close + 60_000L, close + 120_000L)
        assertEquals(SignalDirection.NO_TRADE, signal.direction)
        assertNull(signal.probabilityUp)
        assertEquals("no-trade-v1", signal.signalVersion)
        assertEquals(4L * 60 * 60 * 1000, signal.expiresAtMs - signal.generatedAtMs)
    }

    @Test
    fun `signals reject future information, invalid probability and unsupported symbols`() {
        assertThrows(IllegalArgumentException::class.java) {
            AbstentionBaseline.predict("BTCUSDT", "technical-4h-v1",
                close, close + 120_000L, close + 60_000L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AbstentionBaseline.predict("DOGEUSDT", "technical-4h-v1",
                close, close, close)
        }
        val base = AbstentionBaseline.predict("ETHUSDT", "technical-4h-v1",
            close, close, close)
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(direction = SignalDirection.UP, probabilityUp = null)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(probabilityUp = 1.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            base.copy(expiresAtMs = base.generatedAtMs)
        }
    }
}
