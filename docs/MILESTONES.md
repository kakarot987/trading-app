# AI signals + paper trading — staged milestones

**Evidence-based review of `main` as of 2026-11-01 (commit `6161c571`).** Implementation PRs #5, #7–#12 are merged, but a merged phase is not automatically a fully accepted milestone. Checked tasks distinguish implementation and recorded tests from runtime acceptance; release blockers remain visible. Entries supported by the user's latest review are marked as reported evidence, not independently rerun tests. Suggested 16–20 weeks part-time, adjusted to available hours.

| Phase | Window | Deliverable / exit criterion |
|---|---|---|
| M1 Resource baseline | Weeks 1–2 | Repeatable, working low-resource dev profile; before/after CPU/RAM measurements |
| M2 Ingestion correctness | Weeks 3–4 | Event dedup, persistence/publish coordination, smoke and restart tests |
| M3 Technical features | Weeks 5–6 | Correct OHLCV, RSI/MACD/EMA, timestamped feature sets for BTC/ETH |
| M4 News intelligence | Weeks 7–8 | Provenance-backed event/news sentiment, published & observed times |
| M5 AI signal engine | Weeks 9–11 | Versioned, calibrated 4h directional signals with walk-forward evaluation |
| M6 Paper trading | Weeks 12–14 | Spot-only simulated orders, fees, ledger, reproducible fills and portfolio UI |
| M7 Evaluation | Weeks 15–17 | Backtest and paper-forward tracking; benchmark vs buy-and-hold; failure analysis |
| M8 Portfolio release | Weeks 18–20 | CI, docs, dashboards, security basics, demo and verified case study |
| Mobile A (parallel/after M3) | 1–2 weeks | Independent native Kotlin/Compose Android and Swift/SwiftUI iOS mock-data demos with navigation and typed data |
| Mobile B (after M5/M6 API ready) | 2–3 weeks | Live signals/watchlist/alerts and paper portfolio using same versioned backend contract |

## Current milestone status (reviewed against main on 2026-11-01)

