package com.trading.websocker_broadcaster.websocket

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap

abstract class BaseWebSocketHandler(
    private val objectMapper: ObjectMapper
) : TextWebSocketHandler() {

    protected val logger = LoggerFactory.getLogger(javaClass)
    private val sessions = ConcurrentHashMap<String, WebSocketSession>()
    private val queues = ConcurrentHashMap<String, ArrayBlockingQueue<String>>()
    private val filters = ConcurrentHashMap<String, Set<String>>() // sessionId -> symbols

    private val queueSize: Int = System.getenv().getOrDefault("WS_QUEUE_SIZE", "1000").toInt()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        sessions[session.id] = session
        queues[session.id] = ArrayBlockingQueue(queueSize)
        filters[session.id] = emptySet()
        logger.info("WS connected: ${session.id}")
        println("WS connected: ${session.id}")
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        try {
            val node: JsonNode = objectMapper.readTree(message.payload)
            val symbols = node.get("symbols")?.map { it.asText().uppercase() }?.toSet() ?: emptySet()
            filters[session.id] = symbols
            logger.info("WS connected ${session.id}: ${symbols.ifEmpty { setOf("ALL") }}")
            println("WS connected ${session.id}: ${symbols.ifEmpty { setOf("ALL") }}")

        } catch (ex: Exception) {
            logger.warn("Invalid filter message from ${session.id}: ${ex.message}")
        }
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        sessions.remove(session.id)
        queues.remove(session.id)
        filters.remove(session.id)
        logger.info("WS closed: ${session.id} (${status.code})")
        println("WS closed: ${session.id} (${status.code})")
    }

    protected fun broadcastJson(symbol: String?, json: String) {
        sessions.values.forEach { session ->
            val symbols = filters[session.id] ?: emptySet()
            if (symbols.isEmpty() || symbol == null || symbols.contains(symbol.uppercase())) {
                val q = queues[session.id]
                if (q != null && !q.offer(json)) {
                    q.poll() // drop-oldest
                    q.offer(json)
                }
                drainQueue(session, q)
            }
        }
   //     println("symbol broadcasting: $symbol with jsonValue $json")
    }

    private fun drainQueue(session: WebSocketSession, queue: ArrayBlockingQueue<String>?) {
        if (queue == null || !session.isOpen) return
        var payload = queue.poll()
        while (payload != null) {
            try {
                session.sendMessage(TextMessage(payload))
            } catch (ex: Exception) {
                logger.warn("Send failed to ${session.id}: ${ex.message}")
                break
            }
            payload = queue.poll()
        }
    }

    protected fun toJson(any: Any): String = objectMapper.writeValueAsString(any)
}