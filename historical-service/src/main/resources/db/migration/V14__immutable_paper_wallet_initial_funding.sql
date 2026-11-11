-- M15: Record synthetic USDT funding for NEW paper wallets only.
-- Existing balances are intentionally not backfilled or inferred:
-- pre-V14 wallets remain LEGACY_UNVERIFIED for accounting provenance.
CREATE TABLE paper_wallet_funding (
  wallet_id UUID PRIMARY KEY REFERENCES paper_wallets(wallet_id),
  asset VARCHAR(8) NOT NULL DEFAULT 'USDT' CHECK (asset = 'USDT'),
  initial_amount NUMERIC(38,8) NOT NULL CHECK (initial_amount >= 0),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

-- Reuse the V9 immutable-ledger trigger function. An initial funding event
-- cannot be overwritten or deleted without a privileged schema change.
CREATE TRIGGER trg_paper_wallet_funding_immutable
  BEFORE UPDATE OR DELETE ON paper_wallet_funding
  FOR EACH ROW EXECUTE FUNCTION paper_ledger_immutable();
