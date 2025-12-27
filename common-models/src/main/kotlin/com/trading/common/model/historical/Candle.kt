package com.trading.common.model.historical

data class Candle(
    val symbol: String,
    val interval: CandleInterval,
    val openTime: Long,
    val closeTime: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double,
    val trades: Int,
    val buyVolume: Double,
    val notionalValue: Double
)