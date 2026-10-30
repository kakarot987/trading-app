-- Existing candles come from locally recorded trades.
ALTER TABLE candles
    ADD COLUMN IF NOT EXISTS data_source VARCHAR(32) NOT NULL DEFAULT 'local_trades';
ALTER TABLE candles
    ADD COLUMN IF NOT EXISTS source_observed_at_ms BIGINT;
COMMENT ON COLUMN candles.data_source IS
    'local_trades or binance_spot_klines; use source to prevent partial local aggregation overwriting full exchange candles';
