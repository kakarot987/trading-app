-- Research signals are non-executable, with immutable provenance and time cutoffs.
CREATE TABLE IF NOT EXISTS research_signals (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  symbol VARCHAR(16) NOT NULL CHECK (symbol IN ('BTCUSDT','ETHUSDT')),
  horizon_hours INTEGER NOT NULL CHECK (horizon_hours = 4),
  signal_version VARCHAR(64) NOT NULL,
  feature_version VARCHAR(64) NOT NULL,
  feature_candle_close_ms BIGINT NOT NULL,
  feature_available_at_ms BIGINT NOT NULL,
  generated_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  probability_up DOUBLE PRECISION NULL CHECK (probability_up BETWEEN 0 AND 1),
  direction VARCHAR(16) NOT NULL CHECK (direction IN ('UP','DOWN','NO_TRADE')),
  rationale TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT research_signal_chronology CHECK (
    feature_candle_close_ms <= feature_available_at_ms
    AND feature_available_at_ms <= generated_at_ms
    AND generated_at_ms < expires_at_ms
  ),
  CONSTRAINT research_direction_requires_score CHECK (
    direction='NO_TRADE' OR probability_up IS NOT NULL
  ),
  CONSTRAINT uq_research_signal UNIQUE (symbol, horizon_hours, signal_version,
    feature_version, feature_candle_close_ms, feature_available_at_ms, generated_at_ms)
);
CREATE INDEX idx_research_signals_asof
 ON research_signals(symbol, generated_at_ms DESC);
