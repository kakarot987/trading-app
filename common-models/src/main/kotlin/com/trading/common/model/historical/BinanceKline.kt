package com.trading.common.model.historical

import com.fasterxml.jackson.annotation.JsonProperty

data class BinanceKline(
    @JsonProperty("0") val openTime: Long,
    @JsonProperty("1") val open: String,
    @JsonProperty("2") val high: String,
    @JsonProperty("3") val low: String,
    @JsonProperty("4") val close: String,
    @JsonProperty("5") val volume: String,
    @JsonProperty("6") val closeTime: Long,
    @JsonProperty("7") val quoteVolume: String,
    @JsonProperty("8") val trades: Int,
    @JsonProperty("9") val buyBaseVolume: String,
    @JsonProperty("10") val buyQuoteVolume: String
)