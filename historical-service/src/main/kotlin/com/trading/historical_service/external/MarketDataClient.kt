package com.trading.historical_service.external

import com.trading.common.model.historical.Candle
import com.trading.common.model.historical.CandleInterval
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * Service 1 Client - Calls Service 1 for historical data
 * REPLACES: BinanceDataProvider, AlphaVantageDataProvider, ExternalDataFacade
 *
 * FILES TO DELETE:
 * - BinanceDataProvider.kt
 * - AlphaVantageDataProvider.kt
 * - ExternalDataFacade.kt
 */
@Service
class MarketDataClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${services.market-data-producer.url:http://market-data-producer:8080}")
    private val service1Url: String
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val webClient = webClientBuilder.baseUrl(service1Url).build()

    /**
     * Fetch historical candles from Service 1 (which calls Binance)
     */
    suspend fun fetchHistoricalCandles(
        symbol: String,
        interval: CandleInterval,
        startTime: Long,
        endTime: Long,
        limit: Int = 500
    ): List<Candle> {
        return try {
            logger.info("Fetching historical candles from Service 1: $symbol, interval=${interval.value}")

            val response = webClient.get()
                .uri { builder ->
                    builder.path("/api/v1/market-data/historical/candles")
                        .queryParam("symbol", symbol)
                        .queryParam("interval", interval.value)
                        .queryParam("startTime", startTime)
                        .queryParam("endTime", endTime)
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .awaitBody<Service1CandlesResponse>()

            response.candles.map { it.toCandle(symbol, interval) }

        } catch (e: Exception) {
            logger.error("Error fetching from Service 1: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Fetch recent trades from Service 1
     */
    suspend fun fetchRecentTrades(
        symbol: String,
        limit: Int = 1000
    ): List<Service1Trade> {
        return try {
            logger.info("Fetching recent trades from Service 1: $symbol, limit=$limit")

            webClient.get()
                .uri { builder ->
                    builder.path("/api/v1/market-data/historical/trades")
                        .queryParam("symbol", symbol)
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .awaitBody()

        } catch (e: Exception) {
            logger.error("Error fetching trades from Service 1: ${e.message}", e)
            emptyList()
        }
    }

    /**
     * Check if Service 1 is healthy
     */
    suspend fun isHealthy(): Boolean {
        return try {
            val response = webClient.get()
                .uri("/api/v1/market-data/health")
                .retrieve()
                .awaitBody<Map<String, String>>()

            response["status"] == "UP"
        } catch (e: Exception) {
            logger.warn("Service 1 health check failed: ${e.message}")
            false
        }
    }
}

// Response models from Service 1
data class Service1CandlesResponse(
    val symbol: String,
    val interval: String,
    val candles: List<Service1Candle>
)

data class Service1Candle(
    val openTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val closeTime: Long,
    val trades: Int
) {
    fun toCandle(symbol: String, interval: CandleInterval): Candle {
        return Candle(
            symbol = symbol,
            interval = interval,
            openTime = openTime,
            closeTime = closeTime,
            open = open,
            high = high,
            low = low,
            close = close,
            volume = volume,
            trades = trades,
            buyVolume = 0.0, // Service 1 doesn't provide this, will be aggregated
            notionalValue = close * volume
        )
    }
}

data class Service1Trade(
    val id: Long,
    val price: Double,
    val qty: Double,
    val time: Long,
    val isBuyerMaker: Boolean
)