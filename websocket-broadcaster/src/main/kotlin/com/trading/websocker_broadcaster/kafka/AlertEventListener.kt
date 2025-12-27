package com.trading.websocker_broadcaster.kafka

import com.trading.websocker_broadcaster.model.AlertEvent
import com.trading.websocker_broadcaster.model.OutboundMessage
import com.trading.websocker_broadcaster.websocket.AlertWebSocketHandler
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.messaging.handler.annotation.Payload
import org.springframework.stereotype.Service

@Service
class AlertEventListener(
    private val wsHandler: AlertWebSocketHandler
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @KafkaListener(topics = ["alert-events"], containerFactory = "alertKafkaListenerContainerFactory")
    fun onAlert(@Payload event: AlertEvent) {
        logger.debug("Consumed alert ${event.type} for ${event.symbol}")
        wsHandler.broadcastAlert(event.symbol, OutboundMessage.Alert(payload = event))
    }
}