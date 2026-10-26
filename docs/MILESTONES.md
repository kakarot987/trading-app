# Delivery milestones — v1.0

**Status:** planned, not completed or verified. Estimated 8–10 weeks part-time; order and exit criteria matter more than calendar estimates.

| ID | Milestone | Target | Definition of done |
|---|---|---|---|
| M1 | Baseline, Docker profiling and repository hygiene | Week 1 | Record CPU/RAM baseline; write reproducible dev instructions; clean generated tracked files |
| M2 | Working end-to-end demo | Weeks 2–3 | From market source to Kafka to DB and browser using a stable trade ID; repeatable smoke test |
| M3 | Event processing reliability | Weeks 4–5 | Duplicate ID, PostgreSQL failure, retry and Kafka restart integration tests pass |
| M4 | Historical analytics and UI | Weeks 5–6 | Historical candles, symbol selection, live/historical sync and stale/offline handling verified |
| M5 | CI, observability and optimization | Weeks 7–8 | CI checks green; baseline-vs-optimized metrics reported on equivalent workloads |
| M6 | Portfolio-ready release | Weeks 9–10 | Demo, screenshots, architecture, ADRs, README, limitations and v1.0 tag published |

## Task breakdown by milestone

### M1 — Baseline and cleanup
- [ ] Run `docker stats --no-stream` at idle and under defined load; record per-container figures.
- [ ] Document infrastructure-only Docker mode and current full-stack Compose limitations.
- [ ] Verify all environment variables and broker listeners.
- [ ] Untrack build artifacts and IDE files; review committed `.env`.
- [ ] Capture current build/test pass/fail baseline.

### M2 — End-to-end demo
- [ ] Define expected exchange-event schema, topic, IDs, and routing.
- [ ] Validate producer to consumer to DB path.
- [ ] Validate broadcaster to frontend path.
- [ ] Verify historical REST endpoint against stored data.
- [ ] Add an automated smoke test and clear start-up walkthrough.

### M3 — Reliability
- [ ] Remove independent fire-and-forget persistence subscriptions.
- [ ] Define DB commit / downstream publish strategy and acknowledgment policy.
- [ ] Implement stable ID deduplication and unique constraint.
- [ ] Test retries, DLQ and process/broker restarts.

### M4 — Frontend and history
- [ ] Normalize typed DTOs and application configuration.
- [ ] Verify candle aggregation including gaps, boundaries and time zones.
- [ ] Add loading, stale, offline and failed request UX.
- [ ] Ensure WebSocket reconnection/resubscription and bounded state.

### M5 — Operational quality
- [ ] Add unit/integration/E2E checks and GitHub Actions.
- [ ] Add metrics/logging and lag dashboards.
- [ ] Profile JVM, Kafka and other container usage.
- [ ] Apply low-risk optimizations one at a time; rerun correctness and performance checks.

### M6 — Portfolio release
- [ ] Update README with truthful implemented-vs-target features.
- [ ] Include tested Mermaid architecture and ADRs.
- [ ] Record demo screenshots/video.
- [ ] Publish reproducible setup instructions, known trade-offs and limitations.
- [ ] Tag v1.0 after acceptance criteria pass.

## Review template
For each milestone PR record: **what changed**, **how tested**, **screenshots or metrics**, **known risks**, and **next task**.

See also [prioritized roadmap](ROADMAP.md), [target architecture](TARGET-ARCHITECTURE.md) and [resource optimization](RESOURCE-OPTIMIZATION.md).
