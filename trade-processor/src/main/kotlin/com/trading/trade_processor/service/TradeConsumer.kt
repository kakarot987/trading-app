package com.trading.trade_processor.service

import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.model.toEntity
import com.trading.trade_processor.repository.TradeRepository
import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class TradeConsumer(
    private val tradeRepository: TradeRepository,
    private val redisTemplate: ReactiveRedisTemplate<String, TradeEvent>,
    private val tradePublisher: TradePublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["trade-events"], groupId = "trade-processor")
    fun consume(event: TradeEvent) {
    //    logger.info("Received trade: ${event.symbol} at ${event.price}")

        // Persist to Postgres
        tradeRepository.save(event.toEntity())
            .doOnError { logger.error("DB error", it) }
            .subscribe()

        // Cache in Redis (last 100 trades per symbol)
        redisTemplate.opsForList()
            .leftPush("recent:${event.symbol}", event)
            .then(redisTemplate.opsForList().trim("recent:${event.symbol}", 0, 99))
            .subscribe()

        // Enrich and delegate publishing
        val processed = ProcessedTradeEvent.from(event)
        tradePublisher.publishProcessedTrade(processed)
    }
}