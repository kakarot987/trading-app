-- Features are immutable observations: a later recalculation gets a new
-- available_at_ms, rather than rewriting what an earlier model could know.
CREATE TABLE IF NOT EXISTS technical_feature_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol VARCHAR(16) NOT NULL,
    interval_trade VARCHAR(10) NOT NULL,
    horizon_hours INTEGER NOT NULL,
    feature_version VARCHAR(64) NOT NULL,
    candle_close_time_ms BIGINT NOT NULL,
    available_at_ms BIGINT NOT NULL,
    ema12 DOUBLE PRECISION NOT NULL,
    ema26 DOUBLE PRECISION NOT NULL,
    rsi14 DOUBLE PRECISION NOT NULL,
    macd_line DOUBLE PRECISION NOT NULL,
    macd_signal DOUBLE PRECISION NOT NULL,
    macd_histogram DOUBLE PRECISION NOT NULL,
    volatility20 DOUBLE PRECISION NOT NULL,
    volume_ratio20 DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_technical_feature_snapshot UNIQUE (
        symbol, interval_trade, candle_close_time_ms, feature_version, available_at_ms
    ),
    CONSTRAINT feature_availability_after_close CHECK (available_at_ms >= candle_close_time_ms),
    CONSTRAINT feature_horizon_positive CHECK (horizon_hours > 0)
);
CREATE INDEX idx_technical_features_asof
    ON technical_feature_snapshots(symbol, interval_trade, feature_version, available_at_ms DESC);
