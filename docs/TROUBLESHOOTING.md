# 🔧 Troubleshooting Guide

Common issues and solutions for the trading platform.

---

## Quick Diagnostics

### **Check System Health**

```bash
# Check all containers
docker-compose ps

# Check logs for all services
docker-compose logs --tail=100

# Check specific service
docker-compose logs -f historical-data-service

# Check resource usage
docker stats
```

---

## Common Issues

### **Issue 1: Services Won't Start**

**Symptoms:**
- Containers exit immediately
- "Connection refused" errors
- Services stuck in "starting" state

**Diagnosis:**

```bash
# Check logs
docker-compose logs <service-name>

# Check if ports are already in use
sudo lsof -i :8080  # Service 1
sudo lsof -i :8081  # Service 2
sudo lsof -i :8082  # Service 3
sudo lsof -i :8084  # Service 4
sudo lsof -i :5432  # PostgreSQL
sudo lsof -i :6379  # Redis
sudo lsof -i :9092  # Kafka
```

**Solutions:**

```bash
# Solution 1: Stop conflicting processes
kill -9 <PID>

# Solution 2: Change ports in docker-compose.yml
# Edit ports mapping: "8085:8084" instead of "8084:8084"

# Solution 3: Clean restart
docker-compose down -v
docker-compose up -d --build

# Solution 4: Check Docker resources
# Docker Desktop → Settings → Resources
# Increase CPU: 4+ cores
# Increase Memory: 8+ GB
```

---

### **Issue 2: No Data Flowing Through System**

**Symptoms:**
- No trades in database
- WebSocket not broadcasting
- Historical API returns empty data

**Diagnosis:**

```bash
# 1. Check if Service 1 is running
curl http://localhost:8080/api/v1/market-data/health

# 2. Check Kafka topics
docker exec kafka kafka-topics.sh --list --bootstrap-server localhost:9092

# Should see:
# - trade-events
# - processed-trades

# 3. Check Kafka messages
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic trade-events \
  --from-beginning \
  --max-messages 10

# 4. Check database
docker exec -it postgres psql -U trader -d trades -c "SELECT COUNT(*) FROM trades;"

# 5. Check Service 1 logs for connection errors
docker-compose logs market-data-producer | grep -i "error\|exception"
```

**Solutions:**

```bash
# Solution 1: Service 1 can't connect to Binance
# Check internet connection
ping api.binance.com

# Check if Binance is blocking your IP
# Try using VPN or different network

# Solution 2: Kafka consumer not consuming
# Restart Service 2
docker-compose restart trade-processor

# Check consumer group lag
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group trade-processor-group \
  --describe

# Solution 3: Database connection issues
# Check PostgreSQL is running
docker exec postgres pg_isready -U trader

# Check connection from service
docker exec trade-processor nc -zv postgres 5432
```

---

### **Issue 3: High Memory Usage**

**Symptoms:**
- Containers being killed (OOMKilled)
- System becomes slow
- Docker using 10+ GB RAM

**Diagnosis:**

```bash
# Check memory usage
docker stats

# Check Java heap usage
docker exec historical-data-service jcmd 1 VM.native_memory summary

# Check for memory leaks
docker exec historical-data-service jmap -heap 1
```

**Solutions:**

```bash
# Solution 1: Increase Docker memory limit
# Docker Desktop → Settings → Resources → Memory: 8GB+

# Solution 2: Reduce JVM heap size
# Add to Dockerfile:
ENV JAVA_OPTS="-Xms512m -Xmx1g"

# Solution 3: Limit container resources
# docker-compose.yml:
deploy:
  resources:
    limits:
      memory: 2G

# Solution 4: Enable garbage collection logging
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+PrintGCDetails -Xlog:gc*:file=/tmp/gc.log"

# Solution 5: Reduce connection pool sizes
# application.yaml:
spring:
  r2dbc:
    pool:
      max-size: 20  # Reduce from 50
```

---

### **Issue 4: Slow Query Performance**

