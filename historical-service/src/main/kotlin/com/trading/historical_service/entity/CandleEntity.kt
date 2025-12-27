package com.trading.historical_service.entity

import com.trading.common.model.historical.Candle
import com.trading.common.model.historical.CandleInterval
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import org.springframework.data.annotation.Id

@Table("candles")
data class CandleEntity(
    @Id
    val id: String? = null,
    val symbol: String,
    val intervalTrade: String,
    val openTime: Long,
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val trades: Int,
    val buyVolume: Double,
    val notionalValue: Double,
    val createdAt: Instant = Instant.now()
)

fun CandleEntity.toCandle(interval: CandleInterval): Candle {
    return Candle(
        symbol = this.symbol,
        interval = interval,
        openTime = this.openTime,
        closeTime = this.closeTime,
        open = this.open,
        high = this.high,
        low = this.low,
        close = this.close,
        volume = this.volume,
        trades = this.trades,
        buyVolume = this.buyVolume,
        notionalValue = this.notionalValue
    )
}

fun Candle.toEntity(): CandleEntity {
    return CandleEntity(
        symbol = this.symbol,
        intervalTrade = this.interval.value,
        openTime = this.openTime,
        closeTime = this.closeTime,
        open = this.open,
        high = this.high,
        low = this.low,
        close = this.close,
        volume = this.volume,
        trades = this.trades,
        buyVolume = this.buyVolume,
        notionalValue = this.notionalValue
    )
}