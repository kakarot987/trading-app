package com.trading.historical_service.service

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledTasks(
    private val aggregationService: CandleAggregationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Aggregate recent trades into candles every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5 minutes
    suspend fun aggregateRecentCandles() {
        try {
            logger.info("Starting scheduled candle aggregation")
            aggregationService.aggregateRecentCandles()
            logger.info("Completed scheduled candle aggregation")
        } catch (e: Exception) {
            logger.error("Error during scheduled aggregation: ${e.message}", e)
        }
    }

    /**
     * Clean up old trade data daily (optional, adjust retention policy)
     */
    @Scheduled(cron = "0 0 2 * * *") // 2 AM daily
    suspend fun cleanupOldData() {
        try {
            logger.info("Starting scheduled data cleanup")
            // aggregationService.cleanupOldTrades(daysToKeep = 90)
            logger.info("Completed scheduled data cleanup")
        } catch (e: Exception) {
            logger.error("Error during scheduled cleanup: ${e.message}", e)
        }
    }
}
