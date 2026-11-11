package com.trading.historical_service.evaluation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class PaperRealizedInventoryTest {
    private fun d(value: String) = BigDecimal(value)
    private fun fill(index: Int, time: Long, side: String, quantity: String,
                     gross: String, fee: String = "0") =
        PersistedInventoryFill(UUID(0, index.toLong()), time, "BTCUSDT",
            side, d(quantity), d(gross), d(fee))

    @Test fun `weighted average fees and realized PnL match actual fill movements`() {
        val result = PaperRealizedInventory.summarize(listOf(
            fill(1, 1, "BUY", "1", "100", "1"),
            fill(2, 2, "BUY", "1", "120", "1"),
            fill(3, 3, "SELL", "0.5", "65", "0.5")
        )).single()
        assertEquals("BTC", result.asset)
        assertEquals(3, result.fillCount)
        assertEquals(d("1.50000000"), result.remainingQuantity)
        assertEquals(d("166.50000000"), result.remainingCostBasisUsdt)
        assertEquals(d("9.00000000"), result.realizedPnlUsdt)
        assertEquals(d("-157.50000000"), result.netQuoteMovementUsdt)
    }

    @Test fun `roundtrip shows realized PnL but never invents unrealized returns`() {
        val result = PaperRealizedInventory.summarize(listOf(
            fill(1, 1000, "BUY", "0.1", "10.004", "0.010004"),
            fill(2, 2000, "SELL", "0.1", "11", "0.011")
        )).single()
        assertEquals(d("0E-8").setScale(8), result.remainingQuantity)
        assertEquals(d("0E-8").setScale(8), result.remainingCostBasisUsdt)
        assertEquals(d("0.97499600"), result.realizedPnlUsdt)
        assertEquals(result.realizedPnlUsdt, result.netQuoteMovementUsdt)
    }

    @Test fun `duplicate unordered and ambiguous histories fail closed`() {
        val buy = fill(1, 100, "BUY", "1", "100")
        val sell = fill(2, 200, "SELL", "1", "110")
        assertThrows(IllegalArgumentException::class.java) {
            PaperRealizedInventory.summarize(listOf(buy, buy))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperRealizedInventory.summarize(listOf(sell, buy))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperRealizedInventory.summarize(listOf(buy, sell.copy(tickTimeMs = 100)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperRealizedInventory.summarize(listOf(sell))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PaperRealizedInventory.summarize(listOf(buy.copy(feeQuoteUsdt = d("-1"))))
        }
    }
}
