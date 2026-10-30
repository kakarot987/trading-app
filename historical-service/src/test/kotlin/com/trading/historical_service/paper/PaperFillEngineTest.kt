package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperFillEngineTest {
    private val buy = PaperOrderRequest("paper-1", "BTCUSDT", PaperOrderSide.BUY,
        PaperDecimal.positive("0.10000000"))
    private val tick = PaperMarketTick("BTCUSDT", 2001L, BigDecimal("100.00000000"))

    @Test fun `buy includes adverse spread slippage and quote fee`() {
        val fill = PaperFillEngine.fill(buy, 2000L, tick)
        assertEquals(PaperDecimal.exact("100.04000000"), fill.price)
        assertEquals(PaperDecimal.exact("10.00400000"), fill.grossQuote)
        assertEquals(PaperDecimal.exact("0.01000400"), fill.feeQuote)
        assertEquals(PaperDecimal.exact("10.01400400"), fill.netQuoteDebit)
    }

    @Test fun `sell receives after fee and unfavorable execution price`() {
        val fill = PaperFillEngine.fill(buy.copy(side = PaperOrderSide.SELL), 2000L, tick)
        assertEquals(PaperDecimal.exact("99.96000000"), fill.price)
        assertEquals(PaperDecimal.exact("9.99600000"), fill.grossQuote)
        assertEquals(PaperDecimal.exact("0.00999600"), fill.feeQuote)
        assertEquals(BigDecimal("-9.98600400"), fill.netQuoteDebit)
    }

    @Test fun `cannot fill using same-time or historical ticks`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperFillEngine.fill(buy, 2001L, tick)
        }
        assertNull(PaperFillEngine.fillAtFirstFutureTick(buy, 3000L, listOf(tick)))
    }

    @Test fun `earliest strictly future tick wins even on unsorted input`() {
        val result = PaperFillEngine.fillAtFirstFutureTick(buy, 2000L,
            listOf(tick.copy(timestampMs = 3000L), tick,
                tick.copy(timestampMs = 1999L)))
        assertEquals(2001L, result?.filledAtMs)
    }
}
