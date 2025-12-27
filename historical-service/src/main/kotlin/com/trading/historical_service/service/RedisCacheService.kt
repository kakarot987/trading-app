package com.trading.historical_service.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.model.historical.Candle
import com.trading.common.model.historical.CandleInterval
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import org.springframework.beans.factory.annotation.Qualifier

@Service
class RedisCacheService(
    @Qualifier("reactiveRedisTemplate") private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val CANDLE_KEY_PREFIX = "candles"
        private const val RECENT_TRADES_KEY_PREFIX = "recent_trades"

        // Cache recent data for 15 minutes
        private val CANDLE_TTL = Duration.ofMinutes(15)
        private val RECENT_TRADES_TTL = Duration.ofMinutes(5)
    }

    // Generate cache key for candles
    private fun candleKey(symbol: String, interval: CandleInterval, openTime: Long): String {
        return "$CANDLE_KEY_PREFIX:$symbol:${interval.value}:$openTime"
    }

    // Generate cache key pattern for range queries
    private fun candleKeyPattern(symbol: String, interval: CandleInterval): String {
        return "$CANDLE_KEY_PREFIX:$symbol:${interval.value}:*"
    }

    // Cache a single candle
    suspend fun cacheCandle(candle: Candle): Boolean {
        return try {
            val key = candleKey(candle.symbol, candle.interval, candle.openTime)
            val value = objectMapper.writeValueAsString(candle)

            redisTemplate.opsForValue()
                .set(key, value, CANDLE_TTL)
                .awaitSingleOrNull() ?: false
        } catch (e: Exception) {
            logger.error("Error caching candle: ${e.message}", e)
            false
        }
    }

    // Cache multiple candles
    suspend fun cacheCandles(candles: List<Candle>): Int {
        var cached = 0
        candles.forEach { candle ->
            if (cacheCandle(candle)) cached++
        }
        return cached
    }

    // Get a single candle from cache
    suspend fun getCandle(symbol: String, interval: CandleInterval, openTime: Long): Candle? {
        return try {
            val key = candleKey(symbol, interval, openTime)
            val value = redisTemplate.opsForValue()
                .get(key)
                .awaitFirstOrNull()

            value?.let { objectMapper.readValue(it, Candle::class.java) }
        } catch (e: Exception) {
            logger.error("Error retrieving candle from cache: ${e.message}", e)
            null
        }
    }

    // Get candles in a time range from cache
    suspend fun getCandlesInRange(
        symbol: String,
        interval: CandleInterval,
        startTime: Long,
        endTime: Long
    ): List<Candle> {
        return try {
            val pattern = candleKeyPattern(symbol, interval)
            val keys = redisTemplate.keys(pattern)
                .collectList()
                .awaitSingleOrNull() ?: emptyList()

            if (keys.isEmpty()) return emptyList()

            val values = redisTemplate.opsForValue()
                .multiGet(keys)
                .awaitSingleOrNull() ?: emptyList()

            values.mapNotNull { value ->
                try {
                    objectMapper.readValue(value, Candle::class.java)
                } catch (e: Exception) {
                    logger.warn("Error parsing cached candle: ${e.message}")
                    null
                }
            }.filter { it.openTime >= startTime && it.openTime < endTime }
                .sortedBy { it.openTime }
        } catch (e: Exception) {
            logger.error("Error retrieving candles from cache: ${e.message}", e)
            emptyList()
        }
    }

    // Cache recent trades for quick access (sorted set by timestamp)
    suspend fun cacheRecentTrade(symbol: String, timestamp: Long, tradeData: String): Boolean {
        return try {
            val key = "$RECENT_TRADES_KEY_PREFIX:$symbol"

            // Add to sorted set with timestamp as score
            redisTemplate.opsForZSet()
                .add(key, tradeData, timestamp.toDouble())
                .awaitSingleOrNull()

            // Set expiration
            redisTemplate.expire(key, RECENT_TRADES_TTL)
                .awaitSingleOrNull()

            true
        } catch (e: Exception) {
            logger.error("Error caching recent trade: ${e.message}", e)
            false
        }
    }

    // Get recent trades from cache
    suspend fun getRecentTrades(symbol: String, fromTimestamp: Long): List<String> {
        return try {
            val key = "$RECENT_TRADES_KEY_PREFIX:$symbol"

            // Use Range.closed for range query
            redisTemplate.opsForZSet()
                .rangeByScore(
                    key,
                    org.springframework.data.domain.Range.closed(fromTimestamp.toDouble(), Double.MAX_VALUE)
                )
                .collectList()
                .awaitSingleOrNull() ?: emptyList()
        } catch (e: Exception) {
            logger.error("Error retrieving recent trades from cache: ${e.message}", e)
            emptyList()
        }
    }

    // Check cache health
    suspend fun ping(): Boolean {
        return try {
            redisTemplate.execute { connection ->
                connection.ping()
            }.awaitFirstOrNull() == "PONG"
        } catch (e: Exception) {
            logger.error("Redis ping failed: ${e.message}", e)
            false
        }
    }
}