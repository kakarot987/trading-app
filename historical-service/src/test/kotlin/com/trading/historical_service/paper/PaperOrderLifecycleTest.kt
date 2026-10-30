package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperOrderLifecycleTest {
    private val request = PaperOrderRequest("order-123", "BTCUSDT",
        PaperOrderSide.BUY, PaperDecimal.positive("0.10000000"))
    private val initial = PaperOrderState(request, submittedAtMs = 1000L)
    private val tick = PaperMarketTick("BTCUSDT", 1001L, BigDecimal("100"))

    @Test fun `new order can fill once on a future tick`() {
        val filled = PaperOrderLifecycle.fill(initial, tick)
        assertEquals(PaperOrderStatus.FILLED, filled.status)
        assertNotNull(filled.fill)
        assertThrows(IllegalArgumentException::class.java) {
            PaperOrderLifecycle.fill(filled, tick.copy(timestampMs = 1002L))
        }
    }

    @Test fun `cancelled or rejected orders never fill`() {
        val cancelled = PaperOrderLifecycle.cancel(initial)
        assertEquals(PaperOrderStatus.CANCELLED, cancelled.status)
        assertThrows(IllegalArgumentException::class.java) {
            PaperOrderLifecycle.fill(cancelled, tick)
        }
        val rejected = PaperOrderLifecycle.reject(initial, "Insufficient paper USDT")
        assertEquals("Insufficient paper USDT", rejected.rejectionReason)
        assertThrows(IllegalArgumentException::class.java) {
            PaperOrderLifecycle.cancel(rejected)
        }
    }

    @Test fun `same time ticks and invalid states fail closed`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperOrderLifecycle.fill(initial, tick.copy(timestampMs = 1000L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperOrderLifecycle.reject(initial, " ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            initial.copy(status = PaperOrderStatus.FILLED)
        }
    }
}
