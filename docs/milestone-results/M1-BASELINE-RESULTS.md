# M1 — Baseline and Cleanup: Results Log

**Project:** [trading-app](https://github.com/kakarot987/trading-app)  
**Milestone tracker:** [`docs/MILESTONES.md`](https://github.com/kakarot987/trading-app/blob/main/docs/MILESTONES.md)  
**Measurement dates:** 2026-10-26 to 2026-10-27 (terminal/log times can differ by timezone)  
**Status:** In progress — resource baseline, Compose modes, and Docker application connectivity verified; repository hygiene and build/test baseline remain pending.

> This document records observations, not assumed performance guarantees. The repository was not modified by ChatGPT. Update this file after each local verification and commit/push it yourself when ready.

## 1. Host environment

| Item | Observed value |
|---|---|
| OS | Windows 11 Home Single Language (version `10.0.26200`) |
| CPU | Intel Core i7-14700HX, 20 physical cores / 28 logical processors |
| Installed RAM | 15.71 GiB as reported by PowerShell calculation (`TotalPhysicalMemory / 1GB`) |
| Docker Engine server version | 29.5.3 |
| Memory limit shown per container | 7.609 GiB (not the measured Docker/WSL overhead) |
| Startup mode | `docker compose up -d kafka postgres redis`; later market-data-producer also running in Docker |

## 2. Measurements

All measurements below are single `docker stats --no-stream` snapshots, not sustained averages or peaks. CPU percentages are Docker's per-container reports and should not be interpreted as whole-machine utilization.

### A. Infrastructure-only — initial snapshot

| Container | CPU | Memory |
|---|---:|---:|
| Kafka | 4.15% | 291.4 MiB |
| Redis | 0.40% | 4.152 MiB |
| PostgreSQL | 0.05% | 29.21 MiB |
| **Total (sum of snapshots)** | **4.60%** | **324.762 MiB** |

### B. Infrastructure-only — approximately five minutes later

| Container | CPU | Memory |
|---|---:|---:|
| Kafka | 1.16% | 315.9 MiB |
| Redis | 0.19% | 3.656 MiB |
| PostgreSQL | 0.00% | 32.39 MiB |
| **Total (sum of snapshots)** | **1.35%** | **351.946 MiB** |

Change in summed measured memory: **+27.184 MiB** between snapshots.

### C. Infrastructure + market-data producer — later snapshot

| Container | CPU | Memory |
|---|---:|---:|
| Market-data producer | 0.62% | 279.2 MiB |
| Kafka | 1.38% | 344.4 MiB |
| Redis | 0.15% | 3.902 MiB |
| PostgreSQL | 0.00% | 31.12 MiB |
| **Total (sum of snapshots)** | **2.15%** | **658.622 MiB** |

**Limitations:** Different container sets and timestamps; do not treat the difference between B and C as the isolated producer overhead. No Kafka UI, trade processor, WebSocket broadcaster, historical service, frontend, Docker Desktop/WSL host overhead, controlled load, event-rate timing, or peak memory was measured in this snapshot.

## 3. Functional observations

- `docker top market-data-producer` showed a running `java -jar app.jar` process (container process listing). Windows `Get-Process java` found no host Java process; that is consistent with the producer running in Docker.
- Producer logs showed Spring Boot startup in **3.711 seconds**, an initialized Kafka idempotent producer, and repeated stock quote polling for AAPL, GOOGL, MSFT, and TSLA.
- Some quotes were marked `MOCK`, others `ALPHA_VANTAGE`. Successful polling is not proof every quote was live market data.
- Producer initially logged `UNKNOWN_TOPIC_OR_PARTITION` for `trade-events`; Kafka subsequently created the topic.
- Topic inspection reported `trade-events`, **1 partition**, replication factor **1**, leader **1**, ISR **1** (single-broker development configuration).
- Two offset readings were `trade-events:0:47460` and `trade-events:0:49692`, an increase of **2,232**. This confirms Kafka appended new records between readings; no precise throughput rate can be calculated without the elapsed time.
- Earlier Kafka logs showed intermittent KRaft broker/controller heartbeat request timeouts around **05:24–05:30 UTC**. A subsequent `docker logs --since 2m kafka ... Select-String ...` check produced **no matching lines**. Root cause and recurrence remain unverified.
- PostgreSQL persistence and downstream Kafka consumption were subsequently verified in Task 3 (below). Browser/frontend delivery remains unverified (M2).

## 4. M1 tracker

| M1 requirement | State | Evidence / next action |
|---|---|---|
| Capture baseline CPU/RAM | **Partial** | Infrastructure idle, producer+infra, and seven-container workload snapshots captured; still need reproducible defined-load durations and sustained/peak figures. |
| Document infrastructure-only mode and Compose limitations | **Needs refresh** | `docs/LOCAL-DEVELOPMENT.md` exists and default/optional profiles were validated; guide still describes outdated Kafka host-listener and full-backend limitations, while current user Compose runs seven containers. |
| Verify environment variables and broker listeners | **Substantially verified** | Docker internal `kafka:9092` and host `localhost:29092` listeners configured; JVM timezone UTC; Docker Flyway/R2DBC PostgreSQL URLs must use `postgres:5432`; Kafka consumer group active. Remaining per-service settings/functional checks below. |
| Untrack build/IDE artifacts; audit `.env` | **Pending** | Review tracked files; do not remove source or Gradle wrapper; check exposed secrets. |
| Capture build/test baseline | **Pending** | Run builds/tests and record pass/fail plus environment. |

## 4A. Task 2 — Compose development mode verification (2026-10-26)

**Local user-reported results (historical Task 2 snapshot; later progress appears in Task 3):**

| Check | Result | Evidence |
|---|---|---|
| Compose configuration parses | **Passed** | `docker compose config --quiet` returned without error. |
| Default service selection | **Passed** | `docker compose config --services` listed only `kafka`, `postgres`, `redis`. |
| Combined optional profiles | **Passed** | `docker compose --profile producer --profile tools config --services` listed `redis`, `kafka`, `kafka-ui`, `market-data-producer`, `postgres`. |
| Infrastructure-only startup | **Passed (startup)** | `docker compose up -d --no-build` reported all three infrastructure containers started. |
| Container status | **Partial** | `docker compose ps` reported Kafka `Up 7 seconds (health: starting)`, PostgreSQL Up, Redis Up. Kafka health had not yet transitioned to healthy. |
| Local development guide | **Not verified** | The proposed `docs/LOCAL-DEVELOPMENT.md` contents were supplied, but the user has not confirmed the document was created locally. |
| Full-stack Compose mode | **Not implemented / unverified** | Trade processor, broadcaster and historical service are still commented out in the previously reviewed Compose file; frontend is not included. |

**Interpretation:** The lightweight Compose mode and opt-in profiles have passed syntax and service-selection verification. This is not evidence that an entire trading application boots or that Kafka's health check completes. No Docker Desktop/WSL resource comparison after the profile changes has been measured yet.

**To close Task 2:** Confirm Kafka reaches healthy state (e.g., `docker compose ps` after initialization); confirm the local development document was saved and reviewed. Record those results here. Do not claim the full stack was validated.

Commands executed by user:

```powershell
docker compose config --quiet
docker compose config --services
docker compose --profile producer --profile tools config --services
docker compose up -d --no-build
docker compose ps
```


## 4B. Task 3 — Docker environment, listeners and application verification (2026-10-27)

**Configuration findings and fixes:**

- Kafka advertises both an internal Docker listener, `kafka:9092`, and host listener, `localhost:29092`. The broker subsequently reported healthy.
- Windows `trade-processor:bootRun` initially failed PostgreSQL host resolution (`postgres`), then JDBC timezone negotiation (`Asia/Calcutta`); `JAVA_TOOL_OPTIONS=-Duser.timezone=UTC` resolved the reported timezone failure.
- The trade processor's custom `KafkaConfig.kt` had hardcoded `kafka:9092` in consumer and producer factories, bypassing the environment-configured `localhost:29092` for Windows. Configuration was revised to use Spring Kafka properties; a Windows Java process and Kafka group membership were observed.
- The Docker market-data producer temporarily failed DNS resolution for `kafka:9092` while its previous container was exited; the user reported success after a Compose force-recreate. Root cause was not conclusively isolated.
- The Docker trade processor initially attempted Flyway JDBC connection to `localhost:5432` and failed. Docker Compose configuration was adjusted to supply PostgreSQL service-address URLs for both R2DBC and Flyway (`postgres:5432`) and Redis service host (`redis`).
- At verification, seven containers were up: Kafka (healthy), PostgreSQL, Redis, market-data producer, trade processor, WebSocket broadcaster, historical service. The frontend was not part of this Docker check.

**Kafka and persistence verification:**

| Check | Observed result | Interpretation |
|---|---|---|
| Consumer group | `trade-processor`, `trade-events`, partition `0` | Consumer subscribed and assigned |
| Kafka current offset / log-end offset | `49843 / 49843` | Consumer caught up *at that snapshot* |
| Kafka lag | `0` | No lag *at that snapshot*, not a permanent guarantee |
| PostgreSQL `SELECT COUNT(*) FROM trades` | `52,290` | Existing persisted trade rows |
| Follow-up PostgreSQL count | `56,594` | **+4,304 rows**, confirms ongoing persistence between checks |
| Follow-up `MAX(processed_at)` | `1793073946730` (epoch milliseconds) | Last persisted processing timestamp at query time |
| Recent-latency query | 1,672 records with `processed_at` in preceding 60 seconds | Recent processing sample |
| Recent recorded `latency_ms` | Average **1,341 ms**, minimum **2 ms**, maximum **2,541 ms** | Application-recorded trade latency, not network latency |

First rows inspected were BTCUSDT and ETHUSDT Binance trades with ID, side, volume, timestamps and latency fields. Some older rows had ~1,038,000 ms latency; this may indicate backlog, but root cause was not established. Don't generalize the recent latency sample to a sustained p95/p99 SLA.

**Full backend + infrastructure Docker resource snapshot:**

| Container | CPU | Memory |
|---|---:|---:|
| Trade processor | 3.09% | 929.8 MiB |
| Market-data producer | 0.65% | 320.3 MiB |
| Historical service | 0.20% | 275.4 MiB |
| WebSocket broadcaster | 1.06% | 348.6 MiB |
| Kafka | 8.49% | 983 MiB |
| Redis | 0.58% | 10.53 MiB |
| PostgreSQL | 0.59% | 77.48 MiB |
| **Sum of Docker readings** | **14.66%** | **2,945.11 MiB (2.88 GiB)** |

Seven-container memory is about 8.37 times the earlier ~351.95 MiB infrastructure-only reading, **not a controlled like-for-like comparison**. Snapshots cannot establish peak resource use or throughput; Docker Desktop/WSL host overhead is omitted.

**Remaining service health check:**

- `docker logs --since 2m websocket-broadcaster` filtered for `ERROR|WARN|Exception|Connected|Started` returned no matches.
- The same filter for `historical-data-service` returned no matches. Absence of matched logs does not prove successful WebSocket delivery or historical functionality.
- `Invoke-RestMethod http://localhost:8084/actuator/health` returned `UP`.
- WebSocket delivery to a browser, historical REST result correctness, automated end-to-end smoke tests, and service-specific env audit remain unverified.

**Task 3 assessment:** Key environment, listener, Flyway and Kafka/DB runtime issues resolved in the observed configuration. Core market ingestion → Kafka → trade-processor → PostgreSQL path demonstrated. Further correctness and frontend-facing tests belong to M2. Update `docs/LOCAL-DEVELOPMENT.md` for current full-backend Compose mode in a separate documentation task.

## 5. Commands used

```powershell
docker compose up -d kafka postgres redis
docker compose ps
docker stats --no-stream

docker top market-data-producer
docker logs --tail 100 market-data-producer
docker logs --tail 100 kafka

docker exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic trade-events

docker exec kafka /opt/kafka/bin/kafka-get-offsets.sh --bootstrap-server localhost:9092 --topic trade-events

docker logs --since 2m kafka 2>&1 | Select-String "heartbeat|timed out|Disconnecting"

Get-CimInstance Win32_Processor | Select-Object Name, NumberOfCores, NumberOfLogicalProcessors
Get-CimInstance Win32_ComputerSystem | Select-Object @{Name="TotalRAM_GB";Expression={[math]::Round($_.TotalPhysicalMemory / 1GB, 2)}}
Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version
docker version --format '{{.Server.Version}}'
```

## 6. Current recommendation and next review

**Do not introduce arbitrary memory caps or Kafka timeout changes based on the current snapshots.** Capture reproducible full application startup, logs, workload definition, and matched idle/load readings before optimization. M1's documentation and repository hygiene tasks remain open.

### Change log

| Date | Change | Verification |
|---|---|---|
| 2026-10-26 | Created baseline-results document from terminal measurements and logs | Transcribed from user-provided output; no application code changed |
| 2026-10-26 | Added M1 Task 2 Compose configuration verification | Based on user terminal output; documentation and Kafka final health still pending |
| 2026-10-27 | Recorded M1 Task 3 Kafka, Docker, PostgreSQL, health and resource results | Based on user-provided commands and logs; full application correctness not inferred |
