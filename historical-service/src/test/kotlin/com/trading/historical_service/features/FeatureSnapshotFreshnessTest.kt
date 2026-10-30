package com.trading.historical_service.features

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class FeatureSnapshotFreshnessTest {
    private fun snapshot(close: Long) = FeatureSnapshot(
        "BTCUSDT", "4h", 4, "technical-4h-v1", close, close + 60_000,
        101.0, 100.0, 55.0, 1.0, 0.5, 0.5, 0.02, 1.0
    )
    @Test fun `fresh snapshots are accepted near close`() {
        val close = 1_793_347_200_000L
        assertTrue(FeatureSnapshotSchedule.isFresh(snapshot(close), close + 10 * 60_000))
        assertTrue(FeatureSnapshotSchedule.isFresh(snapshot(close), close + 30 * 60_000))
    }
    @Test fun `historical and future closes are never accepted as current observations`() {
        val close = 1_793_347_200_000L
        assertFalse(FeatureSnapshotSchedule.isFresh(snapshot(close), close + 31 * 60_000))
        assertFalse(FeatureSnapshotSchedule.isFresh(snapshot(close), close - 1))
    }
}
