package com.trading.websocker_broadcaster.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.websocker_broadcaster.model.OutboundMessage
import org.springframework.stereotype.Component

@Component
class AlertWebSocketHandler(
    objectMapper: ObjectMapper
) : BaseWebSocketHandler(objectMapper) {

    fun broadcastAlert(symbol: String, alertMsg: OutboundMessage.Alert) {
        broadcastJson(symbol, toJson(alertMsg))
    }
}