# Local Development Guide

## Status

This document describes the currently supported Docker development modes for the trading-app project.

The complete backend and frontend have not yet been verified as a single Docker Compose deployment.

## Prerequisites

- Docker Desktop with Docker Compose v2
- Java/JDK compatible with the Gradle project, for running backend services locally
- Node.js and npm for running the frontend locally
- Windows PowerShell or another terminal

## Mode 1 — Infrastructure only (recommended)

Start the three infrastructure services:

```powershell
docker compose up -d
```

Services:

| Service | Port |
|---|---|
| Kafka | 9092 / 29092 |
| PostgreSQL | 5432 |
| Redis | 6379 |

Verify:

```powershell
docker compose ps
docker stats --no-stream
```

Backend applications and the frontend can be run separately from the IDE or terminal once their local dependencies and Kafka connectivity have been verified.

## Mode 2 — Infrastructure with Kafka UI

```powershell
docker compose --profile tools up -d
```

Kafka UI is available at:

http://localhost:8090

Kafka UI is optional and does not need to run during normal development.

## Mode 3 — Infrastructure with market-data producer

```powershell
docker compose --profile producer up -d --build
```

The producer runs inside Docker and connects to Kafka using `kafka:9092`.

Producer port: `8080`.

## Mode 4 — Producer with Kafka UI

```powershell
docker compose --profile producer --profile tools up -d --build
```

This starts the three infrastructure services, the producer and Kafka UI.

## Current Docker Compose limitations

The repository contains four Kotlin backend modules:

- market-data
- trade-processor
- websocket-broadcaster
- historical-service

Only market-data-producer is currently enabled as an application service in the root Compose configuration.

The other backend service definitions are commented out. The frontend is not configured as a Compose service.

Therefore, `docker compose up -d` does not start the entire trading application.

Full-stack Docker deployment remains unverified.

## Kafka connectivity

Docker containers connect to Kafka using:

`kafka:9092`

Host applications may require:

`localhost:29092`

The existing Compose configuration exposes port 29092 but does not advertise the host listener. Host-side Kafka connectivity must be verified before relying on it.

## Verify Kafka event flow

Check the topic:

```powershell
docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic trade-events
```

Check latest offset:

```powershell
docker exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic trade-events
```

If the producer is publishing, the latest offset should advance over time.

## Shutdown

Stop the current Compose project:

```powershell
docker compose down
```

This preserves named volumes by default.

**Do not use `docker compose down -v` unless you intentionally want to remove persistent project data.**

## Baseline measurements

See `milestone-results/M1-BASELINE-RESULTS.md` for measured Docker resource usage and milestone verification results.

## Known limitations

- Full-stack Docker deployment has not been verified.
- Kafka host advertised listener configuration needs review.
- Kafka broker previously experienced heartbeat timeouts.
- Only partial application resource measurements have been captured.
- Database persistence, WebSocket delivery and historical integration remain to be verified.
