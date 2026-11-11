package com.trading.historical_service.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureSnapshotTest {
    private val interval = CandleSeries.FOUR_HOURS_MS
    private fun bars(count: Int) = (0 until count).map { i ->
        val start = i * interval
        CandleSeries.Bar(start, start + interval, 100.0 + i, 100.0 + i,
            100.0 + i, 100.0 + i, 10.0, 1)
    }

    @Test
    fun `feature snapshot is versioned and requires complete closed history`() {
        val candles = bars(40)
        val snapshot = FeatureSnapshots.latest("BTCUSDT", candles, 40 * interval)
        assertNotNull(snapshot)
        assertEquals("technical-4h-v1", snapshot!!.featureVersion)
        assertEquals(4, snapshot.horizonHours)
        assertEquals("4h", snapshot.interval)
        assertEquals(40 * interval, snapshot.candleCloseTimeMs)
        assertEquals(40 * interval, snapshot.availableAtMs)
        assertTrue(snapshot.rsi14 >= 0 && snapshot.rsi14 <= 100)
    }

    @Test
    fun `future candle is excluded by observation cutoff`() {
        val candles = bars(40)
        val snapshot = FeatureSnapshots.latest("ETHUSDT", candles, 39 * interval)
        assertEquals(39 * interval, snapshot!!.candleCloseTimeMs)
        assertEquals(39 * interval, snapshot.availableAtMs)
    }

    @Test
    fun `missing candles and unsupported symbols are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSnapshots.latest("BTCUSDT", bars(40).filterIndexed { index, _ -> index != 20 }, 40 * interval)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSnapshots.latest("AAPL", bars(40), 40 * interval)
        }
    }

    @Test
    fun `historical as-of snapshot ignores a future gap and future outlier`() {
        val historical = bars(40)
        val cutoff = 40 * interval
        val asOf = FeatureSnapshots.latest("BTCUSDT", historical, cutoff)
        assertNotNull(asOf)

        val future = CandleSeries.Bar(
            45 * interval, 46 * interval,
            999999.0, 999999.0, 999999.0, 999999.0, 999.0, 1
        )
        val withFuture = FeatureSnapshots.latest(
            "BTCUSDT", historical + future, cutoff
        )
        assertEquals(asOf, withFuture, "Future bars must not affect an as-of snapshot")
    }

    @Test
    fun `historical as-of snapshot rejects an earlier gap even when later bars exist`() {
        val historyWithGap = bars(40).filterIndexed { i, _ -> i != 25 }
        val future = CandleSeries.Bar(
            42 * interval, 43 * interval,
            100.0, 100.0, 100.0, 100.0, 10.0, 1
        )
        assertThrows(IllegalArgumentException::class.java) {
            FeatureSnapshots.latest("ETHUSDT", historyWithGap + future, 40 * interval)
        }
    }

    @Test
    fun `insufficient history cannot fabricate indicators`() {
        assertNull(FeatureSnapshots.latest("BTCUSDT", bars(20), 20 * interval))
    }
}
