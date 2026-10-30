package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperLedgerPostingTest {
    private val order = PaperOrderRequest("paper-ledger-fixture", "BTCUSDT",
        PaperOrderSide.BUY, PaperDecimal.positive("0.10000000"))
    private val tick = PaperMarketTick("BTCUSDT", 2001L, BigDecimal("100.00000000"))

    @Test fun `buy debits quote including fee and credits base exactly`() {
        val fill = PaperFillEngine.fill(order, 2000L, tick)
        val result = PaperLedgerPosting.apply(
            mapOf("USDT" to PaperDecimal.exact("100.00000000")),
            PaperLedgerPosting.movements(fill)
        )
        assertEquals(PaperDecimal.exact("89.98599600"), result["USDT"])
        assertEquals(PaperDecimal.exact("0.10000000"), result["BTC"])
    }

    @Test fun `sell debits base and credits quote after fees`() {
        val fill = PaperFillEngine.fill(order.copy(side = PaperOrderSide.SELL), 2000L, tick)
        val result = PaperLedgerPosting.apply(
            mapOf("BTC" to PaperDecimal.exact("0.10000000")),
            PaperLedgerPosting.movements(fill)
        )
        assertEquals(PaperDecimal.ZERO, result["BTC"])
        assertEquals(PaperDecimal.exact("9.98600400"), result["USDT"])
    }

    @Test fun `insufficient paper balance is rejected`() {
        val fill = PaperFillEngine.fill(order, 2000L, tick)
        assertThrows(IllegalArgumentException::class.java) {
            PaperLedgerPosting.apply(emptyMap(), PaperLedgerPosting.movements(fill))
        }
    }
}
