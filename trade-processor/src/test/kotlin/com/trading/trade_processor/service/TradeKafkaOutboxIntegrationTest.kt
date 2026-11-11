package com.trading.trade_processor.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.model.toEntity
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration
import io.r2dbc.postgresql.PostgresqlConnectionFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.serializer.JsonSerializer
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/**
 * Real disposable PostgreSQL + Kafka broker.
 * Verifies the persisted outbox -> Kafka boundary and at-least-once replay;
 * no mock Kafka acknowledgment. This does not simulate a live broker crash.
 */
@Testcontainers
class TradeKafkaOutboxIntegrationTest {
    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("outbox_kafka")
            withUsername("trader")
            withPassword("integration-test")
        }
        @Container @JvmStatic
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.7.1"))
    }

    @Test
    fun `broker receives one durable event and a stable-key replay after simulated crash window`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { conn ->
            conn.createStatement().use { statement ->
                for (ddl in listOf(
                    "/db/migration/V1__create_trades_table.sql",
                    "/db/migration/V2__trade_outbox.sql"
                )) {
                    statement.execute(requireNotNull(javaClass.getResourceAsStream(ddl))
                        .bufferedReader().use { it.readText() })
                }
            }
        }
        val db = DatabaseClient.create(PostgresqlConnectionFactory(
            PostgresqlConnectionConfiguration.builder()
                .host(postgres.host).port(postgres.getMappedPort(5432))
                .database(postgres.databaseName).username(postgres.username)
                .password(postgres.password).build()
        ))
        val mapper = jacksonObjectMapper()
        val service = TradePersistenceService(db, mapper)
        val event = TradeEvent("BTCUSDT", "BINANCE", 70000.0, 0.001,
            1700000000000L, "BUY", "M2-KAFKA-${UUID.randomUUID()}")
        val processed = ProcessedTradeEvent.from(event)
        assertEquals(1, service.persistWithOutbox(event.toEntity(), processed))
        assertEquals(0, service.persistWithOutbox(event.toEntity(), processed))

        val props: Map<String, Any> = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JsonSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true
        )
        val factory = DefaultKafkaProducerFactory<String, ProcessedTradeEvent>(props)
        val template = KafkaTemplate(factory)
        val publisher = OutboxPublisher(db, mapper, TradePublisher(template))
        try {
            publisher.publishPending()
            assertEquals(0L, pending(db))
            assertEquals(1L, count(db, "trades"))

            // Simulate the precise non-atomic crash window: the Kafka send
            // succeeded, but PostgreSQL did not retain the published marker.
            // A restarted outbox publisher therefore sends the same trade ID.
            val reset = db.sql(
                "UPDATE trade_outbox SET published_at=NULL WHERE trade_id=:id"
            ).bind("id", requireNotNull(event.tradeId))
                .fetch().rowsUpdated().block(Duration.ofSeconds(10))
            assertEquals(1L, reset)
            OutboxPublisher(db, mapper, TradePublisher(template)).publishPending()
            assertEquals(0L, pending(db))
            assertEquals(1L, count(db, "trades"))

            val consumer = KafkaConsumer<String, String>(mapOf(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to "m2-evidence-${UUID.randomUUID()}",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
            ))
            consumer.use {
                it.subscribe(listOf("processed-trade-events"))
                val received = mutableListOf<Pair<String, String>>()
                val deadline = System.nanoTime() + Duration.ofSeconds(25).toNanos()
                while (received.size < 2 && System.nanoTime() < deadline) {
                    it.poll(Duration.ofMillis(500)).forEach { record ->
                        received += record.key() to record.value()
                    }
                }
                assertEquals(2, received.size, "Expected initial broker publish and durable outbox replay")
                assertTrue(received.all { (key, value) ->
                    key == event.tradeId &&
                        mapper.readTree(value).path("tradeId").asText() == event.tradeId
                })
            }
        } finally {
            template.destroy()
            factory.destroy()
        }
    }

    private fun count(db: DatabaseClient, table: String): Long {
        require(table == "trades")
        return requireNotNull(db.sql("SELECT COUNT(*) AS n FROM trades")
            .map { row, _ -> (requireNotNull(row.get("n")) as Number).toLong() }
            .one().block(Duration.ofSeconds(10)))
    }

    private fun pending(db: DatabaseClient): Long =
        requireNotNull(db.sql("SELECT COUNT(*) AS n FROM trade_outbox WHERE published_at IS NULL")
            .map { row, _ -> (requireNotNull(row.get("n")) as Number).toLong() }
            .one().block(Duration.ofSeconds(10)))
}
