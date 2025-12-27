
CREATE TABLE IF NOT EXISTS candles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(16) NOT NULL,
    interval_trade VARCHAR(10) NOT NULL,  -- 1m, 5m, 15m, 1h, 1d
    open_time BIGINT NOT NULL,
    close_time BIGINT NOT NULL,
    open NUMERIC(18,8) NOT NULL,
    high NUMERIC(18,8) NOT NULL,
    low NUMERIC(18,8) NOT NULL,
    close NUMERIC(18,8) NOT NULL,
    volume NUMERIC(18,8) NOT NULL,
    trades INTEGER NOT NULL DEFAULT 0,
    buy_volume NUMERIC(18,8) NOT NULL DEFAULT 0,
    notional_value NUMERIC(18,8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_candle UNIQUE (symbol, interval_trade, open_time)
);

-- Indexes for fast queries
CREATE INDEX idx_candles_symbol_interval_time
    ON candles(symbol, interval_trade, open_time DESC);

CREATE INDEX idx_candles_symbol_interval
    ON candles(symbol, interval_trade);

CREATE INDEX idx_candles_open_time
    ON candles(open_time DESC);

-- Add comment for documentation
COMMENT ON TABLE candles IS 'Pre-aggregated OHLCV candle data for various time intervals';
COMMENT ON COLUMN candles.interval_trade IS 'Time interval: 1m, 5m, 15m, 1h, 1d';
COMMENT ON COLUMN candles.open_time IS 'Candle open time in milliseconds (Unix timestamp)';
COMMENT ON COLUMN candles.close_time IS 'Candle close time in milliseconds (Unix timestamp)';
COMMENT ON COLUMN candles.buy_volume IS 'Volume from buy side trades';
COMMENT ON COLUMN candles.notional_value IS 'Total value of all trades (price * volume)';