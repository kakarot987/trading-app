# WebSocket Setup Guide

## Current Setup

The application supports two modes:

1. **Mock Data Mode** (default) - For testing without backend
2. **Real WebSocket Mode** - Connects to Service 3 (WebSocket Broadcaster)

---

## Switching Between Modes

### Using Mock Data (Testing Mode)

In `src/App.tsx`, set:

```typescript
const USE_MOCK_DATA = true;
```

**What happens:**
- Generates random trades every 1 second
- Generates random alerts every 5 seconds
- Simulates "connected" status
- No actual WebSocket connection

**Use this when:**
- Testing UI components
- Backend services are not running
- Developing frontend features

---

### Using Real WebSocket

In `src/App.tsx`, set:

```typescript
const USE_MOCK_DATA = false;
```

**What happens:**
- Connects to `ws://localhost:8082/ws/trades`
- Connects to `ws://localhost:8082/ws/alerts`
- Sends subscription messages with selected symbols
- Handles auto-reconnection on disconnect

**Prerequisites:**
1. Service 3 (WebSocket Broadcaster) must be running on port 8082
2. Service 3 must be configured to broadcast trades and alerts

---

## WebSocket Message Format

### Subscription Message (Client → Server)

```json
{
  "symbols": ["BTCUSD", "ETH-USD", "AAPL"]
}
```

### Trade Message (Server → Client)

```json
{
  "tradeId": "trade-123",
  "symbol": "BTCUSD",
  "price": 95234.50,
  "volume": 0.5,
  "timestamp": 1703001234567,
  "processedAt": 1703001234570,
  "latencyMs": 3.2,
  "notionalValue": 47617.25,
  "isBuy": true
}
```

### Alert Message (Server → Client)

```json
{
  "alertId": "alert-456",
  "symbol": "ETH-USD",
  "severity": "HIGH",
  "type": "PRICE_SPIKE",
  "message": "Significant price movement detected",
  "triggerValue": 3521.45,
  "threshold": 3500.00,
  "timestamp": 1703001234567
}
```

---

## WebSocket Configuration

Edit `src/config/index.ts` to change WebSocket settings:

```typescript
ws: {
  tradesUrl: 'ws://localhost:8082/ws/trades',
  alertsUrl: 'ws://localhost:8082/ws/alerts',
  reconnectInterval: 3000,        // Base reconnect delay
  maxReconnectAttempts: 10,       // Max attempts before giving up
  heartbeatInterval: 30000,       // Ping interval (30s)
}
```

---

## Connection Features

### Auto-Reconnection

- Uses exponential backoff (3s, 4.5s, 6.75s, ...)
- Maximum delay capped at 30 seconds
- Stops after 10 failed attempts

### Heartbeat

- Sends ping every 30 seconds to keep connection alive
- Helps detect stale connections

### Subscription Management

- Automatically subscribes when connected
- Re-subscribes when symbol selection changes
- Unsubscribes from removed symbols

---

## Monitoring Connection Status

### In the UI

- Header shows connection status with colored indicators
- Green = Connected
- Yellow = Connecting/Reconnecting  
- Red = Disconnected/Error

### In Browser Console

```
[WebSocket] Connecting to ws://localhost:8082/ws/trades...
[WebSocket] Connected to ws://localhost:8082/ws/trades
[Trade WebSocket] Subscribing to symbols: ['BTCUSD', 'ETH-USD']
[Trade WebSocket] Received trade: BTCUSD 95234.50
```

---

## Troubleshooting

### Connection Refused

**Problem:** `[WebSocket] Error: Connection refused`

**Solution:**
- Ensure Service 3 is running: `./gradlew :websocket-broadcaster:bootRun`
- Check port 8082 is not in use
- Verify firewall settings

### No Data Received

**Problem:** Connected but no trades/alerts appear

**Solution:**
- Check Service 3 is receiving data from Kafka
- Verify symbol subscriptions in browser console
- Ensure Service 2 (Trade Processor) is running

### Frequent Disconnections

**Problem:** Connection drops repeatedly

**Solution:**
- Check network stability
- Increase heartbeat interval
- Check Service 3 logs for errors

---

## Testing WebSocket Manually

Use `websocat` or browser console to test:

```bash
# Install websocat
brew install websocat  # macOS
# or download from https://github.com/vi/websocat

# Connect to trades
websocat ws://localhost:8082/ws/trades

# Send subscription
{"symbols":["BTCUSD"]}
```

---

## Next Steps

Once Service 3 is running:

1. Set `USE_MOCK_DATA = false` in App.tsx
2. Start the frontend: `npm run dev`
3. Watch browser console for connection logs
4. Select symbols in Settings view
5. Monitor trades and alerts flowing in

The WebSocket layer is production-ready with:
- ✅ Auto-reconnection with exponential backoff
- ✅ Heartbeat for connection health
- ✅ Subscription management
- ✅ Error handling and logging
- ✅ Clean disconnect on unmount