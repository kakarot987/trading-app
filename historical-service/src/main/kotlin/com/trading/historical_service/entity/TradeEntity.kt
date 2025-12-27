package com.trading.historical_service.entity

import org.springframework.data.relational.core.mapping.Table
import java.math.BigDecimal
import org.springframework.data.annotation.Id

@Table("trades")
data class TradeEntity(
    @Id
    val id: Long? = null,
    val tradeId: String,
    val symbol: String,
    val exchange: String,
    val price: Double,
    val volume: Double,
    val tradeTimestamp: Long,
    val side: String?,
    val processedAt: Long,
    val latencyMs: Long
)