| Milestone | Delivery status | Acceptance status / unresolved proof |
|---|---|---|
| M1 | **Implemented in large part** (baseline, Compose modes, processor changes, build/lint results) | **Open:** equivalent-load peak/CPU/RAM benchmark, Kafka coordinator root cause and confirmed provider key rotation |
| M2 | **Implementation merged** ([PR #5](https://github.com/kakarot987/trading-app/pull/5)) | **Partial:** confirm replay/restart semantics and atomic DB-to-Kafka strategy with fault tests |
| M3 | **Implementation merged** ([PR #7](https://github.com/kakarot987/trading-app/pull/7)) | **Partial:** record deterministic indicator/backfill correctness and time-alignment acceptance evidence |
| M4 | **Implementation merged** ([PR #8](https://github.com/kakarot987/trading-app/pull/8)) | **Partial:** provider compliance, labeled-sample sentiment evaluation and provenance audit |
| M5 | **Research foundation merged** ([PR #9](https://github.com/kakarot987/trading-app/pull/9)) | **Partial:** demonstrate calibrated out-of-sample predictive value; no profit claim |
| M6 | **Paper trading implementation merged** ([PR #10](https://github.com/kakarot987/trading-app/pull/10)) | **Partial:** end-to-end fill/ledger/restart and UI acceptance evidence |
| M7 | **Evaluation implementation merged** ([PR #11](https://github.com/kakarot987/trading-app/pull/11)) | **Partial:** actual persisted fills and comparable strategy benchmarks required |
| M8 | **Local release hardening merged** ([PR #12](https://github.com/kakarot987/trading-app/pull/12)) | **Not release-ready:** auth ownership, browser smoke, load/recovery, provider/legal and release assets outstanding |

**Meaning:** A checked implementation task documents code or recorded evidence; an entire milestone is marked complete only when its exit criteria are demonstrated. Android/iOS apps are planned in independent repositories and are not marked complete here. [M8 release evidence](milestone-results/M8-RELEASE-READINESS.md) explicitly limits deployment to a private simulated trading demo.

## Execution TODO
### M1 — resource optimization and stability (reviewed 2026-10-28)
**Status: substantial progress, still in progress.** Evidence: [M1 resource and recovery results](milestone-results/M1-BASELINE-RESULTS.md), [local development guide](LOCAL-DEVELOPMENT.md), [resource baseline CSV](../m1-resource-baseline.csv), Compose configuration and trade-processor implementation on `milestone/M1-resource-optimization`. Checked items have specific evidence, not a claim that all edge cases were tested.

- [x] Capture infrastructure-only and seven-container Docker snapshots, including 0/5/10/15-minute per-container sampling.
- [x] Configure infrastructure-only and optional `producer` / `tools` Docker profiles, and document seven-container backend launch instructions.
- [x] Configure and verify internal/host Kafka listeners and live producer → Kafka → trade processor → PostgreSQL ingestion.
- [x] Add Git ignore rules and remove previously tracked generated build/IDE outputs as part of baseline cleanup.
- [x] Replace fire-and-forget DB and Redis operations with completion-aware processing, and wait for processed-event Kafka send confirmation (code-level verification).
- [x] Add duplicate-safe insert using `ON CONFLICT (trade_id) DO NOTHING` and inserted/duplicate counters (does not prove every incoming event has a stable trade ID).
- [x] Limit Kafka poll batch size and add a retry/error handler; test recovery from an observed consumer backlog.
- [x] Record trade-processor Gradle compilation and test-task pass, Kafka backlog recovery and live DB growth (per results log).
- [x] Add local development guide and documented Docker modes.
- [x] Record Gitleaks scan of 258 commits with zero findings in the reported scanner scope (M8 release evidence).
- [ ] Confirm Alpha Vantage API key revocation/rotation; audit any exposures not covered by that scan.
- [x] Record frontend lint/build and TypeScript/Vite production-build passes (see M1 results, October 28).
- [x] Record backend Gradle test pass reported during M8 under UTC JVM setting (see M8 release evidence); this does not establish comprehensive test coverage.
- [ ] Demonstrate stable IDs, redelivery/restart fault tests, bounded consumer lag and resilient retry/recovery; also tracked under M2.
- [ ] Establish repeatable fixed-rate workload with sustained throughput and peak CPU/RAM, including host WSL/Docker overhead.
- [ ] Profile broker coordinator timeouts and processor allocation/GC under equivalent load; verify no recurring instability.
- [ ] Produce controlled before/after performance and latency comparison without regressions or OOMs.

**Measured observation (not equivalent-workload proof):** seven-container memory sum changed from 2,945.1 MiB to 2,308.5 MiB in separate snapshots (~21.6% lower). A recorded five-minute run showed 383.6 MiB trade-processor memory and later 15-minute sampling exists in the CSV. A backlog of 370,000+ messages recovered; the latest reported live-ingestion lag was 1 message. The underlying Kafka coordinator failure remains unconfirmed. **M1 exit criterion is not yet met** because controlled performance and failure testing are outstanding.

### M2 — ingestion correctness
**Implementation:** merged in [PR #5](https://github.com/kakarot987/trading-app/pull/5). **Acceptance:** partial.

- [x] Implement stable trade identifiers, duplicate-safe persistence and Kafka replay/idempotency foundations.
- [x] Implement PostgreSQL outbox / durable publish coordination and retry strategy.
- [x] Add backend/frontend CI workflows; reported backend pipeline passes (review evidence).
- [ ] Demonstrate repeatable end-to-end ingestion test with durable assertions across Kafka → DB → downstream.
- [ ] Verify crash windows, process/broker restart, poison-record treatment and retry exhaustion under fault injection.
- [ ] Validate bounded consumer lag and sustained throughput under controlled load.

### M3 — technical market features
**Implementation:** merged in [PR #7](https://github.com/kakarot987/trading-app/pull/7). **Acceptance:** partial.

- [x] Define BTCUSDT/ETHUSDT 4-hour feature pipeline and UTC candle alignment.
- [x] Implement OHLCV aggregation and RSI/EMA/MACD technical indicators with deterministic test fixtures.
- [x] Implement feature snapshots with versioning and timestamp/provenance metadata.
- [ ] Demonstrate end-to-end accuracy for missing/late bars, gaps, backfill and cross-source timestamp alignment.
- [ ] Record repeatable acceptance results for time cutoffs and feature availability (no look-ahead leakage).

### M4 — news intelligence
**Implementation:** merged in [PR #8](https://github.com/kakarot987/trading-app/pull/8). **Acceptance:** partial.

- [x] Integrate CryptoPanic news adapter and record article source/publication/observation provenance.
- [x] Implement article deduplication, storage and associated integration tests.
- [x] Implement sentiment evaluation test foundations.
- [ ] Confirm provider license/terms, quota, attribution and operational approval.
- [ ] Validate symbol relevance, provenance and sentiment quality on representative labeled samples.

### M5 — AI signal engine
**Implementation:** merged in [PR #9](https://github.com/kakarot987/trading-app/pull/9). **Acceptance:** partial.

- [x] Implement technical / no-trade research baselines and logistic regression baseline.
- [x] Implement chronological walk-forward evaluation and leakage-focused tests.
- [x] Implement probability calibration mechanics and versioned signal contracts.
- [ ] Demonstrate calibration quality and out-of-sample predictive skill on untouched data, with stated sample size, period and baselines.
- [ ] Validate signal expiry, risk thresholds and model/data version traceability in end-to-end operation.
- [ ] Consider boosted-tree models only if baseline evaluation justifies them.

### M6 — spot paper trading
**Implementation:** merged in [PR #10](https://github.com/kakarot987/trading-app/pull/10). **Acceptance:** partial.

- [x] Implement decimal money/quantity rules and simulated order lifecycle.
- [x] Implement eligible-tick fill engine, fees/slippage handling, idempotent ledger and database integration tests.
- [x] Implement portfolio / order API and React portfolio view.
- [x] Record local UI observation: synthetic 100 USDT balance, staged BTCUSDT order, zero fills and visible limitations (per review input; full browser checklist remains open).
- [ ] Demonstrate complete persisted fill → ledger → portfolio reconciliation under restart and replay.
- [ ] Verify cash conservation, duplicate/rejected order handling and deterministic forward-tick execution.

### M7 — strategy performance and evaluation
**Implementation:** merged in [PR #11](https://github.com/kakarot987/trading-app/pull/11). **Acceptance:** partial.

- [x] Implement P&L, drawdown/risk, exposure, turnover and comparison/benchmark calculation components.
- [x] Implement strategy evaluation provenance and portfolio UI integration.
- [ ] Demonstrate metrics on genuine persisted fills and timestamp-consistent market marks.
- [ ] Compare net strategy performance with buy-and-hold and simple-rule baselines on the same period, including fees.
- [ ] Report drawdown/regime failures, model drift and backtest-versus-forward paper performance separately.

### M8 — private demo hardening and portfolio release
**Implementation:** merged in [PR #12](https://github.com/kakarot987/trading-app/pull/12). **Release acceptance:** open.

- [x] Add backend/frontend CI workflows and automated test foundations; record reported local Gradle tests.
- [x] Implement local paper API access guard and record no-token 401 / valid-token 200 checks.
- [x] Verify health/info endpoints and restricted JVM metrics access in local acceptance checks.
- [x] Record Gitleaks Git-history scan pass for tested scope and Compose localhost-binding configuration.
- [x] Verify paper API and visible UI data for a synthetic wallet in local demo mode (partial browser acceptance).
- [ ] Complete browser acceptance: console errors, network host, refresh, no-order-submit behavior and recorded screenshots; [smoke checklist](milestone-results/M8-BROWSER-SMOKE.md) remains unexecuted as a whole.
- [ ] Verify actual recreated container port binding, secure identity + wallet ownership authorization, or explicitly restrict deployment to private local demo.
- [ ] Validate recovery/load tests, resource benchmarks and operational monitoring/alerting.
- [ ] Confirm provider licensing, API credential rotation, security/privacy documentation and log redaction.
- [ ] Publish reviewed screenshots/demo, architectural decision records, runbooks and honest benchmark evidence.
- [ ] Tag a release only when scoped acceptance gates pass; no public production-ready claim.

## Mobile stage gates
- Mock UI stage: Android and iOS screens run against mock APIs on both platforms. Claim **prototype experience**, not published apps.
- Live stage: contract tests pass against shared backend; tokens/session, reconnect, offline/stale data and notification policies tested.
- Optional release stage: signed builds, device QA, accessibility, privacy and app-store compliance.

Each milestone PR should document outcome, test evidence, CPU/RAM impact and remaining risk.
