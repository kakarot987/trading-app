# Mobile app strategy: independent native Android and iOS

Build **two independent repositories** with compatible mock JSON contracts first. Mobile applications can subsequently switch to versioned trading-app HTTP APIs without rebuilding screens.

- [Android: Kotlin + Jetpack Compose specification](ANDROID-STANDALONE-APP.md)
- [iOS: Swift + SwiftUI specification](IOS-STANDALONE-APP.md)
- [Platform architecture and API boundary](Architecture.md)
- [Milestones and mobile stage gates](MILESTONES.md)

## Phase 1: independent demonstration
Each app implements a watchlist, asset details, AI signal explanation, alerts, simulated paper portfolio, settings and mock/live data labels. Source-controlled JSON fixtures are the default data source. No backend, exchange account or AI engine is required to run the demos.

## Phase 2: stable shared API contracts
Define OpenAPI contracts for assets, historical candles, signals, alerts and paper positions, including timestamps, pagination, error shapes and schema versioning. Both apps implement platform-native HTTP adapters and separate JSON fixture adapters. Do not expose database, Kafka or internal service addresses.

## Phase 3: backend integration
Connect to authenticated HTTPS endpoints as relevant features become available. Add reconnect/resynchronization, notification policy, offline fallback, contract tests and device/simulator testing.

## Resume positioning
Android demonstrates Kotlin/Compose; iOS demonstrates Swift/SwiftUI. State which devices, builds, tests and APIs are actually implemented. Dummy data remains explicitly labeled.
