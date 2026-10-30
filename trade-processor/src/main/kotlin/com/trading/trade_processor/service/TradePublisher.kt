package com.trading.trade_processor.service

import com.trading.common.model.ProcessedTradeEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class TradePublisher(
    private val kafkaTemplate: KafkaTemplate<String, ProcessedTradeEvent>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishProcessedTrade(event: ProcessedTradeEvent) {
        try {
            kafkaTemplate.send("processed-trade-events", event.tradeId, event)
                .get(10, TimeUnit.SECONDS)
        } catch (ex: Exception) {
            logger.error("Failed to publish processed trade: ${event.tradeId}", ex)
            throw ex
        }
    }
}