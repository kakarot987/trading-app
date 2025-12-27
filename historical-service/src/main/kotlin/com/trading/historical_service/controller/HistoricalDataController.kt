package com.trading.historical_service.controller

import com.trading.common.model.historical.CandleInterval
import com.trading.common.model.historical.request.BatchCandleRequest
import com.trading.common.model.historical.request.HistoricalQueryRequest
import com.trading.common.model.historical.response.HealthResponse
import com.trading.common.model.historical.response.HistoricalQueryResponse
import com.trading.historical_service.entity.TradeEntity
import com.trading.historical_service.service.HistoricalDataService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

@RestController
@RequestMapping("/api/v1/historical")
class HistoricalDataController(
    private val historicalDataService: HistoricalDataService
) {

    /**
     * Get historical candles
     * GET /api/v1/historical/candles?symbol=BTCUSDT&interval=15m&startTime=1234567890&endTime=1234567999&limit=500
     */
    @GetMapping("/candles")
    suspend fun getCandles(
        @RequestParam symbol: String,
        @RequestParam(defaultValue = "15m") interval: String,
        @RequestParam startTime: Long,
        @RequestParam endTime: Long,
        @RequestParam(defaultValue = "500") limit: Int
    ): ResponseEntity<HistoricalQueryResponse> {

        validateTimeRange(startTime, endTime)
        validateLimit(limit)

        val candleInterval = try {
            CandleInterval.fromString(interval)
        } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Invalid interval. Supported values: 1m, 5m, 15m, 1h, 1d"
            )
        }

        val request = HistoricalQueryRequest(
            symbol = symbol.uppercase(),
            interval = candleInterval,
            startTime = startTime,
            endTime = endTime,
            limit = limit
        )

        val response = historicalDataService.getHistoricalCandles(request)
        return ResponseEntity.ok(response)
    }

    /**
     * Get historical candles with date-time parameters
     * GET /api/v1/historical/candles-by-date?symbol=BTCUSDT&interval=1h&start=2024-01-01T00:00:00Z&end=2024-01-02T00:00:00Z
     */
    @GetMapping("/candles-by-date")
    suspend fun getCandlesByDate(
        @RequestParam symbol: String,
        @RequestParam(defaultValue = "15m") interval: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) start: LocalDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) end: LocalDateTime,
        @RequestParam(defaultValue = "500") limit: Int
    ): ResponseEntity<HistoricalQueryResponse> {

        val startTime = start.toInstant(ZoneOffset.UTC).toEpochMilli()
        val endTime = end.toInstant(ZoneOffset.UTC).toEpochMilli()

        return getCandles(symbol, interval, startTime, endTime, limit)
    }

    /**
     * Get recent trades
     * GET /api/v1/historical/trades/recent?symbol=BTCUSDT&from=1234567890&limit=100
     */
    @GetMapping("/trades/recent")
    suspend fun getRecentTrades(
        @RequestParam symbol: String,
        @RequestParam(required = false) from: Long?,
        @RequestParam(defaultValue = "100") limit: Int
    ): ResponseEntity<List<TradeEntity>> {

        validateLimit(limit, maxLimit = 1000)

        val fromTimestamp = from ?: (System.currentTimeMillis() - 3600000) // Default: last hour
        val trades = historicalDataService.getRecentTrades(
            symbol.uppercase(),
            fromTimestamp,
            limit
        )

        return ResponseEntity.ok(trades)
    }

    /**
     * Get trades in a time range
     * GET /api/v1/historical/trades?symbol=BTCUSDT&startTime=1234567890&endTime=1234567999
     */
    @GetMapping("/trades")
    suspend fun getTrades(
        @RequestParam symbol: String,
        @RequestParam startTime: Long,
        @RequestParam endTime: Long
    ): ResponseEntity<List<TradeEntity>> {

        validateTimeRange(startTime, endTime)

        val trades = historicalDataService.getTradesInRange(
            symbol.uppercase(),
            startTime,
            endTime
        )

        return ResponseEntity.ok(trades)
    }

    /**
     * Get multiple symbols' candles in parallel
     * POST /api/v1/historical/candles/batch
     * Body: { "symbols": ["BTCUSDT", "ETHUSDT"], "interval": "15m", "startTime": 123, "endTime": 456 }
     */
    @PostMapping("/candles/batch")
    suspend fun getBatchCandles(
        @RequestBody request: BatchCandleRequest
    ): ResponseEntity<Map<String, HistoricalQueryResponse>> {

        if (request.symbols.isEmpty() || request.symbols.size > 20) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Symbols count must be between 1 and 20"
            )
        }

        validateTimeRange(request.startTime, request.endTime)

        val interval = CandleInterval.fromString(request.interval)

        val results = request.symbols.associateWith { symbol ->
            historicalDataService.getHistoricalCandles(
                HistoricalQueryRequest(
                    symbol = symbol.uppercase(),
                    interval = interval,
                    startTime = request.startTime,
                    endTime = request.endTime,
                    limit = request.limit ?: 500
                )
            )
        }

        return ResponseEntity.ok(results)
    }

    /**
     * Health check endpoint
     * GET /api/v1/historical/health
     */
    @GetMapping("/health")
    suspend fun health(): ResponseEntity<HealthResponse> {
        return ResponseEntity.ok(
            HealthResponse(
                status = "UP",
                timestamp = System.currentTimeMillis(),
                service = "historical-data-service"
            )
        )
    }

    private fun validateTimeRange(startTime: Long, endTime: Long) {
        if (startTime >= endTime) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "startTime must be less than endTime"
            )
        }

        val maxRange = 365L * 24 * 3600 * 1000 // 1 year
        if (endTime - startTime > maxRange) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Time range cannot exceed 1 year"
            )
        }
    }

    private fun validateLimit(limit: Int, maxLimit: Int = 1000) {
        if (limit <= 0 || limit > maxLimit) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Limit must be between 1 and $maxLimit"
            )
        }
    }
}




