package com.trading.websocker_broadcaster.model

import com.trading.common.model.ProcessedTradeEvent

sealed class OutboundMessage {
    data class Trade(val type: String = "trade", val payload: ProcessedTradeEvent) : OutboundMessage()
    data class Alert(val type: String = "alert", val payload: AlertEvent) : OutboundMessage()
}
