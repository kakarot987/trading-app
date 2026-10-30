package com.trading.historical_service.signals

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/** Persistence of research-only signals. No order placement occurs here. */
@Repository
class ResearchSignalStore(private val db: DatabaseClient) {
    fun save(signal: DirectionalSignal): Mono<Long> =
        db.sql(
            """
            INSERT INTO research_signals (
                symbol, horizon_hours, signal_version, feature_version,
                feature_candle_close_ms, feature_available_at_ms,
                generated_at_ms, expires_at_ms, probability_up, direction, rationale
            ) VALUES (
                :symbol, :horizon, :signalVersion, :featureVersion,
                :candleClose, :availableAt, :generatedAt, :expiresAt,
                :probability, :direction, :rationale
            ) ON CONFLICT DO NOTHING
            """.trimIndent()
        ).bind("symbol", signal.symbol)
            .bind("horizon", signal.horizonHours)
            .bind("signalVersion", signal.signalVersion)
            .bind("featureVersion", signal.featureVersion)
            .bind("candleClose", signal.featureCandleCloseMs)
            .bind("availableAt", signal.featureAvailableAtMs)
            .bind("generatedAt", signal.generatedAtMs)
            .bind("expiresAt", signal.expiresAtMs)
            .let { spec ->
                if (signal.probabilityUp == null) spec.bindNull("probability", java.lang.Double::class.java)
                else spec.bind("probability", signal.probabilityUp)
            }
            .bind("direction", signal.direction.name)
            .bind("rationale", signal.rationale)
            .fetch().rowsUpdated()

    fun list(symbol: String, asOfMs: Long, limit: Int): Flux<DirectionalSignal> {
        require(symbol in setOf("BTCUSDT", "ETHUSDT"))
        require(limit in 1..100)
        return db.sql(
            """
            SELECT symbol, horizon_hours, signal_version, feature_version,
                   feature_candle_close_ms, feature_available_at_ms, generated_at_ms,
                   expires_at_ms, probability_up, direction, rationale
            FROM research_signals
            WHERE symbol = :symbol AND generated_at_ms <= :asOf
              AND feature_available_at_ms <= :asOf
            ORDER BY generated_at_ms DESC, id DESC
            LIMIT :limit
            """.trimIndent()
        ).bind("symbol", symbol).bind("asOf", asOfMs).bind("limit", limit)
            .map { row, _ ->
                fun number(name: String) = (requireNotNull(row.get(name)) as Number)
                DirectionalSignal(
                    symbol = requireNotNull(row.get("symbol", String::class.java)),
                    horizonHours = number("horizon_hours").toInt(),
                    signalVersion = requireNotNull(row.get("signal_version", String::class.java)),
                    featureVersion = requireNotNull(row.get("feature_version", String::class.java)),
                    featureCandleCloseMs = number("feature_candle_close_ms").toLong(),
                    featureAvailableAtMs = number("feature_available_at_ms").toLong(),
                    generatedAtMs = number("generated_at_ms").toLong(),
                    expiresAtMs = number("expires_at_ms").toLong(),
                    probabilityUp = (row.get("probability_up") as? Number)?.toDouble(),
                    direction = SignalDirection.valueOf(requireNotNull(row.get("direction", String::class.java))),
                    rationale = requireNotNull(row.get("rationale", String::class.java))
                )
            }.all()
    }
}
