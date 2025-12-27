# 🏗️ System Architecture

## Overview

This trading platform uses a microservices architecture with event-driven design patterns. Each service has a single, well-defined responsibility and communicates via Kafka, REST APIs, and WebSockets.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     TRADING PLATFORM                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌──────────────┐         ┌──────────────┐                     │
│  │   Binance    │         │Yahoo Finance │                     │
│  │   WebSocket  │         │  REST API    │                     │
│  └──────┬───────┘         └──────┬───────┘                     │
│         │                        │                              │
│         └────────┬───────────────┘                              │
│                  ↓                                               │
│         ┌────────────────────┐                                  │
│         │  Service 1         │                                  │
│         │  Market Data       │                                  │
│         │  Producer          │                                  │
│         └─────────┬──────────┘                                  │
│                   │                                              │
│                   ↓ Kafka (trade-events)                        │
│         ┌────────────────────┐                                  │
│         │  Service 2         │                                  │
│         │  Trade Processor   │                                  │
│         └─────┬───┬──────────┘                                  │
│               │   │                                              │
│               │   └─→ PostgreSQL (trades)                       │
│               │   └─→ Redis (cache)                             │
│               │                                                  │
│               ↓ Kafka (processed-trades)                        │
│         ┌────────────────────┐                                  │
│         │  Service 3         │                                  │
│         │  WebSocket         │                                  │
│         │  Broadcaster       │                                  │
│         └─────────┬──────────┘                                  │
│                   │                                              │
│                   ↓ WebSocket                                   │
│         ┌────────────────────┐                                  │
│         │    Frontend        │                                  │
│         │    Clients         │                                  │
│         └─────────┬──────────┘                                  │
│                   │                                              │
│                   ↓ REST API                                    │
│         ┌────────────────────┐                                  │
│         │  Service 4         │                                  │
│         │  Historical Data   │                                  │
│         └─────┬───┬──────────┘                                  │
│               │   │                                              │
│               │   └─→ PostgreSQL (read trades, write candles)   │
│               │   └─→ Redis (cache)                             │
│               │   └─→ Service 1 (backfill missing data)         │
│               │                                                  │
└───────────────┴─────────────────────────────────────────────────┘
```

---

## Service Responsibilities

### **Service 1: Market Data Producer**

**Responsibility:** Gateway to external market data sources

**Functions:**
- Establishes WebSocket connections to Binance for real-time crypto data
- Polls Yahoo Finance REST API every 60 seconds for stock prices
- Normalizes data from different sources into canonical format
- Publishes events to Kafka
- **NEW:** Provides REST endpoints for historical data backfill

**Why Separate:**
- Isolates external API dependencies
- Single point of configuration for API keys
- Easy to add new data sources (Coinbase, Kraken, etc.)

---

### **Service 2: Trade Processor**

**Responsibility:** Centralized trade processing and persistence

**Functions:**
- Consumes raw trade events from Kafka
- Enriches with metadata (processing timestamp, latency)
- Validates and normalizes data
- Persists to PostgreSQL for durability
- Caches recent trades in Redis for fast access
- Publishes enriched events to Kafka

**Why Separate:**
- Decouples data ingestion from storage
- Can scale independently for high write throughput
- Single source of truth for trade data

---

### **Service 3: WebSocket Broadcaster**

**Responsibility:** Real-time communication with frontend clients

**Functions:**
- Maintains persistent WebSocket connections
- Subscribes to Kafka for processed trades
- Broadcasts updates to connected clients
- Supports topic filtering (e.g., only BTC updates)

**Why Separate:**
- Handles connection management complexity
- Can scale horizontally for more WebSocket connections
- Doesn't impact data processing if connections fail

---

### **Service 4: Historical Data Service**

**Responsibility:** Historical data queries and analytics

**Functions:**
- Provides REST APIs for historical queries
- Multi-layer data retrieval strategy
- Aggregates raw trades into OHLCV candles
- Background jobs for pre-aggregation
- Caches results for performance

**Why Separate:**
- Complex query logic isolated from real-time path
- Can optimize for read-heavy workload
- Independent scaling based on query volume

---

## Data Flow Patterns

### **Pattern 1: Real-Time Data Ingestion**

```
External API → Service 1 → Kafka → Service 2 → PostgreSQL/Redis
                                          ↓
                                   Service 3 → WebSocket → Frontend
```

**Characteristics:**
- Event-driven, asynchronous
- High throughput (10,000+ events/sec)
- Low latency (<50ms)

---

### **Pattern 2: Historical Data Query**

```
Frontend → Service 4 → Redis (cache hit?) → Return
                    ↓ (cache miss)
               PostgreSQL → Aggregate → Cache → Return
                    ↓ (no DB data)
               Service 1 → Binance API → Store → Return
```

**Characteristics:**
- Request-response, synchronous
- Multi-layer caching
- Fallback to external APIs

---

### **Pattern 3: Cross-Service Communication**

```
Service 4 ←─REST API─→ Service 1
   ↓                       ↓
