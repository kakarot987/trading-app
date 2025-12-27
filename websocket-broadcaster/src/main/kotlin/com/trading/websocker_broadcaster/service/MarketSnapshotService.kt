package com.trading.websocker_broadcaster.service

import com.trading.common.model.MarketSymbolSnapshot
import com.trading.common.model.ProcessedTradeEvent
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class MarketSnapshotService {

    private val symbols = ConcurrentHashMap<String, MarketSymbolSnapshot>()

    fun update(event: ProcessedTradeEvent) {
        val old = symbols[event.symbol]

        val updated = MarketSymbolSnapshot(
            symbol = event.symbol,
            lastPrice = event.price,
            priceChange24h = old?.let { event.price - it.lastPrice } ?: 0.0,
            priceChangePercent24h = old?.let {
                ((event.price - it.lastPrice) / it.lastPrice) * 100
            } ?: 0.0,
            volume24h = (old?.volume24h ?: 0.0) + event.volume
        )

        symbols[event.symbol] = updated
    }

    fun getSnapshot(): List<MarketSymbolSnapshot> = symbols.values.toList()
}
