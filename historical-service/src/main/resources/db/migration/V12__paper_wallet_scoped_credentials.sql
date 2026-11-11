-- Private-demo capability credentials. A wallet UUID is never an authorization
-- secret. Existing pre-V12 wallets are deliberately unclaimed, with no
-- guessed owner or migration of a shared bearer credential.
CREATE TABLE paper_wallet_access (
    wallet_id UUID PRIMARY KEY REFERENCES paper_wallets(wallet_id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE
        CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at TIMESTAMPTZ
);
CREATE INDEX idx_paper_wallet_access_active
    ON paper_wallet_access(wallet_id) WHERE revoked_at IS NULL;
