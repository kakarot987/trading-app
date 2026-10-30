package com.trading.trade_processor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.model.ProcessedTradeEvent
import org.slf4j.LoggerFactory
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * At-least-once output delivery. A process crash after send and before marking
 * published can resend the same trade ID; downstream must deduplicate by ID.
 */
@Service
class OutboxPublisher(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper,
    private val tradePublisher: TradePublisher
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    fun publishPending() {
        val entries = databaseClient.sql(
            """
            SELECT trade_id, payload FROM trade_outbox
            WHERE published_at IS NULL
            ORDER BY created_at, trade_id
            LIMIT 100
            """.trimIndent()
        ).map { row, _ ->
            requireNotNull(row.get("trade_id", String::class.java)) to
                requireNotNull(row.get("payload", String::class.java))
        }.all().collectList().block(Duration.ofSeconds(10)) ?: return

        for ((tradeId, payload) in entries) {
            try {
                val event = objectMapper.readValue(payload, ProcessedTradeEvent::class.java)
                require(event.tradeId == tradeId) { "Outbox trade ID mismatch" }
                tradePublisher.publishProcessedTrade(event)

                databaseClient.sql(
                    "UPDATE trade_outbox SET published_at = now() WHERE trade_id = :tradeId AND published_at IS NULL"
                ).bind("tradeId", tradeId)
                    .fetch().rowsUpdated().block(Duration.ofSeconds(10))
                    ?: error("Outbox update returned no result")
            } catch (exception: Exception) {
                logger.error("Outbox publication failed for tradeId={}; will retry", tradeId, exception)
                break // Keep publication order; retry at the next scheduled tick.
            }
        }
    }
}
