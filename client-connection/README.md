ClientConnection architectural blueprints
You’ve already got Service 3 and Service 4 in place, so this blueprint focuses on clean boundaries, predictable data flow, and resilience on the client side. We’ll shape the app for high-frequency updates, historical queries, and future AI hooks without overengineering the first iteration.

High-level architecture
App shell: React + TypeScript + Vite with route-level code-splitting and a predictable layout (sidebar + content + notifications).

Data layer: WebSocket client for real-time streams (trades, alerts) + REST via React Query for historical data and metadata.

State layer: Lightweight global state (Zustand or Redux Toolkit) for subscriptions, UI controls, and cached recent events; React Query for server cache.

Rendering: Charting components (TradingView/Apex/ECharts), virtualized lists for tickers/order books, and web workers for indicators.

Integration points: Service 3 for streaming; Service 4 for historical; future AI endpoints plugged into React Query.

Core modules and responsibilities
WebSocket module:

Connect: One connection per stream (trades, alerts) with reconnection/backoff.

Subscribe: Send symbol/topic subscription messages; manage local subscriptions.

Dispatch: Push incoming events to a lightweight event bus/store.

Data fetching module (React Query):

Historical: Trades, candles, and order book snapshots via REST.

Metadata: Symbols list, intervals, and optional AI model health.

Cache policy: Stale-while-revalidate with background refetch on interval change.

State management module:

UI controls: Selected symbols, intervals, filters, and alert preferences.

Subscriptions: Active topics mirrored to WebSocket module.

Recent cache: In-memory ring buffers for recent trades/alerts to drive UI without hitting network.

Visualization module:

Charts: Candle, depth/DOM, and ticker feeds; WebGL-backed rendering where available.

Lists: Virtualized trade feed and alert stream.

Overlays: Indicators (EMA, RSI) computed in a worker.

Security/compliance (deferred):

Auth: Omit JWT for now; keep pluggable interface.

CORS/CSRF: REST calls via same-origin or controlled proxies; rate limits enforced server-side later.

Suggested folder structure
Code
client-connection/
├─ src/
│  ├─ app/
│  │  ├─ providers/        // QueryClient, Theme, Router providers
│  │  └─ routes/           // Route components (Dashboard, Markets, Alerts)
│  ├─ core/
│  │  ├─ ws/               // WebSocket clients, message codecs, backoff
│  │  ├─ api/              // REST clients, query keys, React Query hooks
│  │  ├─ state/            // Zustand/Redux slices (controls, subscriptions, caches)
│  │  └─ workers/          // Indicators, anomaly detection
│  ├─ features/
│  │  ├─ charts/           // Chart wrappers + adapters (TradingView/Apex/ECharts)
│  │  ├─ orderbook/        // Depth view, aggregation controls
│  │  ├─ trades/           // Ticker list, filters, aggregation
│  │  └─ alerts/           // Alert list, notifications
│  ├─ ui/                  // Tailwind+Radix primitives, layout, modals
│  ├─ utils/               // schema validation, formatting, timezones
│  └─ styles/              // Tailwind config, globals
├─ public/
└─ vite.config.ts
Data contracts and message flow
WebSocket channels (Service 3)
Trades stream:

Subscribe payload: { type: "subscribe", channel: "trades", symbols: ["BTCUSD","ETH-USD"] }

Event shape: { ts, symbol, price, size, side }

Alerts stream:

Subscribe payload: { type: "subscribe", channel: "alerts", symbols: ["BTCUSD"] }

Event shape: { ts, symbol, level: "info|warn|critical", code, message, meta }

REST endpoints (Service 4)
Historical trades: GET /trades?symbol=BTCUSD&interval=15m&from=...&to=...

Order book snapshot: GET /orderbook?symbol=BTCUSD

Candles: GET /candles?symbol=BTCUSD&interval=1m

Symbols/metadata: GET /symbols (optional convenience)

Client-side mapping:

Normalization: Convert server payloads into typed DTOs with zod/yup.

Time alignment: UTC-based timestamps; chart adapters expect milliseconds.

State slices and caching strategy
Controls slice:

Selected symbol: single or multi-select.

Interval: 1m | 5m | 15m | 1h | 1d.

Filters: side, min size, alert levels.

Subscriptions slice:

Active topics: channel+symbol set; diffed on change to update WebSocket subscriptions.

Connection status: connected | reconnecting | error.

Recent cache slice:

Trades buffer: fixed-size per symbol (e.g., last 500) for immediate UI.

Alerts buffer: fixed-size per symbol/category.

Compaction: coalesce small trades for smoother charts at high frequency.

React Query cache:

Query keys: ["candles", symbol, interval], ["orderbook", symbol], ["trades-historical", symbol, range].

Policy: { staleTime: 30_000, gcTime: 5 * 60_000, retry: 2 }.

WebSocket client design
Connection factory:

Endpoints: /ws/trades, /ws/alerts.

Backoff: exponential with jitter, max interval and cap.

Lifecycle callbacks: onOpen, onMessage, onError, onClose.

Subscription manager:

Diff-based updates: compute added/removed symbols and send minimal subscription messages.

Heartbeat: optional ping/pong; detect stale connections and force reconnect.

Message pipeline:

Decode → validate → route: trades to trades buffer, alerts to alerts buffer.

Worker handoff: heavy transformations/indicators offloaded to Web Workers.

Visualization approach
Chart adapter layer:

Unified interface: ChartAdapter.renderCandles(data), updateTrades(tick), setOverlay(indicatorData).

Library choice: start with ApexCharts (fast to integrate); keep adapter replaceable for TradingView/ECharts later.

WebGL: enable where supported for high-frequency updates.

Order book and ticker:

Virtualized lists: react-virtual for thousands of rows.

Aggregation controls: group by price tick (e.g., 0.5 USD) for depth clarity.

Alerts UX:

Notifications: non-blocking toasts for critical alerts; persistent panel for history.

Filtering: level and symbol filters with quick toggles.

Performance and resilience plan
Batching updates:

Micro-batches: buffer incoming ticks for 50–100 ms, then flush to UI to avoid re-render storms.

Workers:

Indicators: EMA/RSI/MACD computed off-thread; send deltas only.

Memory management:

Ring buffers: drop oldest entries; avoid unbounded growth.

Backpressure:

Adaptive sampling: reduce chart update frequency under load; maintain accuracy in aggregates.

Testing strategy
Unit tests: codecs, reducers/selectors, WebSocket subscription diffing.

Integration tests: React Query hooks with MSW; charts fed with deterministic streams.

E2E (Cypress): subscribe flows, symbol changes, interval changes, reconnection behavior.

Configuration and environment
Env variables:

WS_TRADES_URL: ws://localhost:8082/ws/trades

WS_ALERTS_URL: ws://localhost:8082/ws/alerts

REST_BASE_URL: http://localhost:8084 (Service 4)

Feature flags:

USE_TRADINGVIEW: toggle chart library

ENABLE_WORKERS: toggle worker-based indicators

Integration flow recap
Live trades/alerts: Service 3 → WebSocket module → recent cache → UI components → charts/lists.

Historical queries: UI controls → React Query → Service 4 → cache → chart adapter.

Future AI signals: AI → Kafka → Alert Service → Service 3 → ClientConnection → notifications.