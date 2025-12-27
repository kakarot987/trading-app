package com.trading.historical_service.service

import com.trading.historical_service.repository.CandleRepository
import com.trading.historical_service.repository.CustomCandleRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import kotlinx.coroutines.flow.toList


@Component
class CandleAggregationService(
    private val customCandleRepository: CustomCandleRepository,
    private val candleRepository: CandleRepository,
    private val cacheService: RedisCacheService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun aggregateRecentCandles() {
        val endTime = System.currentTimeMillis()
        val startTime = endTime - (3600000 * 2) // Last 2 hours

        // Get all active symbols (you might want to maintain a list of active symbols)
        val symbols = listOf("BTCUSDT", "BNBUSDT","AAPL", "GOOGL", "MSFT", "TSLA", "NFLX", "USOIL", "XAUUSD","BTCUSD") // Example

        for (symbol in symbols) {
            for (interval in listOf(1, 5, 15, 60)) {
                try {
                    val candles = customCandleRepository
                        .aggregateTradesToCandles(symbol, interval, startTime, endTime).toList()


                    // Store candles
                    candles.forEach { candleEntity ->
                        try {
                            candleRepository.save(candleEntity)
                        } catch (e: Exception) {
                            logger.warn("Error saving candle: ${e.message}")
                        }
                    }

                    logger.debug("Aggregated ${candles.size} candles for $symbol ${interval}m")
                } catch (e: Exception) {
                    logger.error("Error aggregating candles for $symbol ${interval}m: ${e.message}")
                }
            }
        }
    }
}