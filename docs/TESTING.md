# 🧪 Testing Guide

Comprehensive testing strategies for the trading platform.

---

## Testing Strategy

### **Testing Pyramid**

```
        /\
       /  \      E2E Tests (10%)
      /────\     
     /      \    Integration Tests (30%)
    /────────\   
   /          \  Unit Tests (60%)
  /────────────\ 
```

---

## Unit Tests

### **Service 4 Example: HistoricalDataService Tests**

```kotlin
// src/test/kotlin/com/trading/historical_service/service/HistoricalDataServiceTest.kt

@ExtendWith(MockKExtension::class)
class HistoricalDataServiceTest {

    @MockK
    private lateinit var candleRepository: CandleRepository
    
    @MockK
    private lateinit var tradeRepository: TradeRepository
    
    @MockK
    private lateinit var cacheService: RedisCacheService
    
    @MockK
    private lateinit var service1Client: Service1Client
    
    @InjectMockKs
    private lateinit var historicalDataService: HistoricalDataService

    @Test
    fun `should return candles from cache when available`() = runTest {
        // Given
        val request = HistoricalQueryRequest(
            symbol = "BTCUSDT",
            interval = CandleInterval.FIFTEEN_MINUTES,
            startTime = 1704067200000,
            endTime = 1704153600000
        )
        
        val cachedCandles = listOf(
            Candle(
                symbol = "BTCUSDT",
                interval = CandleInterval.FIFTEEN_MINUTES,
                openTime = 1704067200000,
                closeTime = 1704068100000,
                open = 45000.0,
                high = 45100.0,
                low = 44900.0,
                close = 45050.0,
                volume = 100.0,
                trades = 50,
                buyVolume = 60.0,
                notionalValue = 4500000.0
            )
        )
        
        coEvery {
            cacheService.getCandlesInRange(any(), any(), any(), any())
        } returns cachedCandles

        // When
        val response = historicalDataService.getHistoricalCandles(request)

        // Then
        assertEquals(1, response.count)
        assertEquals("BTCUSDT", response.symbol)
        assertTrue(response.fromCache)
        assertFalse(response.fromExternalApi)
        assertEquals(cachedCandles, response.candles)
        
        coVerify(exactly = 1) {
            cacheService.getCandlesInRange(any(), any(), any(), any())
        }
        coVerify(exactly = 0) {
            candleRepository.findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(any(), any(), any(), any())
        }
    }

    @Test
    fun `should fallback to database when cache is empty`() = runTest {
        // Given
        val request = HistoricalQueryRequest(
            symbol = "ETHUSDT",
            interval = CandleInterval.ONE_HOUR,
            startTime = 1704067200000,
            endTime = 1704153600000
        )
        
        coEvery {
            cacheService.getCandlesInRange(any(), any(), any(), any())
        } returns emptyList()
        
        val dbCandles = flowOf(
            CandleEntity(
                symbol = "ETHUSDT",
                interval = "1h",
                openTime = 1704067200000,
                closeTime = 1704070800000,
                open = 2500.0,
                high = 2510.0,
                low = 2490.0,
                close = 2505.0,
                volume = 200.0,
                trades = 100,
                buyVolume = 120.0,
                notionalValue = 500000.0
            )
        )
        
        coEvery {
            candleRepository.findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(any(), any(), any(), any())
        } returns dbCandles

        // When
        val response = historicalDataService.getHistoricalCandles(request)

        // Then
        assertEquals(1, response.count)
        assertFalse(response.fromCache)
        assertFalse(response.fromExternalApi)
        
        coVerify {
            cacheService.getCandlesInRange(any(), any(), any(), any())
            candleRepository.findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(any(), any(), any(), any())
        }
    }

    @Test
    fun `should call Service 1 when no local data available`() = runTest {
        // Given
        val request = HistoricalQueryRequest(
            symbol = "BNBUSDT",
            interval = CandleInterval.FIVE_MINUTES,
            startTime = 1704067200000,
            endTime = 1704153600000
        )
        
        coEvery { cacheService.getCandlesInRange(any(), any(), any(), any()) } returns emptyList()
        coEvery { candleRepository.findBySymbolAndIntervalAndOpenTimeBetweenOrderByOpenTimeAsc(any(), any(), any(), any()) } returns emptyFlow()
        coEvery { customCandleRepository.aggregateTradesToCandles(any(), any(), any(), any()) } returns emptyFlow()
        
        val externalCandles = listOf(
            Candle(
                symbol = "BNBUSDT",
                interval = CandleInterval.FIVE_MINUTES,
                openTime = 1704067200000,
                closeTime = 1704067500000,
                open = 300.0,
                high = 305.0,
                low = 298.0,
                close = 303.0,
                volume = 50.0,
                trades = 25,
                buyVolume = 30.0,
                notionalValue = 15000.0
            )
        )
        
        coEvery {
            service1Client.fetchHistoricalCandles(any(), any(), any(), any(), any())
        } returns externalCandles

        // When
        val response = historicalDataService.getHistoricalCandles(request)

        // Then
        assertEquals(1, response.count)
        assertTrue(response.fromExternalApi)
        
        coVerify {
            service1Client.fetchHistoricalCandles(any(), any(), any(), any(), any())
        }
    }
}
```

