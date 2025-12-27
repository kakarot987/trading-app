CREATE TABLE IF NOT EXISTS trades (
    id             BIGSERIAL PRIMARY KEY,          -- auto-generated PK
    trade_id       VARCHAR(64) UNIQUE NOT NULL,    -- external trade identifier
    symbol         VARCHAR(16) NOT NULL,
    exchange       VARCHAR(32),
    price          NUMERIC(18,8) NOT NULL,
    volume         NUMERIC(18,8) NOT NULL,
    trade_timestamp BIGINT NOT NULL,
    side           VARCHAR(4),
    processed_at   BIGINT NOT NULL,
    latency_ms     BIGINT NOT NULL
);

-- Index for fast lookups by symbol and time
CREATE INDEX IF NOT EXISTS idx_trades_symbol_timestamp
    ON trades(symbol, trade_timestamp);

-- Optional: index for fast lookups by trade_id
CREATE INDEX IF NOT EXISTS idx_trades_trade_id
    ON trades(trade_id);
