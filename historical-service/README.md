# 🎯 Complete Integration Guide - Service 4 (Historical Data)

## 📁 Your Project Structure

```
trading-platform/
├── docker-compose.yml                    ← UPDATE (use provided version)
├── market-data/                          ← Service 1 (ADD new files)
│   ├── Dockerfile
│   ├── build.gradle.kts
│   └── src/main/kotlin/com/trading/market_data/
│       ├── controller/
│       │   └── HistoricalDataController.kt    ← ADD THIS (NEW)
│       ├── service/
│       │   └── HistoricalDataService.kt       ← ADD THIS (NEW)
│       └── ...existing files...
├── trade-processor/                      ← Service 2 (NO CHANGES)
├── websocket-broadcaster/                ← Service 3 (OPTIONAL changes)
└── historical-service/                   ← Service 4 (NEW - CREATE THIS)
    ├── Dockerfile
    ├── build.gradle.kts
    ├── settings.gradle.kts
    └── src/
        ├── main/
        │   ├── kotlin/com/trading/historical_service/
        │   │   ├── HistoricalServiceApplication.kt
        │   │   ├── config/
        │   │   │   ├── JacksonConfig.kt
        │   │   │   ├── RedisConfig.kt
        │   │   │   └── WebFluxConfig.kt
        │   │   ├── controller/
        │   │   │   ├── GlobalExceptionHandler.kt
        │   │   │   └── HistoricalDataController.kt
        │   │   ├── entity/
        │   │   │   ├── TradeEntity.kt
        │   │   │   ├── CandleEntity.kt
        │   │   │   └── [extension functions]
        │   │   ├── external/
        │   │   │   └── Service1Client.kt              ← REPLACES duplicate code
        │   │   ├── repository/
        │   │   │   ├── TradeRepository.kt
        │   │   │   ├── CandleRepository.kt
        │   │   │   └── CustomCandleRepository.kt
        │   │   └── service/
        │   │       ├── HistoricalDataService.kt
        │   │       ├── RedisCacheService.kt
        │   │       ├── CandleAggregationService.kt
        │   │       └── ScheduledTasks.kt
        │   └── resources/
        │       ├── application.yaml
        │       └── db/migration/
        │           ├── V1__Create_candles_table.sql
        │           ├── V2__Create_aggregation_functions.sql
        │           └── V3__Create_materialized_views.sql
        └── test/
```

---

## 🚀 Step-by-Step Integration

### **Step 1: Add Service 1 REST Endpoints**

**Files to ADD in `market-data/` (Service 1):**

1. **`src/main/kotlin/com/trading/market_data/controller/HistoricalDataController.kt`**
    - Provided above ✅

2. **`src/main/kotlin/com/trading/market_data/service/HistoricalDataService.kt`**
    - Provided above ✅

3. **Update `market-data/build.gradle.kts`** - Add WebFlux dependency:
```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("org.springframework.boot:spring-boot-starter-webflux")  // ADD THIS
}
```

---

### **Step 2: Create Service 4 (Historical Data)**

**All files provided above. Copy them to your project:**

1. Create `historical-service/` folder
2. Copy all files from artifacts above
3. Use the corrected `build.gradle.kts` from earlier
4. Use migration files V1, V2, V3

---

### **Step 3: Update docker-compose.yml**

**Replace your current docker-compose.yml with the updated version I provided** ✅

Key additions:
- `historical-data-service` container
- Health checks for all services
- Service dependencies
- Environment variable for Service 1 → Service 4 connection

---

### **Step 4: Delete Duplicate Code (You'll do this)**

**In Service 4, DELETE these files:**

```bash
# Navigate to historical-service/src/main/kotlin/.../external/
rm BinanceDataProvider.kt
rm AlphaVantageDataProvider.kt
rm ExternalDataFacade.kt

# These are replaced by Service1Client.kt ✅
```

---

## 🔧 Configuration Summary

### **Service 1 Changes:**
```yaml
# No application.yaml changes needed!
# Just add the new controller and service classes
```

### **Service 2:**
```yaml
# NO CHANGES NEEDED ✅
# Service 4 reads from Service 2's trades table
```

### **Service 3 (Optional):**
```yaml
# OPTIONAL: If Service 3 needs historical data
services:
  historical-data-service:
    url: http://historical-data-service:8084
```

### **Service 4:**
```yaml
# Already configured in application.yaml provided ✅
services:
  market-data-producer:
    url: http://market-data-producer:8080
```

---

## 🎬 Startup Order

```bash
# 1. Start infrastructure
docker-compose up -d kafka postgres redis

# 2. Wait for health checks (30 seconds)
docker-compose ps

# 3. Start Service 1 (Market Data Producer)
docker-compose up -d market-data-producer

# 4. Start Service 2 (Trade Processor)
docker-compose up -d trade-processor

# 5. Start Service 4 (Historical Data)
docker-compose up -d historical-data-service

# 6. Start Service 3 (WebSocket Broadcaster)
docker-compose up -d websocket-broadcaster

# OR: Start everything at once
docker-compose up -d
```

