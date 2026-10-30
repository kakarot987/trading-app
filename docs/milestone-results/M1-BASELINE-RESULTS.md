## M1 resource optimization and recovery — October 27–28, 2026

### Problem encountered

During the initial full-stack resource investigation, the trade processor stopped advancing its committed Kafka offset while the producer continued publishing events.

The Kafka consumer backlog grew beyond 370,000 messages. Broker logs revealed repeated consumer-group coordination failures, including `COORDINATOR_NOT_AVAILABLE` and timed-out coordinator writes.

A controlled Kafka restart restored consumer-group progress.

During backlog recovery, the trade processor experienced approximately 680% Docker CPU and 2.30 GiB memory usage. Investigation identified unbounded asynchronous database and Redis operations as a significant processing risk.

The broker-side coordinator timeout's underlying cause remains unconfirmed.

### Changes implemented

- Changed PostgreSQL operations to complete before proceeding with consumption.
- Changed Redis cache updates to use completion-aware processing.
- Waited for Kafka processed-event publication confirmation.
- Added duplicate-safe PostgreSQL insertion using `ON CONFLICT (trade_id) DO NOTHING`.
- Added insert and duplicate counters based on affected rows.
- Added a Kafka retry handler.
- Limited Kafka poll batch size to 10 records.
- Improved logging and local configuration.
- Updated the local development guide to describe the seven-container backend stack.

These changes improve resource stability and processing control but do not provide an atomic PostgreSQL-to-Kafka transaction or exactly-once end-to-end delivery.

### Verification results

- Gradle trade processor compilation: passed.
- Gradle trade processor test task: passed.
- Existing Kafka backlog: recovered to zero lag.
- PostgreSQL persistence: trade row counts increased during replay and live ingestion.
- Insert counters: at least 9,000 successful inserts observed, with zero duplicates reported in the captured counters.
- Seven backend containers: running simultaneously.
- Latest live-ingestion verification: Kafka lag of 1 message, with PostgreSQL growing by 544 rows in 30 seconds.

### Container resource observations

| Container | Earlier snapshot | Latest five-minute full-stack snapshot |
|---|---:|---:|
| Trade processor | 929.8 MiB | 383.6 MiB |
| Kafka | 983.0 MiB | 1,005.0 MiB |
| Market-data producer | 320.3 MiB | 289.9 MiB |
| Historical-data service | 275.4 MiB | 283.8 MiB |
| WebSocket broadcaster | 348.6 MiB | 259.8 MiB |
| PostgreSQL | 77.48 MiB | 58.79 MiB |
| Redis | 10.53 MiB | 7.57 MiB |
| **Total** | **2,945.1 MiB** | **2,308.5 MiB** |

The latest total is approximately 21.6% lower than the earlier observation. This is a comparison of recorded snapshots, not a controlled equivalent-workload optimization benchmark.

The latest five-minute run showed only a 0.4 MiB increase in trade-processor memory.

### Host resource observations

- Windows visible RAM: 15.71 GiB.
- Windows free RAM at observation: 2.74 GiB.
- `vmmemWSL` working set: approximately 2.33 GiB.
- Docker and WSL memory overlap; these measurements must not be added together.

### Outstanding limitations

- Sustained throughput of 100 trades/second has not been established under a repeatable, fixed-rate workload.
- Peak CPU and memory under equivalent before/after workloads remain unmeasured.
- Kafka group-coordinator timeouts could recur.
- Failed-record recovery and restart behavior need dedicated tests.
- Missing trade IDs require a stable identifier strategy.
- Database persistence and Kafka publication are not atomic.
- Frontend TypeScript and lint issues remain separate work.
- The previously exposed Alpha Vantage API key requires confirmed revocation or rotation.
- A comprehensive historical secret scan remains outstanding.
- 
### Build and test verification — October 28, 2026

- Frontend `npm run lint`: PASSED, 0 ESLint errors.
- Frontend `npm run build`: PASSED.
- TypeScript project compilation: PASSED.
- Vite production build: PASSED (1,813 modules transformed in 1.06 seconds).
- Production JavaScript bundle: 230.61 kB, 73.48 kB gzip.
- Backend Gradle build and trade-processor tests: PASSED in earlier verification.
- Nonblocking warning: baseline-browser-mapping dependency data is outdated.

**Status:** Build and lint verification complete. Frontend runtime/integration testing and comprehensive backend test coverage have not been established by these commands.

### Conclusion

M1 demonstrates substantial progress in development-stack stability, reproducible setup, and observed resource usage.

Kafka ingestion and PostgreSQL persistence were verified after recovery, including a full seven-container live run with near-zero consumer lag.

The results support an operational recovery and resource-stability claim, not a proven equivalent-load performance improvement or complete ingest-correctness guarantee.