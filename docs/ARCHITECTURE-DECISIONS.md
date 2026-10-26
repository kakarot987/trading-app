# Architecture decisions and verification register

Status: **proposed documentation baseline**, not an assertion that every behavior exists or has passed load tests.

## System boundary
An event-driven *market analytics* system, not real-money trade execution. External exchange/market adapters → Market Data Producer → Kafka `trade-events` → Trade Processor → PostgreSQL + best-effort Redis → Kafka `processed-trades` → WebSocket Broadcaster → React dashboard. Historical Service serves REST candles/trades using PostgreSQL, Redis and exchange backfill where supported. Current Compose does not run all four services by default.

## ADR-001: Keep four backend services for v1
**Decision:** keep market ingestion, processing, broadcasting and historical queries separate; do not introduce an API gateway, service mesh, AI service, Kubernetes or a new database until there is a requirement. **Reason:** existing system provides real event-driven boundaries and is easier to demonstrate and test without extra operational cost. **Trade-off:** multiple JVM processes increase developer machine load. **Status:** existing boundaries; deployment incomplete.

## ADR-002: Prioritize at-least-once delivery with idempotent writes
**Decision to implement:** use stable event IDs and DB uniqueness; acknowledge Kafka only when the necessary operation succeeds; define retry and dead-letter behavior. Publishing downstream reliably after DB commit requires a documented design (such as a transactional outbox); naive sequential calls are not a distributed transaction. **Current gap:** TradeConsumer starts DB/Redis operations via independent `.subscribe()` and publishes without awaiting persistence success. **Do not claim exactly-once end-to-end delivery** without proof.

## ADR-003: Treat PostgreSQL as historical source of truth
Redis remains an optimization, not the only copy of a trade. Cache failure should not silently invalidate persisted trades. Read/write boundaries are currently shared across services: document ownership of tables and migrations to avoid schema coupling.

## ADR-004: Use WebSocket for live and REST for history
Keep snapshot/history and streaming paths separate. Frontend should deduplicate by stable ID, handle reconnect/replay, bound retained messages, and display stale/offline indicators.

## ADR-005: Prefer cheap local development to premature orchestration
Provide an infrastructure-only Docker mode, with host-run application services, and an optional full Docker Compose mode. Resource budgets are based on measured usage rather than guesses. See [RESOURCE-OPTIMIZATION.md](RESOURCE-OPTIMIZATION.md).

## Verification required before release
- [ ] Trace exchange → Kafka → processor → DB → Kafka → WS with a stable trade ID.
- [ ] Verify duplicate event replay does not create duplicate rows.
- [ ] Verify DB failure does not cause successful downstream publication or acknowledged data loss.
- [ ] Verify broker restart and reconnect behavior.
- [ ] Verify candle aggregation boundaries, late events and backfill consistency.
- [ ] Measure sustained throughput, p50/p95/p99 event latency and CPU/RAM; document load generator, hardware and run dates.
- [ ] Confirm auth/security threat model or clearly label an unauthenticated demo-only deployment.

## Known documentation risks
Earlier docs use terms such as 'production-ready', 'millions of trades' and specific latency numbers. Treat these as aspirations until backed by repeatable benchmarks. See [ROADMAP.md](ROADMAP.md).
