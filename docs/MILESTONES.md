# AI signals + paper trading — staged milestones

**Evidence-based review of `main` as of 2026-11-01 (commit `6161c571`).** Implementation PRs #5, #7–#12 are merged, but a merged phase is not automatically a fully accepted milestone. Only verified individual tasks are checked; release blockers remain visible. Suggested 16–20 weeks part-time, adjusted to available hours.

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
- [ ] Verify previously exposed Alpha Vantage key was revoked/rotated and complete Git-history secret audit.
- [x] Record frontend lint/build and TypeScript/Vite production-build passes (see M1 results, October 28).
- [x] Record backend Gradle test pass reported during M8 under UTC JVM setting (see M8 release evidence); this does not establish comprehensive test coverage.
- [ ] Demonstrate stable IDs, redelivery/restart fault tests, bounded consumer lag and resilient retry/recovery; also tracked under M2.
- [ ] Establish repeatable fixed-rate workload with sustained throughput and peak CPU/RAM, including host WSL/Docker overhead.
- [ ] Profile broker coordinator timeouts and processor allocation/GC under equivalent load; verify no recurring instability.
- [ ] Produce controlled before/after performance and latency comparison without regressions or OOMs.

**Measured observation (not equivalent-workload proof):** seven-container memory sum changed from 2,945.1 MiB to 2,308.5 MiB in separate snapshots (~21.6% lower). A recorded five-minute run showed 383.6 MiB trade-processor memory and later 15-minute sampling exists in the CSV. A backlog of 370,000+ messages recovered; the latest reported live-ingestion lag was 1 message. The underlying Kafka coordinator failure remains unconfirmed. **M1 exit criterion is not yet met** because controlled performance and failure testing are outstanding.

### M2
- [x] Merge M2 ingestion-correctness and recovery implementation ([PR #5](https://github.com/kakarot987/trading-app/pull/5)); treat acceptance of all crash windows separately.
- [x] Review completion-aware persistence and duplicate-safe insert in TradeConsumer/TradeRepository on the resource optimization branch; implementation inspected, not failure-tested.
- [ ] Implement stable trade ID uniqueness, idempotent replay and failure tests.
- [ ] Document Kafka commit/ack/retry/DLQ rules, durability and output publishing strategy.
- [ ] Verify CI workflows and one reproducible end-to-end test on current main; PR merge alone is not evidence of a working CI pipeline.

### M3
- [x] Merge UTC 4h technical-feature and backfill implementation ([PR #7](https://github.com/kakarot987/trading-app/pull/7)); independently verify indicator fixture and live acceptance before signing off M3.
- [ ] Pick BTCUSDT and ETHUSDT spot and 4h forecast horizon.
- [ ] Test OHLCV aggregation including missing/late bars, time zone and interval alignment.
- [ ] Implement RSI, EMA, MACD, volatility and volume features with deterministic fixtures.
- [ ] Store feature data availability cutoff and version.

### M4
- [x] Merge provenance-backed news foundation and offline acceptance work ([PR #8](https://github.com/kakarot987/trading-app/pull/8)); validation and provider compliance remain open.
- [ ] Choose licensed news/event feeds and rate/cost budgets.
- [ ] Normalize event type, symbol relevance, source, published_at and observed_at.
- [ ] Deduplicate and store article references with timestamps.
- [ ] Benchmark sentiment extraction against a labeled sample; avoid LLM price guessing.

### M5
- [x] Merge leakage-aware research signal foundations and offline acceptance work ([PR #9](https://github.com/kakarot987/trading-app/pull/9)); predictive calibration and trading performance remain unverified.
- [ ] Implement no-trade and simple technical baselines; then logistic regression.
- [ ] Add walk-forward splits, leakage checks and untouched evaluation window.
- [ ] Consider boosted trees only if baselines justify complexity.
- [ ] Calibrate probability and save model/data version with every prediction.
- [ ] Implement signal expiry, evidence and risk policy; expose versioned REST/WS DTOs.

### M6
- [x] Merge spot paper-trading ledger, API and portfolio UI implementation ([PR #10](https://github.com/kakarot987/trading-app/pull/10)); do not infer executed fills from created orders.
- [ ] Define paper wallet and precise Decimal currency rules.
- [ ] Define order lifecycle, fees, spread, slippage, reject states and future-tick fills.
- [ ] Persist immutable fill/ledger with idempotent order and fill keys.
- [ ] Expose paper portfolio and order history to web UI.
- [ ] Verify deterministic replay, balance invariants and restart recovery.

### M7
- [x] Merge paper evaluation metrics/provenance and portfolio UI ([PR #11](https://github.com/kakarot987/trading-app/pull/11)); actual strategy outcomes and comparable benchmark evidence remain open.
- [ ] Measure net P&L, max drawdown, exposure, turnover, win rate and risk-adjusted performance.
- [ ] Compare to buy-and-hold and strategy baselines on identical periods with costs.
- [ ] Report strategy failures, drawdown periods, model drift and paper/live data gaps.
- [ ] Distinguish backtest results from genuine forward paper trading.

### M8
- [x] Merge localhost release hardening, paper API token guard and release-verification scripts ([PR #12](https://github.com/kakarot987/trading-app/pull/12)).
- [x] Record reported backend Gradle tests, secret scanner pass (for scanned scope), health/info/metrics checks and local API access checks in [M8 release readiness](milestone-results/M8-RELEASE-READINESS.md).
- [ ] Execute and record [M8 manual browser smoke checklist](milestone-results/M8-BROWSER-SMOKE.md); current document explicitly says not yet executed.
- [ ] Verify multi-user wallet ownership authorization or constrain to private local demo, recreate containers and confirm localhost bindings.
- [ ] Add tests (unit, Testcontainers, browser/mobile smoke), CI and observability.
- [ ] Security/privacy review, provider/API licenses, environment configuration.
- [ ] README demos/screenshots, architectural ADRs and honest benchmark tables.
- [ ] Tag release once criteria are demonstrated, not by calendar alone.

## Mobile stage gates
- Mock UI stage: Android and iOS screens run against mock APIs on both platforms. Claim **prototype experience**, not published apps.
- Live stage: contract tests pass against shared backend; tokens/session, reconnect, offline/stale data and notification policies tested.
- Optional release stage: signed builds, device QA, accessibility, privacy and app-store compliance.

Each milestone PR should document outcome, test evidence, CPU/RAM impact and remaining risk.
