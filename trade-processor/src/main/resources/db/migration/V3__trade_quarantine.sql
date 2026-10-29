CREATE TABLE trade_quarantine (
    topic VARCHAR(249) NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    event_payload TEXT,
    failure_reason TEXT NOT NULL,
    quarantined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (topic, partition_id, record_offset)
);
