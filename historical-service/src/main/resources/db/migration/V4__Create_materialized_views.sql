-- V3__Create_materialized_views.sql
-- Materialized views for performance optimization

-- Materialized view for latest candles per symbol/interval
CREATE MATERIALIZED VIEW IF NOT EXISTS latest_candles AS
SELECT DISTINCT ON (symbol, interval_trade)
    symbol,
    interval_trade,
    open_time,
    close_time,
    open,
    high,
    low,
    close,
    volume,
    trades,
    buy_volume,
    notional_value,
    created_at
FROM candles
ORDER BY symbol, interval_trade, open_time DESC;

-- Unique index for fast lookups
CREATE UNIQUE INDEX idx_latest_candles_symbol_interval
    ON latest_candles(symbol, interval_trade);

-- Regular index for time-based queries
CREATE INDEX idx_latest_candles_open_time
    ON latest_candles(open_time DESC);

-- Function to refresh materialized view
CREATE OR REPLACE FUNCTION refresh_latest_candles()
RETURNS TRIGGER AS $$
BEGIN
    -- Refresh concurrently to avoid locking
    REFRESH MATERIALIZED VIEW CONCURRENTLY latest_candles;
    RETURN NEW;
EXCEPTION
    WHEN OTHERS THEN
        -- If concurrent refresh fails (e.g., first time), do regular refresh
        REFRESH MATERIALIZED VIEW latest_candles;
        RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to auto-refresh on candle insert/update
CREATE TRIGGER trigger_refresh_latest_candles
AFTER INSERT OR UPDATE ON candles
FOR EACH STATEMENT
EXECUTE FUNCTION refresh_latest_candles();

-- Initial refresh
REFRESH MATERIALIZED VIEW latest_candles;

-- Add comments
COMMENT ON MATERIALIZED VIEW latest_candles IS 'Latest candle for each symbol/interval combination for fast access';
COMMENT ON FUNCTION refresh_latest_candles IS 'Auto-refresh materialized view when candles are inserted/updated';