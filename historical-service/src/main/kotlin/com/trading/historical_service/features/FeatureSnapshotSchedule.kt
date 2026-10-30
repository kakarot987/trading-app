package com.trading.historical_service.features

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Capture one observed snapshot per completed 4h interval. Historical gaps
 * or insufficient data produce no snapshot and are logged explicitly.
 */
@Component
class FeatureSnapshotSchedule(
    private val reader: FeatureSnapshotService,
    private val writer: FeatureSnapshotWriter,
    @Value("\${M3_CAPTURE_ON_STARTUP:false}") private val captureOnStartup: Boolean
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        // Treat late/replayed candles as archival inputs, not timely observations.
        const val MAX_CAPTURE_LAG_MS = 30L * 60 * 1000
        fun isFresh(snapshot: FeatureSnapshot, observedAt: Long): Boolean =
            snapshot.candleCloseTimeMs <= observedAt &&
                observedAt - snapshot.candleCloseTimeMs <= MAX_CAPTURE_LAG_MS
    }

    // Run after candle refresh has had time to process the latest closed bar.
    @Scheduled(cron = "0 10 0,4,8,12,16,20 * * *", zone = "UTC")
    fun capture() = captureAt(System.currentTimeMillis())

    @EventListener(ApplicationReadyEvent::class)
    fun captureOnReady() {
        if (captureOnStartup) captureAt(System.currentTimeMillis())
    }

    fun captureAt(observedAt: Long) {
        for (symbol in CandleSeries.spotSymbols.sorted()) {
            try {
                val snapshot = reader.computeLatest(symbol, observedAt)
                if (snapshot == null) {
                    logger.info("No eligible contiguous 4h features for {}", symbol)
                } else if (!isFresh(snapshot, observedAt)) {
                    logger.warn("Skipping stale feature observation symbol={} close={} observed={} lagMs={}",
                        symbol, snapshot.candleCloseTimeMs, observedAt, observedAt - snapshot.candleCloseTimeMs)
                } else {
                    val count = writer.save(snapshot)
                    logger.info("4h feature snapshot symbol={} version={} candleClose={} inserted={}",
                        symbol, snapshot.featureVersion, snapshot.candleCloseTimeMs, count)
                }
            } catch (e: IllegalArgumentException) {
                logger.warn("Skipped 4h features for {} due to invalid or missing candles: {}",
                    symbol, e.message)
            } catch (e: Exception) {
                logger.error("Could not persist 4h feature snapshot for {}", symbol, e)
            }
        }
    }
}
