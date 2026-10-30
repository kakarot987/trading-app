package com.trading.historical_service.paper

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PaperMoneyTest {
    @Test fun `decimal arithmetic preserves exact fractions`() {
        val opening = PaperDecimal.exact("100.00000000")
        val remaining = PaperDecimal.debit(opening, PaperDecimal.positive("0.10000001"))
        assertEquals(PaperDecimal.exact("99.89999999"), remaining)
        assertEquals(opening, PaperDecimal.credit(remaining, PaperDecimal.positive("0.10000001")))
    }

    @Test fun `reject overdraft negative values and excessive precision`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperDecimal.debit(PaperDecimal.exact("1"), PaperDecimal.positive("1.00000001"))
        }
        assertThrows(IllegalArgumentException::class.java) { PaperDecimal.exact("-0.01") }
        assertThrows(IllegalArgumentException::class.java) { PaperDecimal.positive("0") }
        assertThrows(IllegalArgumentException::class.java) { PaperDecimal.exact("0.000000001") }
    }

    @Test fun `spot contracts accept only supported symbols and scaled positive quantities`() {
        val order = PaperOrderRequest("fixture-1", "BTCUSDT",
            PaperOrderSide.BUY, PaperDecimal.positive("0.00010000"))
        assertEquals("BTCUSDT", order.symbol)
        assertThrows(IllegalArgumentException::class.java) {
            order.copy(symbol = "DOGEUSDT")
        }
        assertThrows(IllegalArgumentException::class.java) {
            order.copy(quantity = PaperDecimal.ZERO)
        }
    }
}
