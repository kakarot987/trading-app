package com.trading.trade_processor.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.trading.common.model.ProcessedTradeEvent
import com.trading.trade_processor.entity.TradeEntity
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * The trade row and its output publication intent are inserted by ONE SQL
 * statement. A duplicate trade_id cannot create a second outbox entry.
 */
@Service
class TradePersistenceService(
    private val databaseClient: DatabaseClient,
    private val objectMapper: ObjectMapper
) {
    fun persistWithOutbox(trade: TradeEntity, processed: ProcessedTradeEvent): Int {
        require(trade.tradeId == processed.tradeId) { "Inconsistent trade IDs" }

        val statement = databaseClient.sql(
            """
            WITH inserted AS (
                INSERT INTO trades (
                    trade_id, symbol, exchange, price, volume,
                    trade_timestamp, side, processed_at, latency_ms
                ) VALUES (
                    :tradeId, :symbol, :exchange, :price, :volume,
                    :timestamp, :side, :processedAt, :latency
                )
                ON CONFLICT (trade_id) DO NOTHING
                RETURNING trade_id
            ), queued AS (
                INSERT INTO trade_outbox (trade_id, payload)
                SELECT trade_id, :payload FROM inserted
                ON CONFLICT (trade_id) DO NOTHING
                RETURNING trade_id
            )
            SELECT COUNT(*)::INT AS inserted_count FROM inserted
            """.trimIndent()
        ).bind("tradeId", trade.tradeId)
            .bind("symbol", trade.symbol)
            .bind("exchange", trade.exchange)
            .bind("price", trade.price)
            .bind("volume", trade.volume)
            .bind("timestamp", trade.tradeTimestamp)
            .bind("processedAt", trade.processedAt)
            .bind("latency", trade.latencyMs)
            .bind("payload", objectMapper.writeValueAsString(processed))

        val bound = if (trade.side == null) {
            statement.bindNull("side", String::class.java)
        } else {
            statement.bind("side", trade.side)
        }

        return bound.map { row, _ ->
            row.get("inserted_count", Integer::class.java)?.toInt()
                ?: error("No insert result")
        }.one().block(Duration.ofSeconds(15))
            ?: error("Trade/outbox insert returned no result")
    }
}
