# iOS Crypto Signals Demo — standalone repository specification

**Status:** planning document. Build in a **separate iOS GitHub repository**; no iOS app has been created. This is an independent, native SwiftUI portfolio app with local JSON first, then a future trading-app HTTP integration.

## Purpose
Demonstrate Swift, SwiftUI, MVVM-style presentation, async/await networking, structured concurrency, JSON decoding, Swift Charts and testable data boundaries. It should not depend on Android or the web frontend. Simulated signals and paper balances must always be labeled as demo data.

## Technology
- Swift + SwiftUI; NavigationStack, Observation (`@Observable`) on supported deployment targets or ObservableObject where needed.
- URLSession + async/await; Codable and ISO-8601 date parsing.
- Swift Charts for illustrative candles/equity curves (choose a suitable chart implementation after validation).
- Simple protocol-driven dependency injection (`CryptoAPIClient`); avoid extra architecture frameworks initially.
- UserDefaults / SwiftData when local favorites and cache require persistence.
- XCTest/Swift Testing and XCUITest for model/decoding and navigation checks.
- Xcode, Swift Package Manager, GitHub Actions macOS runner if budget permits.
- **Build requirement:** Xcode/macOS for native app builds and iOS Simulator; a physical iPhone is optional for early development.

## Proposed repository
```text
crypto-signals-ios/
├── CryptoSignals/
│   ├── CryptoSignalsApp.swift
│   ├── Core/{Models,Networking,Storage,UI}/
│   ├── Data/{Mock,Remote,Repositories}/
│   ├── Features/{Watchlist,AssetDetail,Signals,Alerts,PaperPortfolio,Settings}/
│   └── Resources/Mock/{assets,signals,alerts,portfolio}.json
├── CryptoSignalsTests/
├── CryptoSignalsUITests/
├── docs/{ARCHITECTURE,API-CONTRACT,SCREENSHOTS}.md
├── CryptoSignals.xcodeproj
└── README.md
```

## Screens
- Watchlist: search, favorites, fictional/mock market prices.
- Asset detail: price information, example candles, time range and data freshness.
- AI signals: direction, horizon, evidence/provenance, model version; probability is a **fictional demo score** until validated.
- Alerts: timestamped severity and symbol, notification settings UI (local notifications optional).
- Paper portfolio: simulated equity, positions, fills and performance.
- Settings: Mock/Remote environment, privacy, legal/demo labeling.

## Sample local JSON
Use the same **logical API DTO contract** as Android, but ship a **separate fixture file** in the iOS repository. Example `signals.json`:
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

### API boundary
Use `protocol CryptoAPIClient` with `MockCryptoAPIClient` (bundle JSON) and `RemoteCryptoAPIClient` (URLSession). Views consume typed domain models via a view model, never direct networking. Future target endpoints:
`GET /v1/assets`, `GET /v1/assets/{symbol}/candles`, `GET /v1/signals`, `GET /v1/alerts`, `GET /v1/paper/portfolio`, `GET /v1/paper/orders`.
These URLs are **proposed** and not asserted to exist today.

## Stage checklist
### I1: Native UX and mock fixtures
- [ ] Create Xcode SwiftUI project and app navigation.
- [ ] Implement watchlist, asset, signals, alerts and paper portfolio with local JSON.
- [ ] Add accessible dynamic text, VoiceOver labels and loading/empty/error states.
- [ ] Test on iOS Simulator.

**Done:** screenshots and a reproducible simulator build, navigation works offline.

### I2: Architecture and quality
- [ ] Add Codable DTOs, domain mapping, `CryptoAPIClient` abstraction.
- [ ] Add tests for parsing, date/time display, portfolio mock state and error handling.
- [ ] Add one UI smoke test; make mock client deterministic.
- [ ] Configure scheme/build checks on a compatible macOS runner if available.

**Done:** protocol-based mock/remote swap leaves views unchanged, tests pass.

### I3: Optional public quotes
- [ ] Use a documented read-only public market-data API behind separate adapter, respecting limits/terms.
- [ ] Distinguish real market quotes from mock AI confidence and account information.
- [ ] Avoid storing API credentials in the app; disable remote when offline and show stale-state UI.

### I4: trading-app integration (later)
- [ ] Match versioned OpenAPI contract, errors and pagination.
- [ ] Connect using HTTPS and appropriate authentication/session once supported.
- [ ] Add live push/WS carefully with authorization, background limitations, dedup and resync.
- [ ] Test correctness against Android/web fixture and API contract suite.

## Android/iOS consistency and independence
Both repos implement the same *semantic JSON contracts* but use native UI/architecture. Keep fixture contract docs synchronized through OpenAPI/JSON Schema later, ideally via generated models and contract tests. Avoid copying Kotlin UI into iOS or coupling either app to a running backend.

## Resume evidence
After implementation, cite **native iOS / SwiftUI** with demonstrated simulator/device builds, screenshots and automated tests. Do not claim App Store release, production users or real AI model integration until delivered.
