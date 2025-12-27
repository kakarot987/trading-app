package com.trading.common.model

import java.time.Instant
import com.fasterxml.jackson.annotation.JsonProperty

data class TradeEvent(
    val symbol: String,
    val exchange: String,
    val price: Double,
    val volume: Double,
    val tradeTimestamp: Long,
    val side: String? = null,
    @JsonProperty("trade_id")
    val tradeId: String? = null,
    @JsonProperty("processed_at")
    val processedAt: Long = Instant.now().toEpochMilli()
)