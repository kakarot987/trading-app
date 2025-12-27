package com.trading.common.model.historical.response

import com.trading.common.model.historical.Candle

data class HistoricalQueryResponse(
    val symbol: String,
    val interval: String,
    val candles: List<Candle>,
    val count: Int,
    val fromCache: Boolean,
    val fromExternalApi: Boolean = false,
    val queryTimeMs: Long
)