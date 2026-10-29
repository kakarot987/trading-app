package com.trading.trade_processor.service

import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.model.toEntity
import com.trading.trade_processor.repository.TradeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@Service
class TradeConsumer(
    private val tradeRepository: TradeRepository,
    private val redisTemplate: ReactiveRedisTemplate<String, TradeEvent>,
    private val tradePublisher: TradePublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val insertedCount = AtomicLong()
    private val duplicateCount = AtomicLong()

    @KafkaListener(topics = ["trade-events"], groupId = "trade-processor")
    fun consume(event: TradeEvent) {
    //    logger.info("Received trade: ${event.symbol} at ${event.price}")

        // Persist to Postgres
        val entity = event.toEntity()

        val insertedRows = tradeRepository.insertIfAbsent(
            tradeId = entity.tradeId,
            symbol = entity.symbol,
            exchange = entity.exchange,
            price = entity.price,
            volume = entity.volume,
            tradeTimestamp = entity.tradeTimestamp,
            side = entity.side,
            processedAt = entity.processedAt,
            latencyMs = entity.latencyMs
        ).block(Duration.ofSeconds(15))
            ?: error("Database insert returned no result for ${entity.tradeId}")

        when (insertedRows) {
            1 -> insertedCount.incrementAndGet()
            0 -> duplicateCount.incrementAndGet()
            else -> error("Unexpected inserted row count: $insertedRows")
        }

        // Cache in Redis (last 100 trades per symbol)
        redisTemplate.opsForList()
            .leftPush("recent:${event.symbol}", event)
            .then(redisTemplate.opsForList().trim("recent:${event.symbol}", 0, 99))
            .block(Duration.ofSeconds(5))

// Enrich and delegate publishing
        val processed = ProcessedTradeEvent.from(event)
        tradePublisher.publishProcessedTrade(processed)

        val total = insertedCount.get() + duplicateCount.get()

        if (total % 1000L == 0L) {
            logger.info(
                "TRADE_STATS inserted={} duplicates={} total={}",
                insertedCount.get(),
                duplicateCount.get(),
                total
            )
        }
    }
}