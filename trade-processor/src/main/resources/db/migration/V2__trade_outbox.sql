CREATE TABLE trade_outbox (
    trade_id VARCHAR(64) PRIMARY KEY REFERENCES trades(trade_id),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_trade_outbox_pending
    ON trade_outbox(created_at) WHERE published_at IS NULL;
