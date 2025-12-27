package com.trading.market_data.kafka

import com.trading.common.model.TradeEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class KafkaPublisher(
    private val kafkaTemplate: KafkaTemplate<String, TradeEvent>
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun publishTradeEvent(event: TradeEvent) {
        try {
            kafkaTemplate.send("trade-events", event.symbol, event)
                .whenComplete { result, ex ->
                    if (ex == null) {
                //        logger.info("Published event: ${event.symbol} at ${event.price}")
                    } else {
                        logger.error("Failed to publish event: ${event.symbol}", ex)
                    }
                }
        } catch (e: Exception) {
            logger.error("Error publishing to Kafka", e)
        }
    }
}
