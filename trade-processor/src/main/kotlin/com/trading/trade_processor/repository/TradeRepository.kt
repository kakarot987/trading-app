package com.trading.trade_processor.repository

import com.trading.trade_processor.entity.TradeEntity
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import org.springframework.stereotype.Repository
import reactor.core.publisher.Mono

@Repository
interface TradeRepository : ReactiveCrudRepository<TradeEntity, String> {

    @Modifying
    @Query("""
    INSERT INTO trades (
        trade_id, symbol, exchange, price, volume,
        trade_timestamp, side, processed_at, latency_ms
    )
    VALUES (
        :tradeId, :symbol, :exchange, :price, :volume,
        :tradeTimestamp, :side, :processedAt, :latencyMs
    )
    ON CONFLICT (trade_id) DO NOTHING
""")
    fun insertIfAbsent(
        tradeId: String,
        symbol: String,
        exchange: String,
        price: Double,
        volume: Double,
        tradeTimestamp: Long,
        side: String?,
        processedAt: Long,
        latencyMs: Long
    ): Mono<Int>
}