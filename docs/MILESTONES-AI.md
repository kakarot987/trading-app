# AI signals + paper trading — staged milestones

**Proposal, not claimed completed work.** Updated 2026-10-27 from the latest `main` branch after the M1 cleanup merge. Suggested 16–20 weeks part-time, adjusted to available hours. The older [MILESTONES.md](MILESTONES.md) describes the narrower historical analytics scope; this plan supersedes it **for future planning**.

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
| Mobile A (parallel/after M3) | 1–2 weeks | Mock API Expo demo on Android/iOS simulator/device with navigation and typed data |
| Mobile B (after M5/M6 API ready) | 2–3 weeks | Live signals/watchlist/alerts and paper portfolio using same versioned backend contract |

## Execution TODO
### M1 — verified progress against latest `main` (2026-10-27)
Evidence: [M1 baseline results](milestone-results/M1-BASELINE-RESULTS.md), root `.gitignore`, and Docker Compose. **Checked = specific observed subtask**, not the entire milestone.

- [x] Record initial per-container Docker CPU/RAM snapshots (infra-only, infra+producer, seven-container stack); snapshots are **not** sustained/peak benchmarks.
- [x] Configure and validate infrastructure-only Compose selection plus opt-in `producer`/`tools` profiles; full-stack running was also observed later, but not a comprehensive automated smoke test.
- [x] Configure and verify Kafka internal `kafka:9092` and host `localhost:29092` advertised listeners, and confirm consumer group participation.
- [x] Confirm live producer → Kafka → trade processor → PostgreSQL persistence with increasing trade row count and zero consumer lag in a captured snapshot.
- [x] Add root ignore rules for Gradle/build/IDE artifacts and environment files; tracked artifact cleanup was introduced in merged M1 baseline.
- [ ] Verify repository hygiene is fully complete and no sensitive values were previously committed; review and rotate secrets if needed.
- [ ] Update local development instructions to reflect current Compose profiles and seven-container results.
- [ ] Run and record reproducible backend build/test results plus frontend lint/build.
- [ ] Measure controlled sustained/peak Docker + WSL host CPU/RAM with comparable workloads and durations.
- [ ] Profile high-memory containers (notably trade processor and Kafka in the recorded snapshot); apply and verify one optimization at a time.
- [ ] Publish measured before/after CPU/RAM and latency comparison; confirm correctness and no OOM/reconnect loop.

**Observed snapshot (not an optimization outcome):** seven containers total approximately **2,945 MiB (2.88 GiB)** according to the recorded Docker stats sum; excludes host WSL/Docker overhead. M1 remains **in progress**.

### M2
- [ ] Verify fixes in latest TradeConsumer and Postgres schema rather than assuming old findings persist.
- [ ] Implement stable trade ID uniqueness, idempotent replay and failure tests.
- [ ] Document Kafka commit/ack/retry/DLQ rules, durability and output publishing strategy.
- [ ] Add CI and one reproducible end-to-end test.

### M3
- [ ] Pick BTCUSDT and ETHUSDT spot and 4h forecast horizon.
- [ ] Test OHLCV aggregation including missing/late bars, time zone and interval alignment.
- [ ] Implement RSI, EMA, MACD, volatility and volume features with deterministic fixtures.
- [ ] Store feature data availability cutoff and version.

### M4
- [ ] Choose licensed news/event feeds and rate/cost budgets.
- [ ] Normalize event type, symbol relevance, source, published_at and observed_at.
- [ ] Deduplicate and store article references with timestamps.
- [ ] Benchmark sentiment extraction against a labeled sample; avoid LLM price guessing.

### M5
- [ ] Implement no-trade and simple technical baselines; then logistic regression.
- [ ] Add walk-forward splits, leakage checks and untouched evaluation window.
- [ ] Consider boosted trees only if baselines justify complexity.
- [ ] Calibrate probability and save model/data version with every prediction.
- [ ] Implement signal expiry, evidence and risk policy; expose versioned REST/WS DTOs.

### M6
- [ ] Define paper wallet and precise Decimal currency rules.
- [ ] Define order lifecycle, fees, spread, slippage, reject states and future-tick fills.
- [ ] Persist immutable fill/ledger with idempotent order and fill keys.
- [ ] Expose paper portfolio and order history to web UI.
- [ ] Verify deterministic replay, balance invariants and restart recovery.

### M7
- [ ] Measure net P&L, max drawdown, exposure, turnover, win rate and risk-adjusted performance.
- [ ] Compare to buy-and-hold and strategy baselines on identical periods with costs.
- [ ] Report strategy failures, drawdown periods, model drift and paper/live data gaps.
- [ ] Distinguish backtest results from genuine forward paper trading.

### M8
- [ ] Add tests (unit, Testcontainers, browser/mobile smoke), CI and observability.
- [ ] Security/privacy review, provider/API licenses, environment configuration.
- [ ] README demos/screenshots, architectural ADRs and honest benchmark tables.
- [ ] Tag release once criteria are demonstrated, not by calendar alone.

## Mobile stage gates
- Mock UI stage: Android and iOS screens run against mock APIs on both platforms. Claim **prototype experience**, not published apps.
- Live stage: contract tests pass against shared backend; tokens/session, reconnect, offline/stale data and notification policies tested.
- Optional release stage: signed builds, device QA, accessibility, privacy and app-store compliance.

Each milestone PR should document outcome, test evidence, CPU/RAM impact and remaining risk.
