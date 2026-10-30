// common-models/src/main/kotlin/com/trading/common/model/ProcessedTradeEvent.kt
package com.trading.common.model

import java.time.Instant

data class ProcessedTradeEvent(
    val tradeId: String,
    val symbol: String,
    val price: Double,
    val volume: Double,
    val timestamp: Long,
    val processedAt: Long,
    val latencyMs: Long,
    val notionalValue: Double,
    val isBuy: Boolean
) {
    companion object {
        fun from(event: TradeEvent): ProcessedTradeEvent {
            val now = Instant.now().toEpochMilli()
            return ProcessedTradeEvent(
                tradeId = requireNotNull(event.tradeId?.takeIf { it.isNotBlank() }) {
                    "Trade ID is required for idempotent processing"
                },
                symbol = event.symbol.uppercase(),
                price = event.price,
                volume = event.volume,
                timestamp = event.tradeTimestamp,
                processedAt = now,
                latencyMs = now - event.tradeTimestamp,
                notionalValue = event.price * event.volume,
                isBuy = event.side?.uppercase() == "BUY"
            )
        }
    }
}
