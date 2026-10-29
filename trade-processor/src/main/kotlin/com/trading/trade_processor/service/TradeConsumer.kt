package com.trading.trade_processor.service

import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.model.toEntity
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@Service
class TradeConsumer(
    private val persistence: TradePersistenceService,
    private val redisTemplate: ReactiveRedisTemplate<String, TradeEvent>
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val insertedCount = AtomicLong()
    private val duplicateCount = AtomicLong()

    @KafkaListener(topics = ["trade-events"], groupId = "trade-processor")
    fun consume(event: TradeEvent) {
        require(!event.tradeId.isNullOrBlank()) {
            "Trade ID is required for idempotent processing"
        }

        val entity = event.toEntity()
        val processed = ProcessedTradeEvent.from(event)
        // One SQL statement commits the trade and its durable publication intent.
        // The Kafka input can be acknowledged only after this returns.
        val insertedRows = persistence.persistWithOutbox(entity, processed)
        when (insertedRows) {
            1 -> insertedCount.incrementAndGet()
            0 -> {
                duplicateCount.incrementAndGet()
                return // No duplicate Redis mutation or new outbox event.
            }
            else -> error("Unexpected inserted row count: $insertedRows")
        }

        // Redis is an ephemeral cache: a temporary cache failure must not block
        // a trade already durably committed with its outbox entry.
        try {
            redisTemplate.opsForList()
                .leftPush("recent:${event.symbol}", event)
                .then(redisTemplate.opsForList().trim("recent:${event.symbol}", 0, 99))
                .block(Duration.ofSeconds(5))
        } catch (exception: Exception) {
            logger.warn("Redis cache update failed for tradeId={}", entity.tradeId, exception)
        }

        val total = insertedCount.get() + duplicateCount.get()
        if (total % 1000L == 0L) {
            logger.info("TRADE_STATS inserted={} duplicates={} total={}",
                insertedCount.get(), duplicateCount.get(), total)
        }
    }
}