### **Run Unit Tests**

```bash
# Run all unit tests
./gradlew test

# Run specific test class
./gradlew test --tests HistoricalDataServiceTest

# Run with coverage
./gradlew test jacocoTestReport

# View coverage report
open build/reports/jacoco/test/html/index.html
```

---

## Integration Tests

### **Testing with Testcontainers**

```kotlin
// src/test/kotlin/com/trading/historical_service/integration/HistoricalDataIntegrationTest.kt

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class HistoricalDataIntegrationTest {

    @Container
    val postgres = PostgreSQLContainer<Nothing>("postgres:15").apply {
        withDatabaseName("trades")
        withUsername("trader")
        withPassword("traderpass")
    }

    @Container
    val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
        withExposedPorts(6379)
    }

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @DynamicPropertySource
    companion object {
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.r2dbc.url") { 
                "r2dbc:postgresql://${postgres.host}:${postgres.firstMappedPort}/trades" 
            }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.firstMappedPort }
        }
    }

    @Test
    fun `should return historical candles via REST API`() {
        webTestClient.get()
            .uri {
                it.path("/api/v1/historical/candles")
                    .queryParam("symbol", "BTCUSDT")
                    .queryParam("interval", "15m")
                    .queryParam("startTime", 1704067200000)
                    .queryParam("endTime", 1704153600000)
                    .queryParam("limit", 100)
                    .build()
            }
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.symbol").isEqualTo("BTCUSDT")
            .jsonPath("$.interval").isEqualTo("15m")
            .jsonPath("$.candles").isArray
    }

    @Test
    fun `should return 400 for invalid interval`() {
        webTestClient.get()
            .uri("/api/v1/historical/candles?symbol=BTCUSDT&interval=INVALID&startTime=1&endTime=2")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.error").exists()
    }
}
```

### **Run Integration Tests**

```bash
# Run integration tests
./gradlew integrationTest

# Or with specific profile
./gradlew integrationTest -Dspring.profiles.active=test
```

---

## End-to-End Tests

### **Full System Test**

```bash
#!/bin/bash
# tests/e2e/full-system-test.sh

set -e

echo "Starting full system test..."

# 1. Start all services
docker-compose up -d
sleep 30  # Wait for services to be ready

# 2. Test Service 1 (Market Data Producer)
echo "Testing Service 1..."
curl -f http://localhost:8080/api/v1/market-data/health || exit 1

# 3. Wait for data to flow through Kafka
echo "Waiting for data pipeline..."
sleep 10

# 4. Test Service 2 (Check database has trades)
echo "Testing Service 2 (database)..."
TRADE_COUNT=$(docker exec postgres psql -U trader -d trades -t -c "SELECT COUNT(*) FROM trades;")
if [ "$TRADE_COUNT" -gt 0 ]; then
    echo "✓ Trades found in database: $TRADE_COUNT"
else
    echo "✗ No trades in database"
    exit 1
fi

# 5. Test Service 3 (WebSocket)
echo "Testing Service 3 (WebSocket)..."
timeout 5s wscat -c ws://localhost:8082/ws/trades > /tmp/ws-output.txt || true
if grep -q "BTCUSDT" /tmp/ws-output.txt; then
    echo "✓ WebSocket receiving trades"
else
    echo "✗ WebSocket not receiving trades"
    exit 1
fi

# 6. Test Service 4 (Historical Data)
echo "Testing Service 4..."
RESPONSE=$(curl -s "http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000")
if echo "$RESPONSE" | jq -e '.candles | length > 0' > /dev/null; then
    echo "✓ Historical data API working"
else
    echo "✗ Historical data API failed"
    exit 1
fi

# 7. Test Service 1 → Service 4 integration
echo "Testing Service 1 ← Service 4 integration..."
RESPONSE=$(curl -s "http://localhost:8080/api/v1/market-data/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000")
if echo "$RESPONSE" | jq -e '.candles | length > 0' > /dev/null; then
    echo "✓ Service 1 historical endpoint working"
else
    echo "✗ Service 1 historical endpoint failed"
    exit 1
fi

echo "✅ All tests passed!"
```

