package com.trading.historical_service.repository

import com.trading.historical_service.entity.TradeEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import kotlinx.coroutines.reactive.asFlow

@Repository
interface TradeRepository : CoroutineCrudRepository<TradeEntity, Long> {

    // Query by symbol and timestamp range (aligned with Service 2 schema)
    fun findBySymbolAndTradeTimestampBetweenOrderByTradeTimestampAsc(
        symbol: String,
        startTime: Long,
        endTime: Long
    ): Flow<TradeEntity>

    // Get recent trades
    fun findBySymbolAndTradeTimestampGreaterThanEqualOrderByTradeTimestampDesc(
        symbol: String,
        timestamp: Long
    ): Flow<TradeEntity>

    // Get trades by symbol
    fun findBySymbolOrderByTradeTimestampDesc(
        symbol: String
    ): Flow<TradeEntity>
}