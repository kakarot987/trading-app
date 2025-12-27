package com.trading.websocker_broadcaster.kafka

import com.trading.common.model.ProcessedTradeEvent
import com.trading.websocker_broadcaster.model.OutboundMessage
import com.trading.websocker_broadcaster.service.MarketSnapshotService
import com.trading.websocker_broadcaster.websocket.TradeWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
class TradeEventListener(
    private val wsHandler: TradeWebSocketHandler,
    private val snapshotService: MarketSnapshotService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["processed-trade-events"], containerFactory = "tradeKafkaListenerContainerFactory")
    fun onTrade(@Payload event: ProcessedTradeEvent) {
        logger.debug("Consumed trade ${event.symbol} @ ${event.price}")
        snapshotService.update(event)
        wsHandler.broadcastTrade(event.symbol, OutboundMessage.Trade(payload = event))
    }
}