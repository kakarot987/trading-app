# Delivery roadmap — real-time market analytics platform

> Status: planning baseline (26 October 2026). The application reportedly works locally; this document does not assert that CI, load tests or full Docker deployment have passed.
> Scope: **market-data analytics and visualization**, not live brokerage/order execution.

## Definition of v1.0
- Reproducible local setup (document host resource requirements).
- Live market events flow from an exchange adapter through Kafka, processing, persistence, WebSocket, and the React UI.
- Historical candles and trades load from a documented REST API.
- Duplicate delivery and transient dependency failure are handled predictably.
- Automated tests and build checks run in CI.
- Actual latency, throughput and container resource consumption are measured and reported.
- README includes architecture, screenshots, demo instructions, limitations and attribution.

## Prioritized TODO
### P0 — correctness and reproducibility
- [ ] Capture baseline application startup and Docker CPU/RAM numbers; see [RESOURCE-OPTIMIZATION.md](RESOURCE-OPTIMIZATION.md).
- [ ] Document supported dev workflow: infrastructure in Docker, backend/frontend on host (lightweight), plus an optional full Compose profile.
- [ ] Review `trade-processor/.../TradeConsumer.kt`: DB/Redis `.subscribe()` runs asynchronously and processed Kafka publishing does not wait for DB save. Define failure and acknowledgment semantics; implement coordinated flow.
- [ ] Make trade persistence idempotent by stable event/trade ID, with uniqueness enforced by DB and tests.
- [ ] Verify Kafka retries, offset commits and poison-message handling before describing any delivery guarantee.
- [ ] Remove tracked build outputs/IDE caches from Git index and add appropriate root ignore patterns; preserve Gradle wrapper and source.
- [ ] Audit tracked `.env` and all secrets; replace private settings with example files and rotate any exposed credentials.
- [ ] Add an automated smoke test validating exchange/mock producer → Kafka → processor → PostgreSQL → WebSocket.

### P1 — functionality and quality
- [ ] Align documented JSON formats with actual shared Kotlin DTOs and React TypeScript types.
- [ ] Version event schemas and define compatibility policy.
- [ ] Verify and document historical backfill, OHLCV candle boundaries, timezone, gaps, duplicate/late events, cache invalidation.
- [ ] Replace source-code mock toggle with `VITE_*` config and explicitly label demo balances/signals as simulated.
- [ ] Consolidate duplicated trade hooks, REST clients and stores once integration tests guard behavior.
- [ ] Add WS reconnection, resubscription, stale data and bounded buffering tests.
- [ ] Add unit, Testcontainers integration and one browser E2E test.
- [ ] Add GitHub Actions build, lint, test and dependency checks.
- [ ] Add Actuator/Micrometer metrics, structured logs, Kafka consumer lag dashboard.

### P2 — evidence and portfolio
- [ ] Benchmark sustained rate, event latency p50/p95/p99, and container peak/steady CPU/RAM with test hardware and workload disclosed.
- [ ] Publish architecture diagram, ADRs and failure/recovery walkthrough.
- [ ] Create repeatable demo mode, screenshots and short screen recording.
- [ ] Perform basic security/accessibility review.
- [ ] Publish an honest portfolio case study and tag v1.0.

## Milestones and exit criteria
| Milestone | Suggested window | Exit criteria |
|---|---|---|
| M1: Baseline and cleanup | Week 1 | Metrics recorded; tracked artifacts cleaned; setup documented |
| M2: End-to-end delivery | Weeks 2–3 | Test event visible in DB and UI; one-command supported setup |
| M3: Reliable processing | Weeks 4–5 | Duplicate/retry/restart tests pass; failed writes are not silently published as successful |
| M4: Historical + frontend | Weeks 5–6 | Candles, symbols, live/historical integration and offline UI verified |
| M5: Test + observe + optimize | Weeks 7–8 | CI green; resource comparison and measured latency published |
| M6: Portfolio release | Weeks 9–10 | README, ADRs, demo assets, reproducible instructions and v1.0 tag |

## Working agreement
Implement one milestone at a time on feature branches; make small PRs with tests and measurements. Document what was implemented vs proposed. Do not add Kubernetes, AI prediction, broker integration, or more services to v1.0 merely for presentation.