---

## ✅ Verification Steps

### **1. Check all services are running:**
```bash
docker-compose ps

# Should show all containers healthy
```

### **2. Verify Service 1 REST endpoints:**
```bash
# Health check
curl http://localhost:8080/api/v1/market-data/health

# Fetch historical candles
curl "http://localhost:8080/api/v1/market-data/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000"
```

### **3. Verify Service 4 endpoints:**
```bash
# Health check
curl http://localhost:8084/actuator/health

# Get historical candles (should call Service 1 if no DB data)
curl "http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=15m&startTime=1704067200000&endTime=1704153600000&limit=100"
```

### **4. Check database:**
```bash
# Connect to PostgreSQL
docker exec -it postgres psql -U trader -d trades

# Check trades table (created by Service 2)
SELECT COUNT(*) FROM trades;

# Check candles table (created by Service 4)
SELECT COUNT(*) FROM candles;
```

### **5. Check logs:**
```bash
# Service 1 logs
docker-compose logs -f market-data-producer

# Service 4 logs
docker-compose logs -f historical-data-service
```

---

## 🔄 Data Flow Verification

### **Test End-to-End Flow:**

1. **Live Data Flow:**
```
Binance → Service 1 → Kafka → Service 2 → PostgreSQL (trades)
```

2. **Historical Query Flow:**
```
Frontend → Service 4 → Check Cache → Check DB → Aggregate Trades → Call Service 1 → Return Data
```

3. **Service 1 → Service 4 Communication:**
```bash
# Service 4 should call Service 1 when data is missing
# Check Service 4 logs for:
"Fetching historical candles from Service 1: BTCUSDT, interval=1h"

# Check Service 1 logs for:
"Fetching Binance klines: BTCUSDT, interval=1h"
```

---

## 🐛 Troubleshooting

### **Issue: Service 4 can't connect to Service 1**
```bash
# Check network
docker network inspect trading-platform_trading-network

# Test connectivity
docker exec historical-data-service ping market-data-producer

# Check Service 1 is exposing port 8080
docker exec market-data-producer netstat -tuln | grep 8080
```

### **Issue: Flyway migration fails**
```bash
# Service 2 created trades table, Service 4 should skip it
# Check Flyway table
docker exec -it postgres psql -U trader -d trades -c "SELECT * FROM flyway_schema_history;"

# If needed, repair
docker exec historical-data-service ./gradlew flywayRepair
```

### **Issue: Service 4 returns empty data**
```bash
# Check if trades exist in DB
docker exec -it postgres psql -U trader -d trades -c "SELECT COUNT(*) FROM trades;"

# If no trades, wait for Service 1 & 2 to populate data
# Or call Service 1 endpoint directly to fetch from Binance
```

---

## 📊 Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│                  COMPLETE DATA FLOW                          │
└─────────────────────────────────────────────────────────────┘

REAL-TIME PATH:
  Binance WebSocket
      ↓
  [Service 1: Market Data Producer]
      ↓ Kafka (trade-events)
  [Service 2: Trade Processor]
      ↓ PostgreSQL (trades)
      ↓ Kafka (processed-trades)
  [Service 3: WebSocket] → Frontend

HISTORICAL PATH:
  Frontend/Service 3
      ↓ REST API
  [Service 4: Historical Data]
      ├─→ Redis (cache check)
      ├─→ PostgreSQL (query trades)
      ├─→ Aggregate (create candles)
      └─→ [Service 1] → Binance API (backfill)
            ↓
      Return Data → Frontend
```

---

## 🎯 Key Benefits Achieved

✅ **No Code Duplication**: Service 1 handles all Binance API calls  
✅ **Shared Infrastructure**: All services use same Postgres, Redis, Kafka  
✅ **Clean Separation**: Each service has single responsibility  
✅ **Efficient Caching**: Multi-layer cache (Redis → DB → External)  
✅ **Scalable**: Services can scale independently

---

## 📝 Files Summary

### **Files YOU Need to Create:**

**In Service 1 (market-data/):**
- `controller/HistoricalDataController.kt` ← ADD
- `service/HistoricalDataService.kt` ← ADD

**In Service 4 (historical-service/):**
- Everything (new service)

### **Files to DELETE:**
- `historical-service/src/.../external/BinanceDataProvider.kt`
- `historical-service/src/.../external/AlphaVantageDataProvider.kt`
- `historical-service/src/.../external/ExternalDataFacade.kt`

### **Files to UPDATE:**
- `docker-compose.yml` ← Use provided version
- `market-data/build.gradle.kts` ← Add WebFlux dependency

---

## 🚀 Ready to Deploy!

Once all files are in place:

```bash
# Build and start everything
docker-compose up --build -d

# Watch logs
docker-compose logs -f

# Access services
# Service 1: http://localhost:8080
# Service 2: http://localhost:8081
# Service 3: http://localhost:8082
# Service 4: http://localhost:8084
# Kafka UI: http://localhost:8090
```

**Your trading platform is now complete!** 🎉