package com.trading.trade_processor.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Persist invalid trade events before allowing the Kafka error handler to
 * recover their offsets. A PostgreSQL failure throws, so the error handler
 * must NOT treat the record as recovered.
 */
@Service
class TradeQuarantineService(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper
) {
    fun quarantine(record: ConsumerRecord<*, *>, failure: Exception) {
        val payload = record.value()?.let(objectMapper::writeValueAsString)
        val statement = databaseClient.sql(
            """
            INSERT INTO trade_quarantine (
                topic, partition_id, record_offset, event_payload, failure_reason
            ) VALUES (:topic, :partition, :offset, :payload, :reason)
            ON CONFLICT (topic, partition_id, record_offset) DO NOTHING
            """.trimIndent()
        )
            .bind("topic", record.topic())
            .bind("partition", record.partition())
            .bind("offset", record.offset())
            .bind("reason", failure.message?.take(2000) ?: failure.javaClass.name)

        val bound = if (payload == null) {
            statement.bindNull("payload", String::class.java)
        } else {
            statement.bind("payload", payload)
        }
        bound.fetch().rowsUpdated().block(Duration.ofSeconds(15))
            ?: error("Quarantine write returned no result")
    }
}
