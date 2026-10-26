# Docker CPU and memory optimization — measurement-first TODO

**Reported symptom:** the working app uses too much CPU and RAM when Docker is running. No host hardware specification, measurements, container stats or profiler output have been provided, so causes and target resource limits are hypotheses, **not verified diagnoses**.

## Fastest useful development mode
Run *only stateful infrastructure* in Docker and run Kotlin services from Gradle/IDE plus the Vite frontend on the host. This avoids multiple service JVMs inside containers during daily development; those JVMs still consume host resources. Current `docker-compose.yml` enables Kafka, PostgreSQL, Redis, Kafka UI and the market-data producer; the other three backend service entries are commented out. Use explicit service names instead of `docker compose up` while measuring:

```bash
docker compose up -d kafka postgres redis
docker compose ps
docker stats --no-stream
```

If your backend expects `localhost:29092` for Kafka, verify the broker's actual advertised listener configuration first; the existing Compose advertises `kafka:9092`, which may not work for host processes. Do **not** blindly change brokers or hostnames. Start services on the host only after local Kafka/Postgres/Redis endpoints are confirmed.

## P0 — establish a baseline
- [ ] Record OS, CPU cores, system RAM, Docker Desktop/Engine version, Java version, idle available RAM and startup command.
- [ ] Record per-container `docker stats --no-stream` after startup and after 5/15 minutes idle.
- [ ] Measure CPU/RAM under known workload (symbol count, message rate, UI connections); compare Docker-only vs host JVM mode.
- [ ] Identify top processes with `docker top <container>` and host OS tools; note peak vs steady state.
- [ ] Check Kafka, PostgreSQL, Redis and JVM container logs for repeated reconnect, rebalance, retry or error loops.
- [ ] Confirm whether multiple copies of the same producer/services are running (host + Docker).
- [ ] Record baseline before applying resource caps, so a cap cannot be mistaken for an optimization.

## P1 — low-risk changes, one at a time
- [ ] Make Kafka UI opt-in through a Compose profile; stop it when not inspecting topics.
- [ ] Offer `infra-only` and `full-stack` commands or profiles in Compose and docs.
- [ ] Inspect Kafka topic partitions, retention, polling and consumer lag; reduce unnecessary dev topic volume and logging **without losing test coverage**.
- [ ] Set modest **development-only** JVM heap limits after measuring non-heap/native overhead; tune `-Xms`, `-Xmx`/`MaxRAMPercentage` and container-aware JVM behavior per service.
- [ ] Inspect JVM heap/native-memory usage with `jcmd`/JFR where available; investigate high allocation or GC before tuning GC flags.
- [ ] Set safe, measured Postgres and Redis development memory settings if they are significant consumers; preserve data and migration integrity.
- [ ] Review consumer batch size, polling, Redis cache bounds and frontend WS buffering for sustained load.
- [ ] Add explicit health checks and dependency readiness to avoid reconnect storms during Compose startup.
- [ ] Use Docker Compose `mem_limit` / CPU constraints only when confirmed supported by the selected Compose runtime; verify OOM counts and throttling after applying.
- [ ] Verify Kafka broker listener configuration for both host and Docker clients; never sacrifice connectivity for lower resource use.

## P2 — optional simplifications
- [ ] Investigate whether running fewer JVM processes in development is worthwhile (without changing service boundaries in production).
- [ ] Evaluate alternative dev Kafka deployments **only after** establishing costs/benefits and compatibility.
- [ ] Consider reducing refresh/publish frequency in a clearly labeled demo mode.
- [ ] Benchmark each change at matching event rate and number of subscribers.

## Measurement worksheet
| Metric | Baseline | After change | Workload / notes |
|---|---|---|---|
| Total Docker steady RAM | TBD | TBD | Idle after 15 minutes |
| Total Docker peak RAM | TBD | TBD | Same 10-minute load |
| Kafka steady RAM / CPU | TBD | TBD | |
| Market-data JVM RAM / CPU | TBD | TBD | |
| Other service JVM RAM / CPU | TBD | TBD | |
| PostgreSQL / Redis RAM | TBD | TBD | |
| Consumer lag | TBD | TBD | |
| p95 ingest-to-UI latency | TBD | TBD | Clock synchronization noted |
| Dropped / duplicate events | TBD | TBD | |

## Acceptance criteria
- A documented lightweight mode starts the app for day-to-day development.
- On the **same host and workload**, steady and peak memory and CPU are compared before/after (no invented percentage target).
- Data stays correct: processing and history tests pass, consumer lag remains bounded, and no OOM/reconnect loop appears.
- Dev defaults are separate from production sizing. Resource limits are justified by recorded evidence rather than cargo-cult settings.
