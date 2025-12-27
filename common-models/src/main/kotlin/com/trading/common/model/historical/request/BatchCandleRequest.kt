package com.trading.common.model.historical.request

data class BatchCandleRequest(
    val symbols: List<String>,
    val interval: String,
    val startTime: Long,
    val endTime: Long,
    val limit: Int? = 500
)