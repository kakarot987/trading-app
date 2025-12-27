# 🔄 Data Flow & Service Integration

Complete guide to how data flows through the trading platform.

---

## System Overview

```
┌──────────────────────────────────────────────────────────┐
│                   EXTERNAL SOURCES                        │
│  [Binance WebSocket]  [Yahoo Finance API]                │
└────────────┬──────────────────┬─────────────────────────-┘
             │                  │
             ↓                  ↓
    ┌────────────────────────────────────┐
    │   Service 1: Market Data Producer  │
    │   - Normalizes data                │
    │   - Enriches with metadata         │
    └──────────┬──────────────┬──────────┘
               │              │
               │ Kafka        │ REST (historical backfill)
               ↓              ↓
    ┌──────────────────┐  ┌──────────────────────┐
    │   Apache Kafka   │  │   Service 4          │
    │   trade-events   │  │   Historical Data    │
    └──────────┬───────┘  └──────────────────────┘
               │
               ↓
    ┌──────────────────────────────┐
    │   Service 2: Trade Processor │
    │   - Validates trades         │
    │   - Enriches with latency    │
    │   - Persists to DB           │
    └─┬────────┬─────────┬─────────┘
      │        │         │
      │        │         │ Kafka processed-trades
      │        │         ↓
      │        │   ┌──────────────────────────┐
      │        │   │  Service 3: WebSocket    │
      │        │   │  - Maintains connections │
      │        │   │  - Broadcasts updates    │
      │        │   └──────────┬───────────────┘
      │        │              │
      │        ↓              ↓ WebSocket
      │   ┌─────────┐   ┌──────────────┐
      │   │  Redis  │   │   Frontend   │
      │   │  Cache  │   │   Clients    │
      │   └─────────┘   └──────────────┘
      ↓
┌──────────────┐
│  PostgreSQL  │
│  trades      │
│  candles     │
└──────────────┘
```

---

## Data Flow Patterns

### **Pattern 1: Real-Time Trade Ingestion**

```
Binance → Service 1 → Kafka → Service 2 → PostgreSQL + Redis + Kafka → Service 3 → Frontend
```

**Step-by-Step:**

1. **External API → Service 1**
    - Binance sends trade: `{"s":"BTCUSDT","p":"45000.50","q":"0.125",...}`
    - Service 1 normalizes to `TradeEvent`

2. **Service 1 → Kafka**
    - Topic: `trade-events`
    - Partition: Based on symbol hash
    - Payload: `TradeEvent` (JSON)

3. **Kafka → Service 2**
    - Consumer group: `trade-processor-group`
    - Batch size: 500 messages
    - Auto-commit: false (manual acknowledgment)

4. **Service 2 Processing**
    - Enriches with `processedAt`, `latencyMs`
    - Validates data (non-null, positive values)
    - Deduplicates by `tradeId`

5. **Service 2 → PostgreSQL**
    - Insert into `trades` table
    - Uses R2DBC (non-blocking)
    - Batch inserts for performance

6. **Service 2 → Redis**
    - Store recent trades (last 5 minutes)
    - Key: `recent_trades:{symbol}`
    - TTL: 300 seconds

7. **Service 2 → Kafka**
    - Topic: `processed-trades`
    - Payload: `ProcessedTradeEvent` with enrichment

8. **Kafka → Service 3**
    - Consumer group: `websocket-broadcaster-group`
    - Real-time consumption (no batching)

9. **Service 3 → Frontend**
    - WebSocket broadcast to all connected clients
    - Optional filtering by symbol
    - JSON payload

**Latency:**
- External API → Frontend: **50-100ms**
- Binance → Service 1: 10-20ms
- Service 1 → Kafka: 5-10ms
- Kafka → Service 2: 5-10ms
- Service 2 → DB: 10-20ms
- Service 2 → Kafka: 5-10ms
- Kafka → Service 3: 5ms
- Service 3 → Frontend: 5-25ms

---

### **Pattern 2: Historical Data Query**

```
Frontend → Service 4 → Redis → PostgreSQL → Service 1 → Binance API → Frontend
```

**Multi-Layer Retrieval Strategy:**

**Layer 1: Redis Cache (Fastest - <5ms)**
```
Request: GET /candles?symbol=BTCUSDT&interval=15m&start=X&end=Y
    ↓
Service 4 checks Redis: candles:BTCUSDT:15m:*
    ↓
If found: Return cached data ✓ (5ms)
If not found: Go to Layer 2
```

