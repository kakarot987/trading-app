package com.trading.historical_service.repository

import com.trading.historical_service.entity.CandleEntity
import kotlinx.coroutines.flow.Flow
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.flow
import org.springframework.stereotype.Repository

@Repository
class CustomCandleRepository(
    private val databaseClient: DatabaseClient
) {

    // Aggregate trades into candles on the fly
    fun aggregateTradesToCandles(
        symbol: String,
        intervalMinutes: Int,
        startTime: Long,
        endTime: Long
    ): Flow<CandleEntity> {
        val sql = """
            SELECT 
                $1::VARCHAR as symbol,
                $2::VARCHAR as interval_trade,
                bucket as open_time,
                bucket + ($3::BIGINT * 60000) as close_time,
                first_price as open,
                max_price as high,
                min_price as low,
                last_price as close,
                total_volume as volume,
                trade_count as trades,
                buy_volume,
                notional_value
            FROM (
                SELECT 
                    (trade_timestamp / ($3::BIGINT * 60000)) * ($3::BIGINT * 60000) as bucket,
                    (array_agg(price ORDER BY trade_timestamp ASC))[1] as first_price,
                    MAX(price) as max_price,
                    MIN(price) as min_price,
                    (array_agg(price ORDER BY trade_timestamp DESC))[1] as last_price,
                    SUM(volume) as total_volume,
                    COUNT(*)::INTEGER as trade_count,
                    SUM(CASE WHEN side = 'BUY' THEN volume ELSE 0 END) as buy_volume,
                    SUM(price * volume) as notional_value
                FROM trades
                WHERE symbol = $4::VARCHAR
                    AND trade_timestamp >= $5::BIGINT
                    AND trade_timestamp < $6::BIGINT
                GROUP BY bucket
            ) aggregated
            ORDER BY bucket ASC
        """.trimIndent()

        return databaseClient.sql(sql)
            .bind(0, symbol)
            .bind(1, "${intervalMinutes}m")
            .bind(2, intervalMinutes.toLong())  // FIXED: Convert to Long
            .bind(3, symbol)
            .bind(4, startTime)  // These are already Long
            .bind(5, endTime)
            .map { row ->
                CandleEntity(
                    symbol = row.get("symbol", String::class.java)!!,
                    intervalTrade = row.get("interval_trade", String::class.java)!!,
                    openTime = row.get("open_time", java.lang.Long::class.java)!!.toLong(),
                    closeTime = row.get("close_time", java.lang.Long::class.java)!!.toLong(),
                    open = row.get("open", java.lang.Double::class.java)!!.toDouble(),
                    high = row.get("high", java.lang.Double::class.java)!!.toDouble(),
                    low = row.get("low", java.lang.Double::class.java)!!.toDouble(),
                    close = row.get("close", java.lang.Double::class.java)!!.toDouble(),
                    volume = row.get("volume", java.lang.Double::class.java)!!.toDouble(),
                    trades = row.get("trades", Integer::class.java)!!.toInt(),
                    buyVolume = row.get("buy_volume", java.lang.Double::class.java)!!.toDouble(),
                    notionalValue = row.get("notional_value", java.lang.Double::class.java)!!.toDouble()
                )
            }
            .flow()
    }

    // Get distinct symbols from trades
    fun getDistinctSymbols(): Flow<String> {
        return databaseClient.sql("SELECT DISTINCT symbol FROM trades ORDER BY symbol")
            .map { row -> row.get("symbol", String::class.java)!! }
            .flow()
    }
}