**Symptoms:**
- Historical API taking 5+ seconds
- Database CPU at 100%
- Timeout errors

**Diagnosis:**

```sql
-- Check slow queries
docker exec -it postgres psql -U trader -d trades

SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;

-- Check missing indexes
SELECT schemaname, tablename, attname
FROM pg_stats
WHERE schemaname NOT IN ('pg_catalog', 'information_schema')
AND n_distinct = -1
AND correlation < 0.1;

-- Check table sizes
SELECT
  schemaname,
  tablename,
  pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

**Solutions:**

```sql
-- Solution 1: Add missing indexes
CREATE INDEX CONCURRENTLY idx_trades_symbol_timestamp 
  ON trades(symbol, trade_timestamp DESC);

CREATE INDEX CONCURRENTLY idx_candles_lookup 
  ON candles(symbol, interval, open_time DESC);

-- Solution 2: Analyze tables
ANALYZE trades;
ANALYZE candles;

-- Solution 3: Vacuum old data
VACUUM ANALYZE trades;

-- Solution 4: Partition large tables
-- See ARCHITECTURE.md for partitioning strategy

-- Solution 5: Increase cache
ALTER SYSTEM SET shared_buffers = '2GB';
ALTER SYSTEM SET effective_cache_size = '6GB';
-- Restart PostgreSQL
docker-compose restart postgres
```

---

### **Issue 5: WebSocket Connections Dropping**

**Symptoms:**
- Frontend disconnects every few minutes
- "Connection lost" errors
- No real-time updates

**Diagnosis:**

```bash
# Check Service 3 logs
docker-compose logs websocket-broadcaster | grep -i "disconnect\|close"

# Check active connections
docker exec websocket-broadcaster netstat -an | grep :8082 | grep ESTABLISHED | wc -l

# Test WebSocket manually
wscat -c ws://localhost:8082/ws/trades
# Should see continuous trade messages
```

**Solutions:**

```bash
# Solution 1: Increase timeout
# application.yaml (Service 3):
spring:
  websocket:
    idle-timeout: 600000  # 10 minutes

# Solution 2: Implement heartbeat
# Send ping/pong every 30 seconds

# Solution 3: Check load balancer timeout (if using nginx/ALB)
# Nginx example:
proxy_read_timeout 300s;
proxy_send_timeout 300s;

# Solution 4: Check firewall rules
# Some firewalls drop idle connections after 60 seconds

# Solution 5: Add reconnection logic in frontend
const connectWebSocket = () => {
  const ws = new WebSocket('ws://localhost:8082/ws/trades');
  
  ws.onclose = () => {
    console.log('Disconnected, reconnecting in 5s...');
    setTimeout(connectWebSocket, 5000);
  };
};
```

---

### **Issue 6: Kafka Consumer Lag**

**Symptoms:**
- Delayed real-time updates
- Service 2 falling behind
- Growing consumer lag

**Diagnosis:**

```bash
# Check consumer lag
docker exec kafka kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --group trade-processor-group \
  --describe

# Look for LAG column > 1000

# Check partition distribution
docker exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --topic trade-events
```

**Solutions:**

```bash
# Solution 1: Increase consumer instances
docker-compose up -d --scale trade-processor=3

# Solution 2: Increase Kafka partitions
docker exec kafka kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --alter \
  --topic trade-events \
  --partitions 6

# Solution 3: Increase batch size
# application.yaml:
spring:
  kafka:
    consumer:
      max-poll-records: 500  # Increase from 100

# Solution 4: Optimize processing
# Use batch inserts instead of single inserts

# Solution 5: Check if database is bottleneck
# See "Slow Query Performance" section
```

---

### **Issue 7: Service 4 Returns Empty Results**

**Symptoms:**
- `/candles` endpoint returns empty array
- Historical data missing
- `count: 0` in response

**Diagnosis:**

```bash
# 1. Check if trades exist in database
docker exec -it postgres psql -U trader -d trades -c \
  "SELECT COUNT(*) FROM trades WHERE symbol = 'BTCUSDT';"