**Layer 2: PostgreSQL Candles Table (Fast - 10-50ms)**
```
Service 4 queries: SELECT * FROM candles WHERE ...
    ↓
If found: Cache in Redis + Return ✓ (30ms)
If not found: Go to Layer 3
```

**Layer 3: Aggregate from Raw Trades (Medium - 100-500ms)**
```
Service 4 queries: SELECT * FROM trades WHERE ...
    ↓
Aggregate into candles using SQL function
    ↓
Store in candles table
    ↓
Cache in Redis + Return ✓ (200ms)
If insufficient data: Go to Layer 4
```

**Layer 4: External API via Service 1 (Slow - 500-2000ms)**
```
Service 4 → Service 1 REST API
    ↓
Service 1 → Binance API: GET /api/v3/klines
    ↓
Binance returns historical candles
    ↓
Service 1 → Service 4
    ↓
Service 4 stores in DB + caches + returns ✓ (1000ms)
```

---

### **Pattern 3: Cross-Service Communication**

**Service 4 Calling Service 1 for Backfill:**

```
Frontend requests old data (6 months ago)
    ↓
Service 4: No data in DB/cache
    ↓
Service 4 → HTTP GET http://market-data-producer:8080/api/v1/market-data/historical/candles
    ↓
Service 1 → Binance API
    ↓
Service 1 returns BinanceKlinesResponse
    ↓
Service 4 converts to Candle objects
    ↓
Service 4 stores in PostgreSQL
    ↓
Service 4 caches in Redis
    ↓
Service 4 returns to Frontend
```

---

## Message Formats

### **TradeEvent (Service 1 → Kafka)**

```json
{
  "symbol": "BTCUSDT",
  "exchange": "BINANCE",
  "price": 45123.45,
  "volume": 0.125,
  "tradeTimestamp": 1704153600000,
  "side": "BUY",
  "tradeId": "abc123xyz"
}
```

### **ProcessedTradeEvent (Service 2 → Kafka)**

```json
{
  "tradeId": "abc123xyz",
  "symbol": "BTCUSDT",
  "exchange": "BINANCE",
  "price": 45123.45,
  "volume": 0.125,
  "tradeTimestamp": 1704153600000,
  "side": "BUY",
  "processedAt": 1704153600050,
  "latencyMs": 50
}
```

### **Candle (Service 4 Response)**

```json
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
```

---

## Database Schema Integration

### **Shared Tables (Service 2 creates, Service 4 reads)**

**trades table:**
```sql
CREATE TABLE trades (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) UNIQUE NOT NULL,
    symbol VARCHAR(16) NOT NULL,
    exchange VARCHAR(32),
    price NUMERIC(18,8) NOT NULL,
    volume NUMERIC(18,8) NOT NULL,
    trade_timestamp BIGINT NOT NULL,
    side VARCHAR(4),
    processed_at BIGINT NOT NULL,
    latency_ms BIGINT NOT NULL
);

-- Written by: Service 2
-- Read by: Service 4
```

### **Service 4 Exclusive Tables**

**candles table:**
```sql
CREATE TABLE candles (
    id UUID PRIMARY KEY,
    symbol VARCHAR(16) NOT NULL,
    interval VARCHAR(10) NOT NULL,
    open_time BIGINT NOT NULL,
    close_time BIGINT NOT NULL,
    open DOUBLE PRECISION NOT NULL,
    high DOUBLE PRECISION NOT NULL,
    low DOUBLE PRECISION NOT NULL,
    close DOUBLE PRECISION NOT NULL,
    volume DOUBLE PRECISION NOT NULL,
    trades INTEGER NOT NULL,
    buy_volume DOUBLE PRECISION NOT NULL,
    notional_value DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (symbol, interval, open_time)
);

-- Written by: Service 4
-- Read by: Service 4
```

---

## Kafka Topics

### **trade-events**
- **Producer:** Service 1
- **Consumer:** Service 2
- **Partitions:** 6
- **Replication Factor:** 1 (dev), 3 (prod)
- **Retention:** 7 days
- **Message Rate:** 100-1000 msg/sec

### **processed-trades**
- **Producer:** Service 2
- **Consumer:** Service 3
- **Partitions:** 6
- **Replication Factor:** 1 (dev), 3 (prod)
- **Retention:** 3 days
- **Message Rate:** 100-1000 msg/sec

