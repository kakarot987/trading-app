// common-models/src/main/kotlin/com/trading/common/model/AlertEvent.kt
package com.trading.common.model

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
