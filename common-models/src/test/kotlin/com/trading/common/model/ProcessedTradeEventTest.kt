package com.trading.common.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ProcessedTradeEventTest {
    private fun event(tradeId: String?) = TradeEvent(
        symbol = "BTCUSDT",
        exchange = "BINANCE",
        price = 70000.0,
        volume = 0.01,
        tradeTimestamp = 1700000000000L,
        side = "BUY",
        tradeId = tradeId,
        processedAt = 1700000000100L
    )

    @Test
    fun `processed event preserves the source trade ID`() {
        val id = "BINANCE-123456"
        assertEquals(id, ProcessedTradeEvent.from(event(id)).tradeId)
    }

    @Test
    fun `missing trade ID is rejected rather than replaced with random UUID`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessedTradeEvent.from(event(null))
        }
    }

    @Test
    fun `blank trade ID is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProcessedTradeEvent.from(event("   "))
        }
    }
}
