# Local Development Guide

## Status

This document describes the local Docker development workflow for the trading-app project.

The seven-service backend stack has been started and verified using Docker Compose. Kafka ingestion, trade processing, PostgreSQL persistence, and consumer-group participation have been observed.

The React frontend runs separately from Docker Compose. Complete browser-to-backend integration has not yet been fully verified.

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java 21 and the included Gradle wrapper
- Node.js and npm for frontend development
- Windows PowerShell or another compatible terminal
- Git

Use Docker Desktop with sufficient resources for the services being tested. Docker container memory measurements do not include all Docker Desktop and WSL host overhead.

## Environment configuration

Create a root `.env` file for local configuration. Do not commit this file.

Example variable names:

```dotenv
POSTGRES_USER=trader
POSTGRES_PASSWORD=<local-development-password>
POSTGRES_DB=trades
ALPHA_VANTAGE_API_KEY=<optional-api-key>
```

Replace placeholders with local values.

The PostgreSQL password must match the password already configured inside an existing PostgreSQL volume. Changing `POSTGRES_PASSWORD` in `.env` does not automatically update the database user's password.

Keep production credentials out of local configuration and Git history.

## Mode 1 — Infrastructure only

For a lightweight development setup, explicitly start Kafka, PostgreSQL and Redis:

```powershell
docker compose up -d kafka postgres redis
```

Services and host ports:

| Service | Port |
|---|---|
| Kafka | 9092 / 29092 |
| PostgreSQL | 5432 |
| Redis | 6379 |

Verify:

```powershell
docker compose ps kafka postgres redis
docker stats --no-stream kafka postgres redis
```

Explicit service selection is used because services without Compose profiles may otherwise start under the default command.

## Mode 2 — Infrastructure with Kafka UI

```powershell
docker compose --profile tools up -d kafka postgres redis kafka-ui
```

Kafka UI, when enabled by the Compose configuration, is available at:

http://localhost:8090

Kafka UI is optional.

## Mode 3 — Infrastructure with market-data producer

```powershell
docker compose --profile producer up -d --build kafka postgres redis market-data-producer
```

The producer connects to Kafka using `kafka:9092`.

Producer host port: `8080`.

## Mode 4 — Full backend stack

Start the backend services:

```powershell
docker compose --profile full --profile producer up -d --build
```

Expected services:

| Service | Host port |
|---|---|
| Kafka | 9092 / 29092 |
| PostgreSQL | 5432 |
| Redis | 6379 |
| Market-data producer | 8080 |
| Trade processor | 8081 |
| WebSocket broadcaster | 8082 |
| Historical data service | 8084 |

Verify:

```powershell
docker compose --profile full --profile producer ps
```

Check resource usage:

```powershell
docker stats --no-stream
```

Startup and container readiness do not by themselves prove that the entire pipeline is operating correctly. Use the checks below.

## Kafka connectivity

Docker services use:

`kafka:9092`

Host-based applications use:

`localhost:29092`

The Kafka broker advertises separate internal and host listeners.

Check Kafka health:

```powershell
docker compose ps kafka
```

Inspect the input topic:

```powershell
docker exec kafka /opt/kafka/bin/kafka-topics.sh `
  --bootstrap-server localhost:9092 `
  --describe --topic trade-events
```

## Verify ingestion and persistence

Check the Kafka consumer group:

```powershell
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh `
  --bootstrap-server localhost:9092 `
  --describe --group trade-processor
```

Key fields:

- `CURRENT-OFFSET`: consumer group's committed position
- `LOG-END-OFFSET`: latest available Kafka offset
- `LAG`: messages not yet committed by the consumer

Check PostgreSQL trade count:

```powershell
docker exec postgres psql -U trader -d trades -c `
  "SELECT COUNT(*) AS total_trades FROM trades;"
