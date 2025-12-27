package com.trading.market_data.service

import com.trading.common.model.historical.BinanceCandle
import com.trading.common.model.historical.response.BinanceKlinesResponse
import com.trading.common.model.historical.response.BinanceTradeResponse
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

/**
 * NEW: Add this service to Service 1 (Market Data Producer)
 * Handles REST API calls to Binance for historical data
 */
@Service
class HistoricalDataService(
    private val webClientBuilder: WebClient.Builder
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val binanceBaseUrl = "https://api.binance.com"
    private val webClient = webClientBuilder.baseUrl(binanceBaseUrl).build()

    /**
     * Fetch historical klines (candles) from Binance
     */
    suspend fun fetchBinanceKlines(
        symbol: String,
        interval: String,
        startTime: Long,
        endTime: Long,
        limit: Int
    ): BinanceKlinesResponse {
        return try {
            logger.info("Fetching Binance klines: $symbol, interval=$interval")

            val response = webClient.get()
                .uri { builder ->
                    builder.path("/api/v3/klines")
                        .queryParam("symbol", symbol.uppercase())
                        .queryParam("interval", interval)
                        .queryParam("startTime", startTime)
                        .queryParam("endTime", endTime)
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .awaitBody<List<List<Any>>>()

            val candles = response.map { kline ->
                BinanceCandle(
                    openTime = (kline[0] as Number).toLong(),
                    open = (kline[1] as String).toDouble(),
                    high = (kline[2] as String).toDouble(),
                    low = (kline[3] as String).toDouble(),
                    close = (kline[4] as String).toDouble(),
                    volume = (kline[5] as String).toDouble(),
                    closeTime = (kline[6] as Number).toLong(),
                    trades = (kline[8] as Number).toInt()
                )
            }

            logger.info("Fetched ${candles.size} candles for $symbol")
            BinanceKlinesResponse(symbol, interval, candles)

        } catch (e: Exception) {
            logger.error("Error fetching Binance klines for $symbol: ${e.message}", e)
            BinanceKlinesResponse(symbol, interval, emptyList())
        }
    }

    /**
     * Fetch recent trades from Binance
     */
    suspend fun fetchBinanceTrades(
        symbol: String,
        limit: Int
    ): List<BinanceTradeResponse> {
        return try {
            logger.info("Fetching Binance trades: $symbol, limit=$limit")

            val response = webClient.get()
                .uri { builder ->
                    builder.path("/api/v3/trades")
                        .queryParam("symbol", symbol.uppercase())
                        .queryParam("limit", limit)
                        .build()
                }
                .retrieve()
                .awaitBody<List<Map<String, Any>>>()

            response.map { trade ->
                BinanceTradeResponse(
                    id = (trade["id"] as Number).toLong(),
                    price = (trade["price"] as String).toDouble(),
                    qty = (trade["qty"] as String).toDouble(),
                    time = (trade["time"] as Number).toLong(),
                    isBuyerMaker = trade["isBuyerMaker"] as Boolean
                )
            }

        } catch (e: Exception) {
            logger.error("Error fetching Binance trades for $symbol: ${e.message}", e)
            emptyList()
        }
    }
}