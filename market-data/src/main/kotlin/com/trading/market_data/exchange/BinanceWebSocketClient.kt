package com.trading.market_data.exchange

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.market_data.kafka.KafkaPublisher
import com.trading.common.model.TradeEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession
import org.springframework.web.socket.client.WebSocketClient
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.handler.TextWebSocketHandler
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy

@Service
class BinanceWebSocketClient(
    private val kafkaPublisher: KafkaPublisher,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(BinanceWebSocketHandler().javaClass)
    private val client: WebSocketClient = StandardWebSocketClient()
    private var session: WebSocketSession? = null

    private val symbols = listOf("btcusdt", "ethusdt", "bnbusdt")
    private val wsUrl = "wss://stream.binance.com:9443/stream?streams=" +
            symbols.joinToString("/") { "${it}@trade" }

    @PostConstruct
    fun connect() {
        try {
            logger.info("Connecting to Binance WebSocket...")
            session = client.execute(BinanceWebSocketHandler(), wsUrl).get()
            logger.info("Connected to Binance WebSocket")
        } catch (e: Exception) {
            logger.error("Failed to connect to Binance", e)
        }
    }

    @PreDestroy
    fun disconnect() {
        session?.close()
        logger.info("Disconnected from Binance WebSocket")
    }

    inner class BinanceWebSocketHandler : TextWebSocketHandler() {
        override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
            try {
                val json = objectMapper.readTree(message.payload)
                val data = json.get("data")

                if (data != null) {
                    val event = TradeEvent(
                        symbol = data.get("s").asText(),
                        exchange = "BINANCE",
                        price = data.get("p").asDouble(),
                        volume = data.get("q").asDouble(),
                        tradeTimestamp = data.get("T").asLong(),
                        side = if (data.get("m").asBoolean()) "SELL" else "BUY",
                        tradeId = data.get("t").asText()
                    )

                    kafkaPublisher.publishTradeEvent(event)
                }
            } catch (e: Exception) {
                logger.error("Error processing Binance message", e)
            }
        }

        override fun afterConnectionEstablished(session: WebSocketSession) {
            logger.info("Binance WebSocket connection established")
        }

        override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
            logger.warn("Binance WebSocket connection closed: $status")
            // Implement reconnection logic
            reconnect()
        }

        override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
            logger.error("Binance WebSocket transport error", exception)
        }
    }

    private fun reconnect() {
        Thread.sleep(5000) // Wait 5 seconds before reconnecting
        connect()
    }
}
