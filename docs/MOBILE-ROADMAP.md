# Android + iOS strategy — mock now, connect later

> **Updated choice:** Build *two separate native apps*, not the previously suggested single Expo codebase. Use [Android specification](ANDROID-STANDALONE-APP.md) (Kotlin + Jetpack Compose) and [iOS specification](IOS-STANDALONE-APP.md) (Swift + SwiftUI). The Expo approach below is retained as an alternative, not the chosen plan.

**Recommendation:** add a separate **Expo + React Native + TypeScript** mobile client, not a React website rewrite. It is suitable for developing cross-platform app skills alongside the existing React web stack. If targeting specialist native positions, Kotlin/Jetpack Compose for Android and Swift/SwiftUI for iOS demonstrate *native* skills more directly.

## Phase A: independent mock API app
- [ ] Create `apps/mobile` (or `mobile-app`) using Expo, TypeScript and Expo Router.
- [ ] Build watchlist, asset detail, signal feed, signal explanation, alert list and paper portfolio (clearly marked **mock/simulated**).
- [ ] Mock typed endpoints with JSON fixtures or MSW / local adapter: `GET /v1/assets`, `GET /v1/signals`, `GET /v1/portfolio`, `GET /v1/alerts`.
- [ ] Use a dedicated `MarketApi` interface with `MockMarketApi` now and `HttpMarketApi` later; swap via runtime environment config.
- [ ] Include loading/error/empty/offline UI, basic navigation and accessibility.
- [ ] Demonstrate both Android and iOS using simulator/device or builds, and record which platforms were actually tested.

## Phase B: integrate shared backend
- [ ] Introduce API contract-first OpenAPI/JSON Schema in `contracts/openapi.yaml`.
- [ ] Map existing historical REST and new signals/portfolio endpoints into a stable `/v1` namespace via a BFF/API boundary only when needed.
- [ ] Generate or share types and validate payloads at runtime, including schema version, UTC timestamps, pagination and error format.
- [ ] Add auth/session if multi-user paper accounts; never embed broker/API provider secrets in mobile clients.
- [ ] Integrate WS or push alerts separately, with permissions, reconnect/backoff and notification dedup.
- [ ] Add contract tests and mock fallback for independent demo use.

## Frontend strategy
React 19 + TypeScript + Vite + TanStack Query + Zustand in `client-connection` is not intrinsically obsolete. Reuse API contracts, hooks/pure calculation helpers when framework-neutral, colors/tokens and branding—not DOM components. Incremental web redesign can introduce route-based pages and feature boundaries without throwing away working charts and streams.

## Honest resume framing
- After Phase A with devices/emulators tested: **“Built a cross-platform React Native (Expo, TypeScript) cryptocurrency dashboard for Android and iOS using typed mock APIs, navigation and offline/error states.”**
- After Phase B: **“Integrated Android/iOS clients with versioned market-data, AI signal and paper trading APIs, including live alerts and session handling.”**
- Do not claim Kotlin native Android, Swift native iOS, store publication or production users unless you actually built/shipped those.

## Constraints
No real-money trades; mock data and simulated performance clearly labeled. Authentication and pushes are postponed until service/API boundaries are stable. Do not add heavy mobile emulators to normal Docker development.
