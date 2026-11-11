# M1–M2 reliability — consolidated acceptance plan

This PR adds a real, disposable PostgreSQL + Kafka broker integration test,
a consumer error/duplicate boundary test, and repeatable read-only sampling
of an existing local development stack. It is **not** an assertion that M1,
M2 or release readiness have passed.

## Automated evidence

| Requirement | Verification | What it proves |
| --- | --- | --- |
| Ingest identical stable trade IDs | TradeKafkaOutboxIntegrationTest | Exactly one PostgreSQL trade + outbox row |
| Outbox publication to actual Kafka broker | TradeKafkaOutboxIntegrationTest | Broker receives an acknowledged processed event |
| Crash after broker send, before marking published | TradeKafkaOutboxIntegrationTest | Same stable ID is replayed, documenting at-least-once output |
| Invalid input rejected before state mutation | TradeConsumerBoundaryTest | Missing ID cannot be cached or persisted |
| Duplicate input does not mutate Redis cache | TradeConsumerBoundaryTest | Existing durable deduplication is respected |
| Database write failure propagates | TradeConsumerBoundaryTest | Listener cannot treat a failed durable commit as success |
| Kafka consumer error-handling settings | KafkaConfigTest | Auto-commit OFF; restricted JSON and error handler retained |
| Redis recovery and database reconnection | Existing TradeProcessorR2dbcReconnectIntegrationTest | Dead R2DBC connection is replaced, **not** a real DB restart |
| Read-only persisted DB and Kafka diagnostics | verify-m2.ps1, inspect-m9-kafka-replay.ps1 | Snapshot only |
| Repeatable timestamped local resource and lag samples | measure-m1-m2-readonly.ps1 | Observed Docker CPU/RAM, DB row count, pending outbox and Kafka lag |

## Reproduce

Run Java 21 + Docker-backed tests:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
    .\gradlew.bat :trade-processor:test --tests '*TradeKafkaOutboxIntegrationTest' --tests '*TradeConsumerBoundaryTest' --tests '*TradePersistenceIntegrationTest' --tests '*KafkaConfigTest' --tests '*TradeProcessorR2dbcReconnectIntegrationTest'
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

On a local private Compose stack with Kafka, PostgreSQL and trade processor
running, produce a **read-only observational** report:

```powershell
.\scripts\verify-m1-m2-readiness.ps1 -IncludeSampling -Samples 12 -IntervalSeconds 10
```

The script writes `m2-verification-report.json` and
`m1-m2-observation-report.json` with timestamps and constraints.
Keep generated reports outside Git until reviewed for runtime identifiers.

## Remaining interactive and manual exit gates

1. **M1 fixed-rate benchmark:** run the same bounded, documented input
   workload twice under comparable system load. Capture baseline and revised
   branch CPU, RAM, GC, Kafka lag, persistence throughput and latency. Measure
   host Docker Desktop/WSL overhead separately. The sampler does not create
   a fixed-rate producer and does not claim controlled before/after results.
2. **M1 coordinator incident:** diagnose recurring group coordination
   timeouts and document broker logs/configuration, root cause or bounded risk.
3. **M2 fault injection:** run approved, isolated Kafka broker-outage,
   PostgreSQL-outage, Redis-loss and processor-restart tests; check eventual
   durable state and no uncontrolled duplicate trades. The repository already
   has `scripts/accept-m2-kafka-outage.ps1`, which **stops a shared broker**.
   Never run it without explicit operator approval and an isolated stack.
4. **M2 poison records:** verify malformed input quarantine, exhausted
   retries and inability to commit a source offset before durable quarantine.
5. **M2 backlogs:** measure bounded consumer lag and outbox drain time under
   a sustained declared load; at-least-once output permits duplicates.
6. **Provider/security:** verify old Alpha Vantage credential revocation,
   provider policy compliance and authentication/ownership before any public
   exposure.

## Scope decision

This phase is acceptance-focused reliability work. Do not mark
`docs/MILESTONES.md` complete, create a public release, or imply live
trading, true exactly-once delivery, proven profitable signals or full
recovery until independent evidence satisfies the remaining gates.
