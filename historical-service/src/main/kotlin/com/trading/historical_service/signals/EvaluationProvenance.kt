package com.trading.historical_service.signals

import java.security.MessageDigest

/**
 * Stable fingerprint of the actual evaluated rows, including feature values,
 * observation timestamps and labels. Does not assert that the data is complete.
 * SHA-256 is used for reproducibility, not as an authenticity signature.
 */
object EvaluationProvenance {
    fun sha256(rows: List<EvaluationExample>): String {
        require(rows.isNotEmpty())
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(Charsets.UTF_8))
            digest.update(':'.code.toByte())
            digest.update(bytes)
        }
        for (row in rows) {
            val f = row.features
            field(row.symbol)
            field(row.featureVersion)
            field(row.predictionTimeMs.toString())
            field(row.labelAvailableAtMs.toString())
            field(f.candleCloseTimeMs.toString())
            field(f.availableAtMs.toString())
            field(f.interval)
            field(f.horizonHours.toString())
            for (value in listOf(f.ema12, f.ema26, f.rsi14, f.macdLine, f.macdSignal,
                f.macdHistogram, f.volatility20, f.volumeRatio20, row.nextReturn)) {
                require(value.isFinite())
                field(java.lang.Double.doubleToRawLongBits(value).toString())
            }
            field(row.up.toString())
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}
