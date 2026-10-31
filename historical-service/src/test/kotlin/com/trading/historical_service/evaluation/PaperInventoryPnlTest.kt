package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PaperInventoryPnlTest {
    private fun d(s: String) = BigDecimal(s)
    private fun fill(time: Long, side: String, quantity: String, gross: String, fee: String = "0") =
        InventoryFill(time, "BTCUSDT", side, d(quantity), d(gross), d(fee))

    @Test fun `multiple buys include fees in weighted-average inventory basis`() {
        val actual = PaperInventoryPnl.calculate("BTC", listOf(
            fill(1, "BUY", "1", "100", "1"),
            fill(2, "BUY", "1", "120", "1")
        ), d("130"))
        assertEquals(d("2.00000000"), actual.remainingQuantity)
        assertEquals(d("222.00000000"), actual.costBasisUsdt)
        assertEquals(d("0.00000000"), actual.realizedPnlUsdt)
        assertEquals(d("38.00000000"), actual.unrealizedPnlUsdt)
    }

    @Test fun `partial sale recognizes net realized profit and preserves remaining basis`() {
        val actual = PaperInventoryPnl.calculate("BTC", listOf(
            fill(1, "BUY", "1", "100", "1"),
            fill(2, "BUY", "1", "120", "1"),
            fill(3, "SELL", "0.5", "65", "0.5")
        ), d("130"))
        assertEquals(d("1.50000000"), actual.remainingQuantity)
        assertEquals(d("166.50000000"), actual.costBasisUsdt)
        assertEquals(d("9.00000000"), actual.realizedPnlUsdt)
        assertEquals(d("28.50000000"), actual.unrealizedPnlUsdt)
    }

    @Test fun `complete liquidation leaves no open inventory or unrealized profit`() {
        val actual = PaperInventoryPnl.calculate("BTC", listOf(
            fill(1, "BUY", "1", "100", "1"),
            fill(2, "SELL", "1", "120", "2")
        ), d("130"))
        assertEquals(d("0.00000000"), actual.remainingQuantity)
        assertEquals(d("0.00000000"), actual.costBasisUsdt)
        assertEquals(d("17.00000000"), actual.realizedPnlUsdt)
        assertEquals(d("0.00000000"), actual.unrealizedPnlUsdt)
    }

    @Test fun `overselling and malformed trade histories are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            PaperInventoryPnl.calculate("BTC", listOf(fill(1, "SELL", "1", "100")), d("100"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperInventoryPnl.calculate("BTC", listOf(
                fill(2, "BUY", "1", "100"), fill(1, "SELL", "1", "100")
            ), d("100"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperInventoryPnl.calculate("BTC", listOf(fill(1, "BUY", "1", "100", "-1")), d("100"))
        }
    }
}
