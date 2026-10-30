-- M6: isolated, simulated spot-only accounts; never exchange balances.
CREATE TABLE paper_wallets (
  wallet_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE paper_balances (
  wallet_id UUID NOT NULL REFERENCES paper_wallets(wallet_id),
  asset VARCHAR(8) NOT NULL CHECK (asset IN ('USDT', 'BTC', 'ETH')),
  available NUMERIC(38,8) NOT NULL DEFAULT 0 CHECK (available >= 0),
  PRIMARY KEY (wallet_id, asset)
);
CREATE TABLE paper_orders (
  order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID NOT NULL REFERENCES paper_wallets(wallet_id),
  client_order_id VARCHAR(128) NOT NULL,
  symbol VARCHAR(16) NOT NULL CHECK (symbol IN ('BTCUSDT','ETHUSDT')),
  side VARCHAR(4) NOT NULL CHECK (side IN ('BUY','SELL')),
  quantity NUMERIC(38,8) NOT NULL CHECK (quantity > 0),
  submitted_at_ms BIGINT NOT NULL CHECK (submitted_at_ms >= 0),
  status VARCHAR(16) NOT NULL DEFAULT 'NEW'
    CHECK (status IN ('NEW','FILLED','REJECTED','CANCELLED')),
  rejection_reason TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_paper_client_order UNIQUE (wallet_id, client_order_id),
  CONSTRAINT paper_rejection_reason CHECK (
    (status = 'REJECTED' AND rejection_reason IS NOT NULL)
    OR (status <> 'REJECTED' AND rejection_reason IS NULL)
  )
);
CREATE TABLE paper_fills (
  fill_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  order_id UUID NOT NULL UNIQUE REFERENCES paper_orders(order_id),
  tick_time_ms BIGINT NOT NULL,
  quantity NUMERIC(38,8) NOT NULL CHECK (quantity > 0),
  execution_price NUMERIC(38,8) NOT NULL CHECK (execution_price > 0),
  gross_quote NUMERIC(38,8) NOT NULL CHECK (gross_quote >= 0),
  fee_quote NUMERIC(38,8) NOT NULL CHECK (fee_quote >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_paper_fill_key UNIQUE (order_id, tick_time_ms)
);
-- Append-only signed movements: both sides of a fill must be posted in a
-- single DB transaction. A DB trigger rejects UPDATE/DELETE of ledger rows.
CREATE TABLE paper_ledger_entries (
  entry_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  wallet_id UUID NOT NULL REFERENCES paper_wallets(wallet_id),
  fill_id UUID NOT NULL REFERENCES paper_fills(fill_id),
  asset VARCHAR(8) NOT NULL CHECK (asset IN ('USDT','BTC','ETH')),
  amount NUMERIC(38,8) NOT NULL CHECK (amount <> 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_paper_ledger_fill_asset UNIQUE (fill_id, asset)
);
CREATE FUNCTION paper_ledger_immutable() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'Paper ledger entries are immutable';
END;
$$;
CREATE TRIGGER trg_paper_ledger_immutable BEFORE UPDATE OR DELETE
  ON paper_ledger_entries FOR EACH ROW EXECUTE FUNCTION paper_ledger_immutable();
CREATE INDEX idx_paper_orders_wallet_time ON paper_orders(wallet_id, submitted_at_ms DESC);
CREATE INDEX idx_paper_ledger_wallet_time ON paper_ledger_entries(wallet_id, created_at DESC);
