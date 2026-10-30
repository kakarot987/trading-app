package com.trading.historical_service.features

import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Service
import java.time.Duration

/**
 * Recomputes closed UTC 4h candles from durable trades, including late trades
 * arriving during the lookback window. Repeated runs update the same candle.
 * Gaps stay absent; no artificial zero-volume bar is introduced.
 */
@Service
class FourHourCandleWriter(private val databaseClient: DatabaseClient) {
    fun refresh(symbol: String, observedAtMs: Long = System.currentTimeMillis()): Long {
        require(symbol in CandleSeries.spotSymbols) { "Unsupported symbol: $symbol" }
        val interval = CandleSeries.FOUR_HOURS_MS
        val cutoff = Math.floorDiv(observedAtMs, interval) * interval
        val start = cutoff - 120L * interval
        return databaseClient.sql(
            """
            INSERT INTO candles (
                symbol, interval_trade, open_time, close_time, open, high,
                low, close, volume, trades, buy_volume, notional_value
            )
            SELECT :symbol, '240m', bucket, bucket + :interval,
                   (array_agg(price ORDER BY trade_timestamp, trade_id))[1],
                   MAX(price), MIN(price),
                   (array_agg(price ORDER BY trade_timestamp DESC, trade_id DESC))[1],
                   SUM(volume), COUNT(*)::INT,
                   SUM(CASE WHEN side = 'BUY' THEN volume ELSE 0 END),
                   SUM(price * volume)
            FROM (
                SELECT (trade_timestamp / :interval) * :interval AS bucket,
                       trade_timestamp, trade_id, price, volume, side
                FROM trades
                WHERE symbol = :symbol
                  AND trade_timestamp >= :start
                  AND trade_timestamp < :cutoff
            ) source
            GROUP BY bucket
            ON CONFLICT (symbol, interval_trade, open_time)
            DO UPDATE SET
                close_time = EXCLUDED.close_time,
                open = EXCLUDED.open,
                high = EXCLUDED.high,
                low = EXCLUDED.low,
                close = EXCLUDED.close,
                volume = EXCLUDED.volume,
                trades = EXCLUDED.trades,
                buy_volume = EXCLUDED.buy_volume,
                notional_value = EXCLUDED.notional_value
            """.trimIndent()
        ).bind("symbol", symbol)
            .bind("interval", interval)
            .bind("start", start)
            .bind("cutoff", cutoff)
            .fetch().rowsUpdated().block(Duration.ofSeconds(30))
            ?: error("4h candle refresh returned no result")
    }
}
