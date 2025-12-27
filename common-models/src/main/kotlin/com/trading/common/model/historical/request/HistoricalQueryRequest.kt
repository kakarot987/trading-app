package com.trading.common.model.historical.request

import com.trading.common.model.historical.CandleInterval

data class HistoricalQueryRequest(
    val symbol: String,
    val interval: CandleInterval,
    val startTime: Long,
    val endTime: Long,
    val limit: Int = 500
)