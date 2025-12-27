package com.trading.websocker_broadcaster.model

data class AlertEvent(
    val alertId: String,
    val symbol: String,
    val severity: String,
    val type: String,
    val message: String,
    val triggerValue: Double?,
    val threshold: Double?,
    val timestamp: Long
)