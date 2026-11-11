-- M11: credential lifecycle audit. Wallet capability possession is not
-- user identity; these records track state transitions, not a human actor.
-- Existing V12 wallets start at credential_version=1 without a fabricated
-- historical issuance event.
ALTER TABLE paper_wallet_access
  ADD COLUMN credential_version BIGINT NOT NULL DEFAULT 1
    CHECK (credential_version > 0);

CREATE TABLE paper_wallet_credential_events (
  event_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  wallet_id UUID NOT NULL REFERENCES paper_wallets(wallet_id),
  event_type VARCHAR(12) NOT NULL CHECK (event_type IN ('ROTATED','REVOKED')),
  credential_version BIGINT NOT NULL CHECK (credential_version > 0),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  UNIQUE (wallet_id, credential_version),
  CONSTRAINT ck_revocation_version CHECK (
    event_type <> 'REVOKED' OR credential_version > 0
  )
);

-- No UPDATE/DELETE operations on credential lifecycle events. Rows can
-- only be appended by a successful credential transition.
CREATE TRIGGER trg_paper_credential_event_immutable
  BEFORE UPDATE OR DELETE ON paper_wallet_credential_events
  FOR EACH ROW EXECUTE FUNCTION paper_ledger_immutable();

CREATE INDEX idx_paper_credential_events_wallet
  ON paper_wallet_credential_events(wallet_id, event_id);