PostgreSQL (shared)   Binance API
```

**Characteristics:**
- REST for historical data fetch
- Shared database (Service 2 writes, Service 4 reads)
- No circular dependencies

---

## Technology Choices

### **Why Kotlin?**
- Concise, expressive syntax
- Null-safety built-in
- Excellent coroutines support for async/reactive code
- Full Java interoperability

### **Why Spring WebFlux?**
- Non-blocking, reactive programming model
- Better resource utilization
- Handles high concurrency with fewer threads
- Native Kotlin coroutines support

### **Why Kafka?**
- Guaranteed message delivery
- High throughput (millions of messages/sec)
- Event replay capability
- Decouples producers and consumers

### **Why PostgreSQL?**
- ACID compliance for financial data
- Excellent query performance
- JSON support for flexible schemas
- Proven reliability

### **Why Redis?**
- Sub-millisecond latency
- Pub/sub for real-time updates
- Data structures (sorted sets, hashes)
- LRU eviction for cache management

### **Why R2DBC?**
- Reactive database access (non-blocking)
- Consistent with Spring WebFlux
- Better scalability than JDBC

---

## Scalability Strategy

### **Horizontal Scaling**

**Service 1:**
- Add more instances to connect to different exchanges
- Each instance handles different symbols

**Service 2:**
- Kafka consumer group auto-balances partitions
- Scale based on write throughput

**Service 3:**
- Stateless, can scale to thousands of instances
- Load balancer distributes WebSocket connections

**Service 4:**
- Read-only, can scale independently
- Add replicas based on query volume

### **Vertical Scaling**

**PostgreSQL:**
- Increase connection pool size
- Add read replicas for Service 4
- Partition tables by time (monthly)

**Redis:**
- Redis Cluster for horizontal sharding
- Read replicas for cache reads

**Kafka:**
- Increase partitions for parallelism
- Add brokers for higher throughput

---

## Fault Tolerance

### **Service Failures**

**Service 1 Down:**
- Kafka retains events until Service 2 catches up
- Service 4 can still serve cached/stored data

**Service 2 Down:**
- Service 1 buffers events in Kafka
- WebSocket clients see stale data until recovery

**Service 3 Down:**
- No real-time updates to frontend
- Frontend can poll Service 4 for updates

**Service 4 Down:**
- Real-time path unaffected
- Historical queries fail, frontend shows error

### **Infrastructure Failures**

**Kafka Down:**
- Services buffer in-memory (limited)
- Graceful degradation

**PostgreSQL Down:**
- Redis serves cached data
- Writes fail, services log errors

**Redis Down:**
- Services fall back to PostgreSQL
- Slower but functional

---

## Security Considerations

### **API Security**
- Service-to-service auth with API keys
- Rate limiting per service
- Input validation on all endpoints

### **Data Security**
- PostgreSQL connection pooling with SSL
- Redis password authentication
- Kafka SASL/SCRAM authentication (production)

### **Network Security**
- Docker network isolation
- Services only expose necessary ports
- Frontend CORS configuration

---

## Monitoring & Observability

### **Metrics**
- Spring Boot Actuator endpoints
- Prometheus-compatible metrics
- Custom business metrics (trades/sec, latency)

### **Logging**
- Structured JSON logging
- Correlation IDs for request tracing
- Log aggregation (ELK stack recommended)

### **Health Checks**
- Kubernetes-ready liveness/readiness probes
- Database connection health
- Kafka consumer lag monitoring

---

## Trade-offs & Design Decisions

### **Shared Database (Service 2 & 4)**

**Pro:** Simple, consistent data  
**Con:** Tight coupling  
**Decision:** Acceptable for this use case, shared read-only access

### **Synchronous REST (Service 4 → Service 1)**

**Pro:** Simple request-response  
**Con:** Latency, tight coupling  
**Decision:** Acceptable for backfill use case (infrequent)

### **No Service Mesh (Istio/Linkerd)**

**Pro:** Simpler deployment, fewer moving parts  
**Con:** Manual service discovery  
**Decision:** Docker Compose sufficient for small-scale deployment

### **Single Kafka Instance**

**Pro:** Simple setup  
**Con:** Single point of failure  
**Decision:** Fine for development, use cluster in production

---

## Future Enhancements

### **Phase 2**
- Service 5: AI/Analytics Engine
- Alert Service (price alerts, anomaly detection)
- Service discovery (Consul/Eureka)

### **Phase 3**
- Multi-region deployment
- Event sourcing with Event Store
- GraphQL API layer

### **Phase 4**
- Kubernetes deployment
- Auto-scaling based on metrics
- Distributed tracing (Jaeger)

---

## Architecture Principles

1. **Single Responsibility:** Each service does one thing well
2. **Loose Coupling:** Services communicate via well-defined interfaces
3. **High Cohesion:** Related functionality grouped together
4. **Scalability:** Each component can scale independently
5. **Resilience:** Graceful degradation on failures
6. **Observability:** Comprehensive logging and metrics

---

## Conclusion

This architecture balances:
- **Simplicity:** Easy to understand and deploy
- **Performance:** Sub-second latency, high throughput
- **Reliability:** Fault-tolerant with multiple fallbacks
- **Scalability:** Can grow from prototype to production

Perfect for learning microservices while building a real trading platform! 🚀