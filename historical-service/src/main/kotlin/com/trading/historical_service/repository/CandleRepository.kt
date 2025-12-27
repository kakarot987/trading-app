package com.trading.historical_service.repository

import com.trading.historical_service.entity.CandleEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository


@Repository
interface CandleRepository : CoroutineCrudRepository<CandleEntity, String> {

    // FIXED: Use explicit query with type casting
    @Query("""
        SELECT * FROM candles 
        WHERE symbol = :symbol 
        AND interval_trade = :intervalTrade 
        AND open_time >= :startTime 
        AND open_time < :endTime 
        ORDER BY open_time ASC
    """)
    fun findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(
        symbol: String,
        intervalTrade: String,
        startTime: Long,
        endTime: Long
    ): Flow<CandleEntity>

    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM candles 
            WHERE symbol = :symbol 
            AND interval_trade = :intervalTrade 
            AND open_time = :openTime
        )
    """)
    suspend fun existsBySymbolAndIntervalAndOpenTime(
        symbol: String,
        intervalTrade: String,
        openTime: Long
    ): Boolean

//    fun findBySymbolAndIntervalOrderByOpenTimeDesc(
//        symbol: String,
//        interval: String
//    ): Flow<CandleEntity>
}