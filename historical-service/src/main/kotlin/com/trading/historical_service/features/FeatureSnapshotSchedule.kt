package com.trading.historical_service.features

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Capture one observed snapshot per completed 4h interval. Historical gaps
 * or insufficient data produce no snapshot and are logged explicitly.
 */
@Component
class FeatureSnapshotSchedule(
    private val reader: FeatureSnapshotService,
    private val writer: FeatureSnapshotWriter
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val interval = CandleSeries.FOUR_HOURS_MS

    // Run after candle refresh has had time to process the latest closed bar.
    @Scheduled(cron = "0 10 0,4,8,12,16,20 * * *", zone = "UTC")
    fun capture() {
        val observedAt = System.currentTimeMillis()
        for (symbol in CandleSeries.spotSymbols.sorted()) {
            try {
                val snapshot = reader.computeLatest(symbol, observedAt)
                if (snapshot == null) {
                    logger.info("No eligible contiguous 4h features for {}", symbol)
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
