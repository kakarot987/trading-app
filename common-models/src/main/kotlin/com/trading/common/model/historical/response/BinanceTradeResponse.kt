package com.trading.common.model.historical.response

data class BinanceTradeResponse(
    val id: Long,
    val price: Double,
    val qty: Double,
    val time: Long,
    val isBuyerMaker: Boolean
)