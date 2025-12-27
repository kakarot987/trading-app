package com.trading.websocker_broadcaster.websocket

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.websocker_broadcaster.model.OutboundMessage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ArrayBlockingQueue

@Component
class TradeWebSocketHandler(
    objectMapper: ObjectMapper
) : BaseWebSocketHandler(objectMapper) {

    fun broadcastTrade(symbol: String, tradeMsg: OutboundMessage.Trade) {
        broadcastJson(symbol, toJson(tradeMsg))
    }
}