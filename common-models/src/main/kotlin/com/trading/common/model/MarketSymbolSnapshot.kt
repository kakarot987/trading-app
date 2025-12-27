package com.trading.common.model

data class MarketSymbolSnapshot(
    val symbol: String,
    val lastPrice: Double,
    val priceChange24h: Double,
    val priceChangePercent24h: Double,
    val volume24h: Double
)

data class MarketSnapshotResponse(
    val symbols: List<MarketSymbolSnapshot>
)
