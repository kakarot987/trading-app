package com.trading.trade_processor.model

import com.trading.common.model.TradeEvent
import com.trading.trade_processor.entity.TradeEntity
import java.time.Instant

fun TradeEvent.toEntity(): TradeEntity {
    val now = Instant.now().toEpochMilli()
    return TradeEntity(
        id = null,
        tradeId = requireNotNull(this.tradeId?.takeIf { it.isNotBlank() }) {
            "Trade ID is required for idempotent persistence"
        },
        symbol = this.symbol.uppercase(),
        exchange = this.exchange,
        price = this.price,
        volume = this.volume,
        tradeTimestamp = this.tradeTimestamp,
        side = this.side,
        processedAt = now,
        latencyMs = now - this.tradeTimestamp
    )
}
