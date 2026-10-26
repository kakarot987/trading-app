# M1 — Baseline and Cleanup: Results Log

**Project:** [trading-app](https://github.com/kakarot987/trading-app)  
**Milestone tracker:** [`docs/MILESTONES.md`](https://github.com/kakarot987/trading-app/blob/main/docs/MILESTONES.md)  
**Measurement date:** 2026-10-26 (log timestamps in UTC)  
**Status:** In progress — initial resource baseline captured; M1 Task 2 Compose profiles validated locally, documentation and final health checks pending.

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
- PostgreSQL persistence, downstream processing, and frontend delivery were **not** verified by this exercise (planned for M2).

## 4. M1 tracker

| M1 requirement | State | Evidence / next action |
|---|---|---|
| Capture baseline CPU/RAM | **Partial** | Infrastructure idle snapshots and producer+infra snapshot captured; still need defined workload, full app, peak/sustained readings. |
| Document infrastructure-only mode and Compose limitations | **Partially verified** | Default Compose now selects only Kafka/PostgreSQL/Redis; opt-in `producer` and `tools` profiles list intended services. Confirm `docs/LOCAL-DEVELOPMENT.md` exists and accurately describes limitations before calling complete. |
| Verify environment variables and broker listeners | **Pending** | Inspect all service configs; Compose advertises `kafka:9092` but also exposes port `29092` for host access. Validate before editing. |
| Untrack build/IDE artifacts; audit `.env` | **Pending** | Review tracked files; do not remove source or Gradle wrapper; check exposed secrets. |
| Capture build/test baseline | **Pending** | Run builds/tests and record pass/fail plus environment. |

## 4A. Task 2 — Compose development mode verification (2026-10-26)

**Local user-reported results:**

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
