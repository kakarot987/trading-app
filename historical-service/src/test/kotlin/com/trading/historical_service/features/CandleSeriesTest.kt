package com.trading.historical_service.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CandleSeriesTest {
    private val interval = CandleSeries.FOUR_HOURS_MS
    private fun trade(id: String, timestamp: Long, price: Double, volume: Double = 1.0) =
        CandleSeries.Trade(id, timestamp, price, volume)

    @Test
    fun `UTC boundaries use start inclusive end exclusive and exclude unfinished bar`() {
        val bars = CandleSeries.aggregateClosed(
            listOf(
                trade("a", 0, 100.0),
                trade("b", interval - 1, 105.0),
                trade("c", interval, 200.0),
                trade("d", 2 * interval - 1, 205.0)
            ),
            observedAtMs = interval + 1
        )
        assertEquals(1, bars.size)
        assertEquals(0L, bars[0].openTimeMs)
        assertEquals(interval, bars[0].closeTimeMs)
        assertEquals(100.0, bars[0].open)
        assertEquals(105.0, bars[0].close)
    }

    @Test
    fun `late arrivals update high low and volume deterministically without double counting replay`() {
        val trades = listOf(
            trade("b", 10, 105.0, 2.0),
            trade("a", 10, 100.0, 1.0),
            trade("c", 20, 90.0, 3.0),
            trade("c", 20, 90.0, 3.0)
        )
        val bar = CandleSeries.aggregateClosed(trades, observedAtMs = interval).single()
        assertEquals(100.0, bar.open)
        assertEquals(105.0, bar.high)
        assertEquals(90.0, bar.low)
        assertEquals(90.0, bar.close)
        assertEquals(6.0, bar.volume)
        assertEquals(3, bar.tradeCount)
    }

    @Test
    fun `missing bars remain explicit gaps rather than zero volume invented bars`() {
        val bars = CandleSeries.aggregateClosed(
            listOf(trade("a", 5, 100.0), trade("b", 3 * interval, 110.0)),
            observedAtMs = 4 * interval
        )
        assertEquals(2, bars.size)
        assertEquals(listOf(interval, 2 * interval), CandleSeries.missingOpenTimes(bars))
    }

    @Test
    fun `rejects invalid trade and interval inputs`() {
        assertThrows(IllegalArgumentException::class.java) {
            CandleSeries.aggregateClosed(listOf(trade("a", 1, Double.NaN)), observedAtMs = interval)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CandleSeries.aggregateClosed(listOf(trade("a", 1, 100.0)), intervalMs = 0, observedAtMs = interval)
        }
    }
}
