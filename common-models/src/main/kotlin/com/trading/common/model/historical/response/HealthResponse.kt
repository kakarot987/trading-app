package com.trading.common.model.historical.response

data class HealthResponse(
    val status: String,
    val timestamp: Long,
    val service: String
)