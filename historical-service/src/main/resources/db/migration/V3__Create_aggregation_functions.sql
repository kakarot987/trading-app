CREATE OR REPLACE FUNCTION aggregate_trades_to_candles(
    p_symbol VARCHAR,
    p_interval_minutes INTEGER,
    p_start_time BIGINT,
    p_end_time BIGINT
)
RETURNS TABLE (
    symbol VARCHAR,
    interval_trade VARCHAR,
    open_time BIGINT,
    close_time BIGINT,
    open DOUBLE PRECISION,
    high DOUBLE PRECISION,
    low DOUBLE PRECISION,
    close DOUBLE PRECISION,
    volume DOUBLE PRECISION,
    trades INTEGER,
    buy_volume DOUBLE PRECISION,
    notional_value DOUBLE PRECISION
) AS $$
BEGIN
    RETURN QUERY
    SELECT
        t.symbol,
        (p_interval_minutes || 'm')::VARCHAR AS interval_trade,
        (t.trade_timestamp / (p_interval_minutes * 60000)) * (p_interval_minutes * 60000) AS open_time,
        ((t.trade_timestamp / (p_interval_minutes * 60000)) + 1) * (p_interval_minutes * 60000) AS close_time,
        (array_agg(t.price ORDER BY t.trade_timestamp ASC))[1] AS open,
        MAX(t.price) AS high,
        MIN(t.price) AS low,
        (array_agg(t.price ORDER BY t.trade_timestamp DESC))[1] AS close,
        SUM(t.volume) AS volume,
        COUNT(*)::INTEGER AS trades,
        SUM(CASE WHEN t.side = 'BUY' THEN t.volume ELSE 0 END) AS buy_volume,
        SUM(t.price * t.volume) AS notional_value
    FROM trades t
    WHERE t.symbol = p_symbol
      AND t.trade_timestamp >= p_start_time
      AND t.trade_timestamp < p_end_time
    GROUP BY t.symbol, (t.trade_timestamp / (p_interval_minutes * 60000))
    ORDER BY open_time ASC;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION auto_aggregate_recent_candles()
RETURNS TABLE (
    symbol VARCHAR,
    interval_trade VARCHAR,
    candles_created INTEGER
) AS $$
DECLARE
    v_symbol VARCHAR;
    v_interval INTEGER;
    v_end_time BIGINT;
    v_start_time BIGINT;
    v_candles_inserted INTEGER;
    v_total_candles INTEGER := 0;
BEGIN
    v_end_time := EXTRACT(EPOCH FROM CURRENT_TIMESTAMP)::BIGINT * 1000;

    FOR v_interval IN SELECT unnest(ARRAY[1, 5, 15, 60, 1440]) LOOP
        v_start_time := v_end_time - (v_interval * 60000 * 100);

        FOR v_symbol IN SELECT DISTINCT s.symbol FROM trades s WHERE s.trade_timestamp >= v_start_time LOOP
            WITH new_candles AS (
                INSERT INTO candles (symbol, interval_trade, open_time, close_time, open, high, low, close, volume, trades, buy_volume, notional_value)
                SELECT * FROM aggregate_trades_to_candles(v_symbol, v_interval, v_start_time, v_end_time)
                ON CONFLICT (symbol, interval_trade, open_time) DO UPDATE
                SET
                    close = EXCLUDED.close,
                    high = GREATEST(candles.high, EXCLUDED.high),
                    low = LEAST(candles.low, EXCLUDED.low),
                    volume = EXCLUDED.volume,
                    trades = EXCLUDED.trades,
                    buy_volume = EXCLUDED.buy_volume,
                    notional_value = EXCLUDED.notional_value
                RETURNING 1
            )
            SELECT COUNT(*)::INTEGER INTO v_candles_inserted FROM new_candles;

            v_total_candles := v_total_candles + v_candles_inserted;

            RETURN QUERY SELECT v_symbol, v_interval || 'm', v_candles_inserted;
        END LOOP;
    END LOOP;

    RAISE NOTICE 'Total candles created/updated: %', v_total_candles;
END;
$$ LANGUAGE plpgsql;


CREATE OR REPLACE FUNCTION cleanup_old_candles(days_to_keep INTEGER DEFAULT 90)
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
    cutoff_time BIGINT;
BEGIN
    cutoff_time := EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - (days_to_keep || ' days')::INTERVAL))::BIGINT * 1000;

    DELETE FROM candles WHERE open_time < cutoff_time;
    GET DIAGNOSTICS deleted_count = ROW_COUNT;

    RAISE NOTICE 'Deleted % old candles (older than % days)', deleted_count, days_to_keep;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;
