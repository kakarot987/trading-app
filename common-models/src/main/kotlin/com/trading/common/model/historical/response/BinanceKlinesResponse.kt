package com.trading.common.model.historical.response

import com.trading.common.model.historical.BinanceCandle

data class BinanceKlinesResponse(
    val symbol: String,
    val interval: String,
    val candles: List<BinanceCandle>
)