# 2. Check if candles are being aggregated
docker exec -it postgres psql -U trader -d trades -c \
  "SELECT COUNT(*) FROM candles WHERE symbol = 'BTCUSDT';"

# 3. Check Service 4 logs
docker-compose logs historical-data-service | grep -i "error\|aggregat"

# 4. Check if Service 1 historical endpoint works
curl "http://localhost:8080/api/v1/market-data/historical/candles?symbol=BTCUSDT&interval=1h&startTime=1704067200000&endTime=1704153600000"

# 5. Check time range
# Make sure endTime > startTime
# Make sure times are in milliseconds (13 digits)
```

**Solutions:**

```bash
# Solution 1: Wait for data to accumulate
# New deployment needs 15-30 minutes for data

# Solution 2: Trigger manual aggregation
docker exec historical-data-service \
  psql -U trader -d trades -c "SELECT auto_aggregate_recent_candles();"

# Solution 3: Check time range is correct
# Use recent timestamps:
START_TIME=$(($(date +%s)*1000-86400000))  # 24 hours ago
END_TIME=$(($(date +%s)*1000))              # now

curl "http://localhost:8084/api/v1/historical/candles?symbol=BTCUSDT&interval=1h&startTime=$START_TIME&endTime=$END_TIME"

# Solution 4: Check if Service 1 can reach Binance
docker exec market-data-producer curl -I https://api.binance.com

# Solution 5: Clear cache and retry
docker exec redis redis-cli FLUSHALL
```

---

### **Issue 8: Database Connection Pool Exhausted**

**Symptoms:**
- "Connection pool exhausted" errors
- Timeouts on database queries
- Services hanging

**Diagnosis:**

```sql
-- Check active connections
docker exec -it postgres psql -U trader -d trades -c \
  "SELECT count(*) FROM pg_stat_activity WHERE datname = 'trades';"

-- Check waiting queries
SELECT pid, usename, state, wait_event, query
FROM pg_stat_activity
WHERE datname = 'trades' AND state = 'active';
```

**Solutions:**

```bash
# Solution 1: Increase connection pool
# application.yaml:
spring:
  r2dbc:
    pool:
      initial-size: 20
      max-size: 100  # Increase from 50
      max-idle-time: 30m

# Solution 2: Increase PostgreSQL max connections
docker exec -it postgres psql -U trader -d trades -c \
  "ALTER SYSTEM SET max_connections = 200;"
docker-compose restart postgres

# Solution 3: Find and kill long-running queries
SELECT pg_terminate_backend(pid)
FROM pg_stat_activity
WHERE state = 'active' AND query_start < NOW() - INTERVAL '5 minutes';

# Solution 4: Add connection timeout
spring:
  r2dbc:
    pool:
      max-acquire-time: 30s
```

---

### **Issue 9: Flyway Migration Fails**

**Symptoms:**
- Service 4 won't start
- "Flyway validation failed" error
- Migration checksum mismatch

**Diagnosis:**

```bash
# Check Flyway schema history
docker exec -it postgres psql -U trader -d trades -c \
  "SELECT * FROM flyway_schema_history ORDER BY installed_rank;"

# Check for failed migrations
docker exec -it postgres psql -U trader -d trades -c \
  "SELECT * FROM flyway_schema_history WHERE success = false;"

# Check Service 4 logs
docker-compose logs historical-data-service | grep -i "flyway\|migration"
```

**Solutions:**

```bash
# Solution 1: Repair Flyway (if checksum changed)
# Add to application.yaml temporarily:
spring:
  flyway:
    repair: true

# Restart service
docker-compose restart historical-data-service

# Remove repair flag after success

# Solution 2: Baseline existing database
spring:
  flyway:
    baseline-on-migrate: true
    baseline-version: 0

# Solution 3: Manual fix
docker exec -it postgres psql -U trader -d trades -c \
  "DELETE FROM flyway_schema_history WHERE version = '2';"

