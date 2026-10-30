package com.trading.historical_service.signals

import com.trading.historical_service.features.CandleSeries
import com.trading.historical_service.features.FeatureSnapshot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WalkForwardDatasetTest {
    private val h = CandleSeries.FOUR_HOURS_MS
    private fun candle(i: Int, close: Double) =
        CandleSeries.Bar(i * h, (i + 1) * h, close, close, close, close, 10.0, 1)
    private fun feature(i: Int, observed: Long = (i + 1) * h) =
        FeatureSnapshot("BTCUSDT", "4h", 4, "technical-4h-v1",
            (i + 1) * h, observed, 100.0, 100.0, 50.0,
            0.0, 0.0, 0.0, 0.01, 1.0)

    @Test
    fun `next bar label is known only at future candle close`() {
        val rows = WalkForwardDataset.build(
            listOf(feature(0), feature(1)),
            listOf(candle(0, 100.0), candle(1, 110.0), candle(2, 99.0))
        )
        assertEquals(2, rows.size)
        assertEquals(0.10, rows[0].nextReturn, 1e-10)
        assertTrue(rows[0].up)
        assertEquals(2 * h, rows[0].labelAvailableAtMs)
        assertFalse(rows[1].up)
        assertTrue(rows.all { it.labelAvailableAtMs > it.predictionTimeMs })
    }

    @Test
    fun `late backfills cannot masquerade as timely predictions`() {
        val rows = WalkForwardDataset.build(
            listOf(feature(0, observed = 2 * h), feature(1)),
            listOf(candle(0, 100.0), candle(1, 110.0), candle(2, 120.0))
        )
        assertEquals(1, rows.size)
        assertEquals(2 * h, rows.single().predictionTimeMs)
    }

    @Test
    fun `missing next candle leaves example unlabeled`() {
        assertTrue(WalkForwardDataset.build(
            listOf(feature(0)), listOf(candle(0, 100.0))
        ).isEmpty())
    }

    @Test
    fun `purged split prevents training labels overlapping holdout`() {
        val rows = WalkForwardDataset.build(
            (0..4).map(::feature),
            (0..5).map { candle(it, 100.0 + it) }
        )
        val split = WalkForwardDataset.splitChronologically(rows, 3 * h)
        assertEquals(2, split.train.size)
        assertEquals(3, split.test.size)
        assertTrue(split.train.all { it.labelAvailableAtMs <= 3 * h })
        assertTrue(split.test.all { it.predictionTimeMs >= 3 * h })
    }
}
