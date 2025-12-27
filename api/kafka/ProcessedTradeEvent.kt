package api.kafka.model

import java.time.Instant
import java.util.UUID


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
                tradeId = event.tradeId ?: UUID.randomUUID().toString(),
                symbol = event.symbol.uppercase(),
                price = event.price,
                volume = event.volume,
                timestamp = event.timestamp,
                processedAt = now,
                latencyMs = now - event.timestamp,
                notionalValue = event.price * event.volume,
                isBuy = event.side?.uppercase() == "BUY"
            )
        }
    }
}
