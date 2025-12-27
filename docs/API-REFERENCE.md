# 📡 API Reference

Complete API documentation for all services.

---

## Service 1: Market Data Producer (Port 8080)

### **GET /api/v1/market-data/health**

Health check endpoint.

**Response:**
```json
{
  "status": "UP",
  "service": "market-data-producer",
  "timestamp": "1704153600000"
}
```

---

### **GET /api/v1/market-data/historical/candles**

Fetch historical OHLCV candles from Binance.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair (e.g., BTCUSDT) |
| `interval` | String | Yes | Candle interval: 1m, 5m, 15m, 1h, 1d |
| `startTime` | Long | Yes | Start time (Unix timestamp in milliseconds) |
| `endTime` | Long | Yes | End time (Unix timestamp in milliseconds) |
| `limit` | Integer | No | Max candles to return (default: 500) |

**Example Request:**
```bash
GET /api/v1/market-data/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000&limit=100
```

**Response:**
```json
{
  "symbol": "BTCUSDT",
  "interval": "1h",
  "candles": [
    {
      "openTime": 1704067200000,
      "open": 45123.45,
      "high": 45234.56,
      "low": 45000.12,
      "close": 45200.34,
      "volume": 123.45,
      "closeTime": 1704070800000,
      "trades": 1234
    }
  ]
}
```

---

### **GET /api/v1/market-data/historical/trades**

Fetch recent trades from Binance.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair (e.g., BTCUSDT) |
| `limit` | Integer | No | Number of trades (default: 1000, max: 1000) |

**Example Request:**
```bash
GET /api/v1/market-data/historical/trades?symbol=BTCUSDT&limit=100
```

**Response:**
```json
[
  {
    "id": 123456789,
    "price": 45200.50,
    "qty": 0.125,
    "time": 1704153600000,
    "isBuyerMaker": false
  }
]
```

---

## Service 2: Trade Processor (Port 8081)

### **Note:**
Service 2 does not expose REST endpoints. It consumes from Kafka and writes to database.

**Internal Components:**

**Kafka Consumer:**
- Topic: `trade-events`
- Group ID: `trade-processor-group`

**Kafka Producer:**
- Topic: `processed-trades`

**Database Tables:**
- `trades` - stores all processed trades

---

## Service 3: WebSocket Broadcaster (Port 8082)

### **WebSocket Connection**

**Endpoint:** `ws://localhost:8082/ws/trades`

**Protocol:** WebSocket (RFC 6455)

### **Connection Example (JavaScript):**

```javascript
const ws = new WebSocket('ws://localhost:8082/ws/trades');

ws.onopen = () => {
  console.log('Connected to trade stream');
};

ws.onmessage = (event) => {
  const trade = JSON.parse(event.data);
  console.log('New trade:', trade);
};

ws.onerror = (error) => {
  console.error('WebSocket error:', error);
};

ws.onclose = () => {
  console.log('Disconnected from trade stream');
};
```

### **Message Format:**

Incoming messages (broadcasted trades):

```json
{
  "tradeId": "12345678",
  "symbol": "BTCUSDT",
  "exchange": "BINANCE",
  "price": 45200.50,
  "volume": 0.125,
  "tradeTimestamp": 1704153600000,
  "side": "BUY",
  "processedAt": 1704153600050,
  "latencyMs": 50
}
```

### **Filtering (Optional):**

Send filter message after connection:

```javascript
ws.send(JSON.stringify({
  type: 'subscribe',
  symbols: ['BTCUSDT', 'ETHUSDT']
}));
```

---

## Service 4: Historical Data Service (Port 8084)

### **GET /api/v1/historical/candles**

Get historical OHLCV candles (aggregated from trades or external API).

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair (e.g., BTCUSDT) |
| `interval` | String | Yes | Candle interval: 1m, 5m, 15m, 1h, 1d |
| `startTime` | Long | Yes | Start time (Unix timestamp in ms) |
| `endTime` | Long | Yes | End time (Unix timestamp in ms) |
| `limit` | Integer | No | Max candles (default: 500, max: 1000) |

**Example Request:**
```bash
GET /api/v1/historical/candles?symbol=BTCUSDT&interval=15m&startTime=1704067200000&endTime=1704153600000&limit=100
```

**Response:**
```json
{
  "symbol": "BTCUSDT",
  "interval": "15m",
  "candles": [
    {
      "symbol": "BTCUSDT",
      "interval": "15m",
      "openTime": 1704067200000,
      "closeTime": 1704068100000,
      "open": 45123.45,
      "high": 45234.56,
      "low": 45000.12,
      "close": 45200.34,
      "volume": 123.45,
      "trades": 1234,
      "buyVolume": 67.89,
      "notionalValue": 5567890.12
    }
  ],
  "count": 100,
  "fromCache": true,
  "fromExternalApi": false,
  "queryTimeMs": 15
}
```

**Data Source Priority:**
1. Redis cache (fastest)
2. PostgreSQL candles table
3. Aggregate from raw trades
4. Service 1 → Binance API (slowest)

---

### **GET /api/v1/historical/candles-by-date**

Same as `/candles` but with ISO date-time parameters.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair |
| `interval` | String | Yes | Candle interval |
| `start` | DateTime | Yes | Start time (ISO 8601: 2024-01-01T00:00:00Z) |
| `end` | DateTime | Yes | End time (ISO 8601) |
| `limit` | Integer | No | Max candles |