### **Run E2E Tests**

```bash
chmod +x tests/e2e/full-system-test.sh
./tests/e2e/full-system-test.sh
```

---

## Load Testing

### **Using K6 (Load Testing Tool)**

```javascript
// tests/load/candles-load-test.js

import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '1m', target: 10 },   // Ramp up to 10 users
    { duration: '3m', target: 50 },   // Ramp up to 50 users
    { duration: '2m', target: 100 },  // Ramp up to 100 users
    { duration: '2m', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],   // Error rate under 1%
  },
};

export default function () {
  const startTime = Date.now() - 86400000;  // 24 hours ago
  const endTime = Date.now();
  
  const url = `http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=15m&startTime=${startTime}&endTime=${endTime}&limit=96`;
  
  const response = http.get(url);
  
  check(response, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
    'has candles': (r) => JSON.parse(r.body).candles.length > 0,
  });
  
  sleep(1);
}
```

### **Run Load Tests**

```bash
# Install k6
brew install k6  # macOS
# or
sudo apt install k6  # Ubuntu

# Run load test
k6 run tests/load/candles-load-test.js

# With custom duration
k6 run --duration 10m tests/load/candles-load-test.js

# Save results
k6 run --out json=results.json tests/load/candles-load-test.js
```

---

## Performance Testing

### **Database Query Performance**

```sql
-- Explain analyze queries
EXPLAIN ANALYZE
SELECT * FROM trades
WHERE symbol = 'BTCUSDT'
  AND trade_timestamp BETWEEN 1704067200000 AND 1704153600000
ORDER BY trade_timestamp DESC
LIMIT 1000;

-- Check index usage
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read, idx_tup_fetch
FROM pg_stat_user_indexes
WHERE tablename = 'trades';

-- Check slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

### **JVM Performance Profiling**

```bash
# Enable JMX monitoring
java -Dcom.sun.management.jmxremote \
     -Dcom.sun.management.jmxremote.port=9999 \
     -Dcom.sun.management.jmxremote.authenticate=false \
     -Dcom.sun.management.jmxremote.ssl=false \
     -jar historical-service.jar

# Connect with VisualVM or JConsole
visualvm
# Connect to localhost:9999
```

---

## Test Data Generation

### **Generate Mock Trades**

```kotlin
// tests/utils/TestDataGenerator.kt

object TestDataGenerator {
    fun generateTrades(
        symbol: String = "BTCUSDT",
        count: Int = 1000,
        startTime: Long = System.currentTimeMillis() - 86400000
    ): List<TradeEntity> {
        var price = 45000.0
        return (0 until count).map { i ->
            // Random walk price
            price += Random.nextDouble(-100.0, 100.0)
            
            TradeEntity(
                id = i.toLong(),
                tradeId = "test_trade_$i",
                symbol = symbol,
                exchange = "BINANCE",
                price = price,
                volume = Random.nextDouble(0.1, 10.0),
                tradeTimestamp = startTime + (i * 1000),
                side = if (Random.nextBoolean()) "BUY" else "SELL",
                processedAt = startTime + (i * 1000) + 10,
                latencyMs = 10
            )
        }
    }
}
```

### **Populate Test Database**

```bash
# Run script to populate database with test data
./gradlew bootRun --args='--spring.profiles.active=test --populate-test-data=true'
```

---

## CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/test.yml

name: Test Suite

on: [push, pull_request]

jobs:
  unit-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run unit tests
        run: ./gradlew test
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  integration-tests:
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:15
        env:
          POSTGRES_PASSWORD: traderpass
      redis:
        image: redis:7
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '17'
      - name: Run integration tests
        run: ./gradlew integrationTest

  e2e-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Start services
        run: docker-compose up -d
      - name: Run E2E tests
        run: ./tests/e2e/full-system-test.sh
      - name: Stop services
        run: docker-compose down
```

---

## Test Coverage Goals

| Component | Target Coverage |
|-----------|----------------|
| Services | 80%+ |
| Repositories | 70%+ |
| Controllers | 90%+ |
| Utilities | 85%+ |

### **Check Coverage**

```bash
./gradlew test jacocoTestReport
open build/reports/jacoco/test/html/index.html
```

---

## Manual Testing Checklist

### **Before Each Release:**

- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] E2E test passes
- [ ] Load test with 100 concurrent users
- [ ] Memory leak check (run for 24 hours)
- [ ] Database migration test
- [ ] Rollback test
- [ ] Security scan with OWASP ZAP
- [ ] API documentation updated
- [ ] Performance benchmarks recorded

---

**Happy Testing! 🧪**