package com.trading.market_data.controller

import com.trading.common.model.historical.response.BinanceKlinesResponse
import com.trading.common.model.historical.response.BinanceTradeResponse
import com.trading.market_data.service.HistoricalDataService
import org.springframework.web.bind.annotation.*

/**
 * NEW: Add this controller to Service 1 (Market Data Producer)
 * Provides historical data endpoints for Service 4 to call
 */
@RestController
@RequestMapping("/api/v1/market-data")
class HistoricalDataController(
    private val historicalDataService: HistoricalDataService
) {

    /**
     * Fetch historical candles from Binance
     * Called by Service 4 when it needs to backfill missing data
     */
    @GetMapping("/historical/candles")
    suspend fun getHistoricalCandles(
        @RequestParam symbol: String,
        @RequestParam interval: String,
        @RequestParam startTime: Long,
        @RequestParam endTime: Long,
        @RequestParam(defaultValue = "500") limit: Int
    ): BinanceKlinesResponse {
        return historicalDataService.fetchBinanceKlines(
            symbol = symbol,
            interval = interval,
            startTime = startTime,
            endTime = endTime,
            limit = limit
        )
    }

    /**
     * Fetch recent trades from Binance
     * Called by Service 4 for recent trade history
     */
    @GetMapping("/historical/trades")
    suspend fun getHistoricalTrades(
        @RequestParam symbol: String,
        @RequestParam(defaultValue = "1000") limit: Int
    ): List<BinanceTradeResponse> {
        return historicalDataService.fetchBinanceTrades(symbol, limit)
    }

    /**
     * Health check for Service 4 to verify connection
     */
    @GetMapping("/health")
    fun health(): Map<String, String> {
        return mapOf(
            "status" to "UP",
            "service" to "market-data-producer",
            "timestamp" to System.currentTimeMillis().toString()
        )
    }
}