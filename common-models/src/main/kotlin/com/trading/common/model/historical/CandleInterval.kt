package com.trading.common.model.historical

enum class CandleInterval(val minutes: Int, val value: String) {
    ONE_MINUTE(1, "1m"),
    FIVE_MINUTES(5, "5m"),
    FIFTEEN_MINUTES(15, "15m"),
    ONE_HOUR(60, "1h"),
    ONE_DAY(1440, "1d");

    companion object {
        fun fromString(value: String): CandleInterval {
            return entries.find { it.value == value }
                ?: throw IllegalArgumentException("Invalid interval: $value. Supported: 1m, 5m, 15m, 1h, 1d")
        }
    }
}