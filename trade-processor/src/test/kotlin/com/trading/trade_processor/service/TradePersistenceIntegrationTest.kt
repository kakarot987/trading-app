package com.trading.trade_processor.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.trading.common.model.ProcessedTradeEvent
import com.trading.common.model.TradeEvent
import com.trading.trade_processor.model.toEntity
import io.r2dbc.spi.ConnectionFactories
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.r2dbc.core.DatabaseClient
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import java.time.Duration
import java.util.UUID

/**
 * Runs against real PostgreSQL. A duplicate must not create a second trade
 * or a second pending output; both rows must persist from the same statement.
 */
@Testcontainers
class TradePersistenceIntegrationTest {
    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
            withDatabaseName("trades")
            withUsername("trader")
            withPassword("integration-test")
        }
    }

    @Test
    fun `one trade yields one trade row and one pending outbox row despite replay`() {
        DriverManager.getConnection(postgres.jdbcUrl, postgres.username, postgres.password).use { connection ->
            connection.createStatement().use { sql ->
                sql.execute(
                    """
                    CREATE TABLE trades (
                        id BIGSERIAL PRIMARY KEY,
                        trade_id VARCHAR(64) UNIQUE NOT NULL,
                        symbol VARCHAR(16) NOT NULL,
                        exchange VARCHAR(32),
                        price NUMERIC(18,8) NOT NULL,
                        volume NUMERIC(18,8) NOT NULL,
                        trade_timestamp BIGINT NOT NULL,
                        side VARCHAR(4),
                        processed_at BIGINT NOT NULL,
                        latency_ms BIGINT NOT NULL
                    )
                    """.trimIndent()
                )
                sql.execute(
                    """
                    CREATE TABLE trade_outbox (
                        trade_id VARCHAR(64) PRIMARY KEY REFERENCES trades(trade_id),
                        payload TEXT NOT NULL,
                        created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                        published_at TIMESTAMPTZ
                    )
                    """.trimIndent()
                )
            }
        }

        val connectionFactory = ConnectionFactories.get(
            "r2dbc:postgresql://${postgres.host}:${postgres.getMappedPort(5432)}/${postgres.databaseName}" +
                "?user=${postgres.username}&password=${postgres.password}"
        )
        val db = DatabaseClient.create(connectionFactory)
        val service = TradePersistenceService(db, jacksonObjectMapper())
        val event = TradeEvent(
            symbol = "BTCUSDT",
            exchange = "BINANCE",
            price = 70000.0,
            volume = 0.001,
            tradeTimestamp = 1700000000000L,
            side = "BUY",
            tradeId = "IT-${UUID.randomUUID()}"
        )
        val entity = event.toEntity()
        val output = ProcessedTradeEvent.from(event)

        assertEquals(1, service.persistWithOutbox(entity, output))
        assertEquals(0, service.persistWithOutbox(entity, output))

        val tradeCount = db.sql("SELECT COUNT(*) AS total FROM trades")
            .map { row, _ -> (row.get("total") as Number).toLong() }
            .one().block(Duration.ofSeconds(5))
        val outboxCount = db.sql("SELECT COUNT(*) AS total FROM trade_outbox WHERE published_at IS NULL")
            .map { row, _ -> (row.get("total") as Number).toLong() }
            .one().block(Duration.ofSeconds(5))

        assertEquals(1L, tradeCount)
        assertEquals(1L, outboxCount)
    }
}
