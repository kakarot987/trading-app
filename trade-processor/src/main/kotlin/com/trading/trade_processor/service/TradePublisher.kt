package com.trading.trade_processor.service

import com.trading.common.model.ProcessedTradeEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class TradePublisher(
    private val kafkaTemplate: KafkaTemplate<String, ProcessedTradeEvent>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishProcessedTrade(event: ProcessedTradeEvent) {
        kafkaTemplate.send("processed-trade-events", event.symbol, event)
            .whenComplete { result, ex ->
                if (ex == null) {
              //      logger.debug("Published processed trade: ${event.symbol} at ${event.price}")
                } else {
                    logger.error("Failed to publish processed trade: ${event.symbol}", ex)
                }
            }
    }
}