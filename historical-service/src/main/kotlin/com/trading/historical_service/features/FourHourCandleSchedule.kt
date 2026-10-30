package com.trading.historical_service.features

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Recompute a rolling 4h history to incorporate late-arriving trades. */
@Component
class FourHourCandleSchedule(private val writer: FourHourCandleWriter) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
    fun refreshClosedCandles() {
        val observedAt = System.currentTimeMillis()
        for (symbol in CandleSeries.spotSymbols.sorted()) {
            try {
                val rows = writer.refresh(symbol, observedAt)
                log.info("4h candle refresh symbol={} affectedRows={}", symbol, rows)
            } catch (exception: Exception) {
                log.error("4h candle refresh failed for {}", symbol, exception)
            }
        }
    }
}