```

With the producer enabled, repeat these checks after a short interval.

Healthy behavior means the consumer offset advances, database records increase when new trades arrive, and Kafka lag remains manageable.

A single zero-lag snapshot is useful evidence but not proof of sustained processing capacity or end-to-end exactly-once delivery.

## Application build and tests

From the project root:

```powershell
.\gradlew.bat clean build --console=plain
.\gradlew.bat test --console=plain
```

To test only the trade processor:

```powershell
.\gradlew.bat :trade-processor:test --console=plain
```

For frontend development:

```powershell
cd client-connection
npm install
npm run build
npm run lint
```

The frontend currently has known TypeScript and lint issues that must be resolved separately. A successful backend build does not imply the frontend build passes.

## Monitoring resource usage

Capture a current snapshot:

```powershell
docker stats --no-stream
```

For repeated measurements, capture CPU, memory and process counts at fixed intervals under a comparable workload.

Record whether the producer is running, the duration of the test, Kafka throughput, lag and PostgreSQL progress.

Docker's displayed per-container CPU percentage is not the same as overall Windows host CPU usage. Account separately for Docker Desktop and WSL resource consumption when evaluating total development-machine requirements.

## Troubleshooting

### Kafka consumer lag is growing

Inspect the group:

```powershell
docker exec kafka /opt/kafka/bin/kafka-consumer-groups.sh `
  --bootstrap-server localhost:9092 `
  --describe --group trade-processor
```

Compare offsets over time to determine whether the consumer is keeping up with incoming events.

If the producer publishes faster than the processor consumes, the backlog will grow.

### Kafka coordinator unavailable

During M1, Kafka experienced repeated `COORDINATOR_NOT_AVAILABLE` errors and consumer-group rebalancing.

Inspect broker logs:

```powershell
docker logs --since 10m kafka
```

Also verify Kafka storage and resources:

```powershell
docker exec kafka df -h
docker stats --no-stream kafka
```

A controlled broker restart restored consumer-group progress during the M1 investigation. However, the underlying cause of the coordinator timeouts has not been conclusively established. Do not treat restarting Kafka as a permanent fix.

### Trade processor uses excessive resources

Check:

```powershell
docker stats --no-stream trade-processor
```

The processor previously experienced high CPU and memory usage while launching asynchronous persistence work without waiting for completion.

The M1 changes introduced completion-aware processing and duplicate-safe PostgreSQL inserts.

If usage increases sharply, inspect recent application logs and Kafka lag before changing memory settings or resetting consumer offsets.

### PostgreSQL connection issues

Check database availability:

```powershell
docker compose ps postgres
```

Check existing connections:

```powershell
docker exec postgres psql -U trader -d trades -c `
  "SELECT state, COUNT(*) FROM pg_stat_activity WHERE datname='trades' GROUP BY state;"
```

Verify that Compose database credentials match the existing PostgreSQL user configuration.

## Shutdown

Stop selected application services without removing infrastructure:

```powershell
docker compose stop market-data-producer trade-processor websocket-broadcaster historical-data-service
```

Stop the complete Compose project:

```powershell
docker compose down
```

Named volumes are preserved by default.

**Do not use `docker compose down -v` unless you intentionally want to delete persistent project data.**

## M1 benchmark notes

During M1:

- A Kafka coordinator failure caused consumer-group progress to stall.
- The consumer backlog subsequently recovered to zero lag.
- Trade processing was changed to wait for PostgreSQL, Redis and Kafka operations.
- PostgreSQL uniqueness was verified.
- Trade processor memory remained approximately 375–413 MiB in several subsequent observations.
- A five-minute live-ingestion check finished with zero Kafka lag.

These observations demonstrate recovery and improved stability under the measured workloads. They do not yet constitute a fully controlled before/after resource benchmark or proof of exactly-once processing.

See `docs/milestone-results/M1-BASELINE-RESULTS.md` for the recorded baseline evidence.

## Remaining limitations

- Frontend build and lint errors remain.
- Complete browser-to-backend integration requires further verification.
- Sustained throughput at 100 trades/second has not been established by a controlled test.
- PostgreSQL persistence and Kafka output publication are not atomic.
- Kafka coordinator timeout recurrence requires monitoring.
- Full Docker Desktop/WSL host resource measurements remain to be documented.