**Example Request:**
```bash
GET /api/v1/historical/candles-by-date?symbol=BTCUSDT&interval=1h&start=2024-01-01T00:00:00Z&end=2024-01-02T00:00:00Z
```

---

### **GET /api/v1/historical/trades/recent**

Get recent trades for a symbol.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair |
| `from` | Long | No | From timestamp (default: 1 hour ago) |
| `limit` | Integer | No | Max trades (default: 100, max: 1000) |

**Example Request:**
```bash
GET /api/v1/historical/trades/recent?symbol=BTCUSDT&from=1704067200000&limit=100
```

**Response:**
```json
[
  {
    "id": 123456,
    "tradeId": "abc123",
    "symbol": "BTCUSDT",
    "exchange": "BINANCE",
    "price": 45200.50,
    "volume": 0.125,
    "tradeTimestamp": 1704153600000,
    "side": "BUY",
    "processedAt": 1704153600050,
    "latencyMs": 50
  }
]
```

---

### **GET /api/v1/historical/trades**

Get trades in a specific time range.

**Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `symbol` | String | Yes | Trading pair |
| `startTime` | Long | Yes | Start timestamp |
| `endTime` | Long | Yes | End timestamp |

**Example Request:**
```bash
GET /api/v1/historical/trades?symbol=BTCUSDT&startTime=1704067200000&endTime=1704153600000
```

**Response:** Same as `/trades/recent`

---

### **POST /api/v1/historical/candles/batch**

Query multiple symbols in parallel.

**Request Body:**
```json
{
  "symbols": ["BTCUSDT", "ETHUSDT", "BNBUSDT"],
  "interval": "15m",
  "startTime": 1704067200000,
  "endTime": 1704153600000,
  "limit": 500
}
```

**Response:**
```json
{
  "BTCUSDT": {
    "symbol": "BTCUSDT",
    "candles": [ /* ... */ ],
    "count": 100,
    "fromCache": true,
    "queryTimeMs": 15
  },
  "ETHUSDT": {
    "symbol": "ETHUSDT",
    "candles": [ /* ... */ ],
    "count": 100,
    "fromCache": false,
    "queryTimeMs": 45
  }
}
```

---

### **GET /api/v1/historical/health**

Health check for historical service.

**Response:**
```json
{
  "status": "UP",
  "timestamp": 1704153600000,
  "service": "historical-data-service"
}
```

---

## Error Responses

All services follow standard HTTP error codes:

### **400 Bad Request**
```json
{
  "error": "Invalid interval. Supported values: 1m, 5m, 15m, 1h, 1d",
  "timestamp": 1704153600000
}
```

### **404 Not Found**
```json
{
  "error": "Symbol not found: XYZ123",
  "timestamp": 1704153600000
}
```

### **429 Too Many Requests**
```json
{
  "error": "Rate limit exceeded. Max 100 requests per minute.",
  "timestamp": 1704153600000
}
```

### **500 Internal Server Error**
```json
{
  "error": "Internal server error: Database connection failed",
  "timestamp": 1704153600000
}
```

### **503 Service Unavailable**
```json
{
  "error": "Service temporarily unavailable. Please retry.",
  "timestamp": 1704153600000
}
```

---

## Rate Limits

| Service | Endpoint | Limit |
|---------|----------|-------|
| Service 1 | All endpoints | 100 req/min |
| Service 4 | /candles | 60 req/min |
| Service 4 | /trades | 30 req/min |
| Service 4 | /batch | 10 req/min |

---

## Common Query Patterns

### **Get last 24 hours of 15-minute candles:**
```bash
curl "http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=15m&startTime=$(($(date +%s)*1000-86400000))&endTime=$(($(date +%s)*1000))&limit=96"
```

### **Get last 1000 trades:**
```bash
curl "http://localhost:8084/api/v1/historical/trades/recent?symbol=BTCUSDT&limit=1000"
```

### **Connect to WebSocket:**
```bash
wscat -c ws://localhost:8082/ws/trades
```

---

## Pagination

Currently not implemented. Use `limit` parameter to control response size.

**Future:** Cursor-based pagination for large datasets.

---

## Authentication

Currently: **None** (development mode)

**Production:** Add API keys or JWT tokens.

---

## CORS Configuration

Allowed origins (configurable):
- `http://localhost:3000` (React dev server)
- `http://localhost:4200` (Angular dev server)
- Production domain

---

## WebSocket Protocol Details

### **Connection Lifecycle:**

1. Client connects: `ws://localhost:8082/ws/trades`
2. Server sends: `{ "type": "connected", "clientId": "abc123" }`
3. Server broadcasts trades
4. Client closes connection or timeout (5 min idle)

### **Heartbeat:**
Server sends ping every 30 seconds. Client must respond with pong.

---

## Data Formats

### **Timestamps:**
- All timestamps are Unix epoch in **milliseconds**
- Example: `1704153600000` = 2024-01-02 00:00:00 UTC

### **Prices & Volumes:**
- Decimal numbers as strings in external API responses
- Converted to `Double` internally
- Precision: 8 decimal places

### **Symbols:**
- Format: `<BASE><QUOTE>` (e.g., `BTCUSDT`)
- Case-insensitive (converted to uppercase)

---

This API reference covers all public endpoints. For internal Kafka topics and database schemas, see [Architecture Documentation](ARCHITECTURE.md).