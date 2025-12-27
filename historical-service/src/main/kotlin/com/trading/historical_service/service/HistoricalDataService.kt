package com.trading.historical_service.service

import com.trading.common.model.historical.Candle
import com.trading.common.model.historical.request.HistoricalQueryRequest
import com.trading.common.model.historical.response.HistoricalQueryResponse
import com.trading.historical_service.entity.TradeEntity
import com.trading.historical_service.entity.toCandle
import com.trading.historical_service.entity.toEntity
import com.trading.historical_service.external.MarketDataClient
import com.trading.historical_service.repository.CandleRepository
import com.trading.historical_service.repository.CustomCandleRepository
import com.trading.historical_service.repository.TradeRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import kotlin.collections.isNotEmpty
import kotlin.collections.take

@Service
class HistoricalDataService(
    private val tradeRepository: TradeRepository,
    private val candleRepository: CandleRepository,
    private val customCandleRepository: CustomCandleRepository,
    private val cacheService: RedisCacheService,
    private val service1Client: MarketDataClient,
    private val databaseClient: DatabaseClient  // ADD THIS for UPSERT
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val RECENT_DATA_THRESHOLD_MS = 3600000L // 1 hour
    }

    /**
     * Main method to fetch historical candles with multi-layer strategy
     */
    suspend fun getHistoricalCandles(request: HistoricalQueryRequest): HistoricalQueryResponse {
        val startTime = System.currentTimeMillis()
        var fromCache = false
        var fromExternalApi = false

        logger.info("Processing historical query: symbol=${request.symbol}, interval=${request.interval.value}, " +
                "range=${request.startTime}-${request.endTime}")

        // Step 1: Try cache for recent data
        val currentTime = System.currentTimeMillis()
        if (currentTime - request.endTime <= RECENT_DATA_THRESHOLD_MS) {
            val cachedCandles = cacheService.getCandlesInRange(
                request.symbol,
                request.interval,
                request.startTime,
                request.endTime
            )

            if (cachedCandles.isNotEmpty() &&
                cachedCandles.size >= calculateExpectedCandles(request)) {
                logger.info("Returning ${cachedCandles.size} candles from cache")
                fromCache = true

                return HistoricalQueryResponse(
                    symbol = request.symbol,
                    interval = request.interval.value,
                    candles = cachedCandles.take(request.limit),
                    count = cachedCandles.size,
                    fromCache = true,
                    fromExternalApi = false,
                    queryTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }

        // Step 2: Query stored candles from PostgreSQL
        val storedCandles = candleRepository
            .findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(
                request.symbol,
                request.interval.value,
                request.startTime,
                request.endTime
            )
            .toList()
            .map { it.toCandle(request.interval) }

        if (storedCandles.isNotEmpty() &&
            storedCandles.size >= calculateExpectedCandles(request)) {
            logger.info("Returning ${storedCandles.size} candles from PostgreSQL")

            // Cache for future queries
            cacheService.cacheCandles(storedCandles)

            return HistoricalQueryResponse(
                symbol = request.symbol,
                interval = request.interval.value,
                candles = storedCandles.take(request.limit),
                count = storedCandles.size,
                fromCache = false,
                fromExternalApi = false,
                queryTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 3: Aggregate from raw trades if no candles exist
        val aggregatedCandles = customCandleRepository
            .aggregateTradesToCandles(
                request.symbol,
                request.interval.minutes,
                request.startTime,
                request.endTime
            )
            .toList()
            .distinctBy { it.openTime }
            .map { it.toCandle(request.interval) }

        if (aggregatedCandles.isNotEmpty()) {
            logger.info("Aggregated ${aggregatedCandles.size} candles from trades")

            // Store aggregated candles with UPSERT
            storeCandlesWithUpsert(aggregatedCandles)
            cacheService.cacheCandles(aggregatedCandles)

            return HistoricalQueryResponse(
                symbol = request.symbol,
                interval = request.interval.value,
                candles = aggregatedCandles.take(request.limit),
                count = aggregatedCandles.size,
                fromCache = false,
                fromExternalApi = false,
                queryTimeMs = System.currentTimeMillis() - startTime
            )
        }

        // Step 4: Fetch from Service 1 as last resort
        logger.info("No local data found, fetching from Service 1")
        val externalCandles = service1Client.fetchHistoricalCandles(
            request.symbol,
            request.interval,
            request.startTime,
            request.endTime,
            request.limit
        )

        if (externalCandles.isNotEmpty()) {
            logger.info("Fetched ${externalCandles.size} candles from Service 1")
            fromExternalApi = true

            // Store with UPSERT
            storeCandlesWithUpsert(externalCandles)
            cacheService.cacheCandles(externalCandles)
        }

        return HistoricalQueryResponse(
            symbol = request.symbol,
            interval = request.interval.value,
            candles = externalCandles,
            count = externalCandles.size,
            fromCache = false,
            fromExternalApi = true,
            queryTimeMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Get recent trades for a symbol
     */
    suspend fun getRecentTrades(
        symbol: String,
        fromTimestamp: Long,
        limit: Int = 100
    ): List<TradeEntity> {
        return tradeRepository
            .findBySymbolAndTradeTimestampGreaterThanEqualOrderByTradeTimestampDesc(symbol, fromTimestamp)
            .toList()
            .take(limit)
    }

    /**
     * Get trades in a specific time range
     */
    suspend fun getTradesInRange(
        symbol: String,
        startTime: Long,
        endTime: Long
    ): List<TradeEntity> {
        return tradeRepository
            .findBySymbolAndTradeTimestampBetweenOrderByTradeTimestampAsc(symbol, startTime, endTime)
            .toList()
    }

    /**
     * Store candles with UPSERT (ON CONFLICT DO UPDATE)
     * This prevents duplicate key errors
     */
    private suspend fun storeCandlesWithUpsert(candles: List<Candle>) {
        candles.forEach { candle ->
            try {
                val sql = """
                    INSERT INTO candles (
                        symbol, interval_trade, open_time, close_time, 
                        open, high, low, close, volume, trades, 
                        buy_volume, notional_value, created_at
                    ) VALUES (
                        $1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, CURRENT_TIMESTAMP
                    )
                    ON CONFLICT (symbol, interval_trade, open_time) 
                    DO UPDATE SET
                        close_time = EXCLUDED.close_time,
                        open = EXCLUDED.open,
                        high = GREATEST(candles.high, EXCLUDED.high),
                        low = LEAST(candles.low, EXCLUDED.low),
                        close = EXCLUDED.close,
                        volume = EXCLUDED.volume,
                        trades = EXCLUDED.trades,
                        buy_volume = EXCLUDED.buy_volume,
                        notional_value = EXCLUDED.notional_value
                """.trimIndent()

                databaseClient.sql(sql)
                    .bind(0, candle.symbol)
                    .bind(1, candle.interval.value)
                    .bind(2, candle.openTime)
                    .bind(3, candle.closeTime)
                    .bind(4, candle.open)
                    .bind(5, candle.high)
                    .bind(6, candle.low)
                    .bind(7, candle.close)
                    .bind(8, candle.volume)
                    .bind(9, candle.trades)
                    .bind(10, candle.buyVolume)
                    .bind(11, candle.notionalValue)
                    .fetch()
                    .rowsUpdated()
                    .subscribe()

            } catch (e: Exception) {
                logger.debug("Candle already exists or error: ${candle.symbol} ${candle.openTime} - ${e.message}")
                // Don't log as error - this is expected behavior
            }
        }
    }

    /**
     * Calculate expected number of candles for validation
     */
    private fun calculateExpectedCandles(request: HistoricalQueryRequest): Int {
        val timeRangeMs = request.endTime - request.startTime
        val intervalMs = request.interval.minutes * 60000L
        return (timeRangeMs / intervalMs).toInt()
    }
}