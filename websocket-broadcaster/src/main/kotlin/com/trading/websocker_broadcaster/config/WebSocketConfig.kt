package com.trading.websocker_broadcaster.config

import com.trading.websocker_broadcaster.websocket.AlertWebSocketHandler
import com.trading.websocker_broadcaster.websocket.TradeWebSocketHandler
import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.EnableWebSocket
import org.springframework.web.socket.config.annotation.WebSocketConfigurer
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry

@Configuration
@EnableWebSocket
class WebSocketConfig(
    private val tradeWebSocketHandler: TradeWebSocketHandler,
    private val alertWebSocketHandler: AlertWebSocketHandler
) : WebSocketConfigurer {
    override fun registerWebSocketHandlers(registry: WebSocketHandlerRegistry) {
        registry.addHandler(tradeWebSocketHandler, "/ws/trades").setAllowedOrigins("*")
        registry.addHandler(alertWebSocketHandler, "/ws/alerts").setAllowedOrigins("*")
    }
}