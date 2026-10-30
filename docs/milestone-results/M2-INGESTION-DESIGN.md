# M2 — ingestion correctness and remaining verification

## Implementation

- Binance trade identity is the namespaced exchange, symbol and native trade ID.
- Events without trade IDs are rejected before PostgreSQL or Redis mutation.
- PostgreSQL `trades.trade_id` is unique; duplicates do not reinsert or mutate Redis.
- A single PostgreSQL statement inserts a trade and its outbox entry. The
  consumer returns successfully only after the database statement completes.
- The scheduled outbox worker publishes processed trades, awaits Kafka's send
  acknowledgement, then records `published_at`.
- Output records are keyed by stable trade IDs.

## Delivery contract

**Input: at least once.** A PostgreSQL outage must retain the Kafka record for
retry; the database uniqueness constraint prevents duplicate rows.

**Output: at least once.** A crash between sending to Kafka and marking the
outbox record published can result in a duplicate output. Consumers must
deduplicate using `tradeId`. Exactly-once delivery is not claimed.

**Redis: best effort.** Redis is a cache, and its outage will not block durable
persistence; Redis recent-trade lists can be incomplete until refreshed.

**Invalid input:** Missing or blank IDs fail processing, and the retry
handler currently retries indefinitely. A poison record can therefore stop
progress on its partition. A durable quarantine/dead-letter workflow is a
prerequisite for production.

**Feed caveat:** Alpha Vantage and mock observations use identifiers generated
per fetch. They are distinguishable observations, not stable exchange trade IDs.

**Outbox caveat:** The scheduled worker is suitable for one local trade-processor
instance. Scaling to multiple replicas requires row claiming/locking to avoid
concurrent publishes. Pending rows are retained indefinitely.

## Required verification before declaring M2 complete

1. CI compilation and all backend unit tests, including new regression tests.
2. Integration test with PostgreSQL + Kafka: new trade produces exactly one
   database row and an eventual processed event.
3. Duplicate input produces no second row or Redis mutation.
4. Simulate PostgreSQL outage: input offset does not advance; recovery inserts.
5. Simulate Kafka output outage: outbox remains pending, then publishes after
   recovery; verify resends remain identifiable by trade ID.
6. Restart processor with pending outbox and verify delivery.
7. Add a poison-record quarantine path with explicit alerting.
8. Verify high-throughput outbox draining under bounded load.

No test-run or integration pass is claimed merely because code was committed.
Do not deploy unverified changes to a production trading system.
