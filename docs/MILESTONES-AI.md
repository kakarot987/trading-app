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
### M1
- [ ] Reconcile latest `main` cleanup with resource optimization TODO before repeating completed work.
- [ ] Record `docker stats --no-stream`, heap/native memory and workload; profile Kafka UI, Kafka and each JVM.
- [ ] Establish and document infra-only Docker, host-run JVM, optional AI inference.
- [ ] Fix advertised Kafka listeners for host/container clients; verify smoke flow.
- [ ] Publish measured before/after memory, CPU and latency data; no arbitrary resource cap target.

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
