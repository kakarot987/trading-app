# Android Crypto Signals Demo — standalone repository specification

**Status:** planning document. Create a **separate Android GitHub repository** later; this document does not create or modify an Android application. Build a genuine native Android demo before claiming Kotlin/Android experience.

## Purpose
A read-only and simulated crypto intelligence Android app showcasing Kotlin, Jetpack Compose, Android architecture, networking, offline data handling and UI tests. Run independently with deterministic local JSON. Later integrate with trading-app's versioned HTTP API **without replacing the entire UI**. No broker connections or real-money orders.

## Technology
- Kotlin, Jetpack Compose, Material 3, Navigation Compose.
- ViewModel, Coroutines / Flow, immutable UI state, Repository pattern.
- Retrofit or Ktor Client with kotlinx.serialization; OkHttp logging only in debug.
- Hilt for dependency injection **only when complexity warrants it**; otherwise manual constructor injection.
- Room or DataStore for favorites/preferences and optional last-known snapshots.
- JUnit, kotlinx-coroutines-test, MockWebServer, Compose UI tests.
- Gradle Kotlin DSL, GitHub Actions for debug build/lint/tests.

Versions must be checked at implementation time. Do not add all dependencies before their corresponding feature needs them.

## Proposed repository
```text
crypto-signals-android/
├── app/src/main/java/com/example/cryptosignals/
│   ├── MainActivity.kt
│   ├── navigation/
│   ├── core/{model,network,storage,ui}/
│   ├── data/{api,mock,repository}/
│   └── feature/{watchlist,assetdetail,signals,alerts,paperportfolio,settings}/
├── app/src/main/assets/mock/{assets,signals,alerts,portfolio}.json
├── app/src/test/
├── app/src/androidTest/
├── docs/{ARCHITECTURE,API-CONTRACT,SCREENSHOTS}.md
└── README.md
```

## Screens (MVP)
1. Watchlist: symbol, latest **mock** price/change, search and favorites.
2. Asset detail: simulated price/candle chart, selected interval and freshness label.
3. Signals: directional label, **illustrative** probability, time horizon, model version and reason.
4. Alerts: severity, asset, event time, seen/unseen.
5. Paper portfolio: simulated account equity, positions, order history and P&L (no live trading).
6. Settings: Mock / Remote API mode, units, data provenance and disclaimer.

## API contract — mock-first, replaceable
Create `CryptoApi` repository interface with `FakeCryptoApi` reading bundled JSON and `HttpCryptoApi` calling a configurable HTTPS base URL. The UI should not know which adapter is active.

Target endpoints (not available in trading-app yet):
- `GET /v1/assets`
- `GET /v1/assets/{symbol}/candles?interval=1h`
- `GET /v1/signals?symbol=BTCUSDT`
- `GET /v1/alerts`
- `GET /v1/paper/portfolio`
- `GET /v1/paper/orders`

Put sample responses in source-controlled JSON fixtures, e.g. `signals.json`:
```json
{
  "data": [{
    "id": "demo-signal-001",
    "symbol": "BTCUSDT",
    "direction": "BULLISH",
    "probability": 0.68,
    "horizonMinutes": 240,
    "createdAt": "2026-10-01T12:00:00Z",
    "expiresAt": "2026-10-01T16:00:00Z",
    "modelVersion": "demo-v0",
    "explanation": "Illustrative example, not model output",
    "mode": "MOCK"
  }],
  "asOf": "2026-10-01T12:00:00Z",
  "nextCursor": null
}
```
The fixture is fictional and must be visibly labeled MOCK. Add similar JSON for assets, candles, alerts and portfolio. Do not use a random online API as the authoritative future schema: map its response through a data-source adapter.

## Implementation steps and acceptance criteria
### A1: UI skeleton
- [ ] Create Android Studio project, Compose theme and navigation.
- [ ] Implement watchlist, asset detail, signal, alert and portfolio screens with previews.
- [ ] Use fake repositories and bundled JSON; no network necessary.
- [ ] Add loading, empty, error and offline states.

**Done:** debug build runs on Android emulator or hardware, and all screens navigate correctly.

### A2: Architecture and API
- [ ] Make typed `CryptoApi` interface, JSON parsing, DTO → domain mapping, repository and Flow/ViewModel.
- [ ] Unit-test parsing, state changes and error handling; test one Compose navigation path.
- [ ] Add mock server for HTTP contract tests, independently of backend availability.

**Done:** switching adapter requires no screen changes; unit/UI checks pass.

### A3: Optional public-data mode
- [ ] Use a documented **public market-data API** (e.g., an exchange's publicly documented read-only endpoints) behind a separate adapter; check uptime, rate limits, CORS/client terms, regional availability and licensing.
- [ ] Keep news/AI signals/portfolio mocked until a real compatible backend exists.
- [ ] Handle throttling, disconnection and expired/stale data, without embedding provider secrets in the APK.

**Done:** live quote view is clearly distinguished from mock signals and paper equity.

### A4: trading-app integration (later)
- [ ] Adopt versioned OpenAPI schemas and shared error/pagination conventions.
- [ ] Implement `HttpCryptoApi` against trading-app gateway/BFF, not internal Kafka or database.
- [ ] Add authenticated paper account/session only when server supports it; no credentials in source.
- [ ] Add WebSocket/notifications only after correct reconnect, authorization and dedup rules are defined.

**Done:** app works against backend contract tests and retains offline mock demo mode.

## Resume evidence
Record screenshots, emulator/device test evidence, APK/build workflow, code architecture and unit/UI test results. Claim **native Android/Kotlin/Compose** only after building/testing it. An independent dummy demo is legitimate portfolio work; call it a demo, not production trading.
