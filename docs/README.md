# AI Crypto Intelligence & Paper Trading Platform

An AI-powered cryptocurrency intelligence platform for live market data, news and event sentiment, directional signals, alerts and simulated paper trading. This project is under development: the architecture and feature descriptions include planned functionality, not production claims.

## Project documentation
- [System architecture and data flows](Architecture.md)
- [Milestones M1–M8 and verified progress](MILESTONES.md)
- [Project structure and technology](PROJECT-STRUCTURE-AND-TECH.md)
- [Prioritized implementation TODO](ROADMAP.md)
- [Docker CPU/RAM optimization](RESOURCE-OPTIMIZATION.md)
- [Architecture decision records](ARCHITECTURE-DECISIONS.md)
- [Android standalone Kotlin/Compose app specification](ANDROID-STANDALONE-APP.md)
- [iOS standalone Swift/SwiftUI app specification](IOS-STANDALONE-APP.md)
- [Mobile API integration plan](MOBILE-ROADMAP.md)
- [M1 observations and resource baselines](milestone-results/M1-BASELINE-RESULTS.md)

## Product vision
- Aggregate market trades and candles, with timestamped news/events.
- Engineer technical and sentiment features; evaluate calibrated directional predictions using walk-forward testing.
- Publish explainable alerts subject to explicit risk validation.
- Simulate long-only spot orders, maintain auditable cash/position ledgers, and compare strategy performance with baselines.
- Serve a React web client; independent Kotlin Android and Swift iOS clients start with dummy JSON and connect through versioned HTTP APIs later.

## Development priorities
M1 measures and reduces resource usage with correctness preserved. M2 hardens event handling, M3 adds technical features, M4 news intelligence, M5 AI predictions, M6 paper execution, M7 evaluation, and M8 portfolio release. The full checklist and acceptance criteria are in [MILESTONES.md](MILESTONES.md).

**Note:** No real-money brokerage execution is part of v1. Do not interpret demo confidence values or paper P&L as live investment recommendations.
