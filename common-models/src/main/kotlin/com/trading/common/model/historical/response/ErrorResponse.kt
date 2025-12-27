package com.trading.common.model.historical.response

data class ErrorResponse(
    val error: String,
    val timestamp: Long
)