---

## Redis Data Structures

### **Recent Trades (Sorted Set)**
```
Key: recent_trades:BTCUSDT
Type: Sorted Set
Score: timestamp
Value: JSON trade data
TTL: 300 seconds (5 minutes)

ZADD recent_trades:BTCUSDT 1704153600000 '{"tradeId":"abc123",...}'
ZRANGEBYSCORE recent_trades:BTCUSDT 1704153000000 1704153600000
```

### **Candles (String)**
```
Key: candles:BTCUSDT:15m:1704067200000
Type: String (JSON)
Value: Candle object
TTL: 900 seconds (15 minutes)

SET candles:BTCUSDT:15m:1704067200000 '{"open":45000,...}' EX 900
GET candles:BTCUSDT:15m:1704067200000
```

---

## Service Input/Output Summary

### **Service 1: Market Data Producer**

**Inputs:**
- Binance WebSocket: Real-time trade stream
- Yahoo Finance API: Polling every 60 seconds

**Outputs:**
- Kafka `trade-events`: Normalized trades
- REST `/historical/candles`: OHLCV data from Binance
- REST `/historical/trades`: Recent trades from Binance

**Dependencies:**
- Kafka (publish)
- External APIs (Binance, Yahoo Finance)

---

### **Service 2: Trade Processor**

**Inputs:**
- Kafka `trade-events`: Raw trades from Service 1

**Outputs:**
- PostgreSQL `trades` table: Persistent storage
- Redis `recent_trades`: Fast cache
- Kafka `processed-trades`: Enriched trades

**Dependencies:**
- Kafka (consume + publish)
- PostgreSQL (write)
- Redis (write)

---

### **Service 3: WebSocket Broadcaster**

**Inputs:**
- Kafka `processed-trades`: Enriched trades from Service 2

**Outputs:**
- WebSocket messages: Real-time broadcasts to frontend

**Dependencies:**
- Kafka (consume)
- Frontend clients (WebSocket)

---

### **Service 4: Historical Data**

**Inputs:**
- REST API requests: Frontend queries
- PostgreSQL `trades`: Raw trade data (from Service 2)
- Redis cache: Cached candles
- Service 1 REST API: External data backfill

**Outputs:**
- REST API responses: Historical candles and trades
- PostgreSQL `candles`: Aggregated OHLCV data
- Redis cache: Cached results

**Dependencies:**
- PostgreSQL (read `trades`, write `candles`)
- Redis (read/write cache)
- Service 1 REST API (backfill)

---

## Error Handling & Retry Logic

### **Service 1 → Binance WebSocket**
- **Connection Lost:** Auto-reconnect after 5 seconds
- **Rate Limit:** Backoff exponentially
- **Invalid Data:** Log and skip

### **Service 2 → PostgreSQL**
- **Connection Failed:** Retry 3 times with backoff
- **Duplicate Key:** Skip (trade already processed)
- **Constraint Violation:** Log and dead-letter queue

### **Service 3 → Frontend**
- **Client Disconnect:** Clean up resources
- **Broadcast Failed:** Log and continue
- **Client Reconnect:** Resume from last message

### **Service 4 → Service 1**
- **HTTP Timeout:** Retry once after 1 second
- **503 Unavailable:** Return cached/DB data only
- **404 Not Found:** Return empty result

---

## Performance Optimization

### **Batching**
- Service 2: Batch insert 500 trades at once
- Service 4: Batch cache 100 candles at once

### **Connection Pooling**
- PostgreSQL: 10-50 connections per service
- Redis: 5-20 connections per service

### **Caching Strategy**
- Cache hit ratio target: >80%
- Cache TTL: 5-15 minutes
- Cache eviction: LRU policy

### **Kafka Optimization**
- Batch size: 500 messages
- Compression: snappy
- Acks: all (reliability)

---

## Monitoring Data Flow

### **Key Metrics**

**Service 1:**
- Trades published/sec
- WebSocket connection uptime
- API request latency

**Service 2:**
- Kafka consumer lag
- Database write latency
- Processing time per trade

**Service 3:**
- Active WebSocket connections
- Messages broadcast/sec
- Connection errors/sec

**Service 4:**
- Cache hit rate
- Query response time (p95, p99)
- Database query count

---

**Understanding data flow is key to debugging and optimization! 🔄**