# Then restart service

# Solution 4: Clean slate (WARNING: deletes all data)
docker-compose down -v
docker-compose up -d
```

---

### **Issue 10: Redis Out of Memory**

**Symptoms:**
- Redis returning errors
- Cache not working
- OOM errors in Redis logs

**Diagnosis:**

```bash
# Check Redis memory
docker exec redis redis-cli INFO memory

# Check keyspace
docker exec redis redis-cli INFO keyspace

# Check eviction policy
docker exec redis redis-cli CONFIG GET maxmemory-policy
```

**Solutions:**

```bash
# Solution 1: Increase Redis memory
# docker-compose.yml:
redis:
  command: redis-server --maxmemory 2gb

# Solution 2: Enable LRU eviction
docker exec redis redis-cli CONFIG SET maxmemory-policy allkeys-lru

# Solution 3: Clear old keys
docker exec redis redis-cli --scan --pattern "candles:*" | \
  xargs -L 1000 docker exec redis redis-cli DEL

# Solution 4: Reduce TTL
# RedisCacheService.kt:
private val CANDLE_TTL = Duration.ofMinutes(5)  # Reduce from 15

# Solution 5: Use Redis persistence
# docker-compose.yml:
redis:
  command: redis-server --appendonly yes
  volumes:
    - redis-data:/data
```

---

## Debugging Tips

### **Enable Debug Logging**

```yaml
# application.yaml
logging:
  level:
    root: DEBUG
    com.trading: DEBUG
    org.springframework.kafka: DEBUG
    org.springframework.r2dbc: DEBUG
```

### **JVM Remote Debugging**

```bash
# Add to Dockerfile or docker-compose
JAVA_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"

# Expose port in docker-compose.yml
ports:
  - "5005:5005"

# Connect IntelliJ IDEA:
# Run → Edit Configurations → + → Remote JVM Debug
# Host: localhost, Port: 5005
```

### **Database Query Logging**

```yaml
# application.yaml
logging:
  level:
    org.springframework.r2dbc: DEBUG

# This will log all SQL queries
```

### **Kafka Message Inspection**

```bash
# Consume from beginning
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic processed-trades \
  --from-beginning \
  --max-messages 10

# With key and timestamp
docker exec kafka kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic processed-trades \
  --property print.key=true \
  --property print.timestamp=true
```

---

## Performance Troubleshooting

### **Slow Startup**

```bash
# Measure startup time
time docker-compose up -d historical-data-service

# Check what's taking time
docker-compose logs historical-data-service | grep "Started"
```

### **High CPU Usage**

```bash
# Check which process
docker stats

# Java thread dump
docker exec historical-data-service jstack 1 > thread-dump.txt

# Find hot methods
docker exec historical-data-service jcmd 1 JFR.start duration=60s filename=/tmp/profile.jfr
```

---

## Emergency Procedures

### **Complete System Reset**

```bash
# WARNING: This deletes all data!

# 1. Stop everything
docker-compose down -v

# 2. Remove all images
docker-compose rm -f

# 3. Clean build
./gradlew clean build

# 4. Fresh start
docker-compose up -d --build

# 5. Verify
docker-compose ps
curl http://localhost:8084/actuator/health
```

### **Rollback to Previous Version**

```bash
# 1. Stop current version
docker-compose down

# 2. Check out previous version
git checkout <previous-commit>

# 3. Rebuild
docker-compose build

# 4. Start
docker-compose up -d

# 5. Restore database if needed
docker exec -i postgres psql -U trader trades < backup.sql
```

---

## Getting Help

If none of these solutions work:

1. **Check logs** with debug level enabled
2. **Search existing issues** on GitHub
3. **Create detailed issue** with:
    - Exact error message
    - Steps to reproduce
    - Docker logs
    - System info (OS, Docker version, RAM)
4. **Contact support** (see README for contact info)

---

**Most issues can be solved with a clean restart and checking logs! 🔧**