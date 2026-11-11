# M6 paper execution and ledger acceptance

Single phase-level PR. This phase hardens **spot-only simulated orders**.
No real exchange execution, real funds or model-driven auto-orders are enabled.
`docs/MILESTONES.md` remains unchanged.

## Implemented acceptance matrix

| Requirement | Automated evidence | Important boundary |
| --- | --- | --- |
| Only future market ticks can fill | PaperFillEngineTest | Ticks are externally supplied fixtures |
| Ambiguous first-timestamp prices fail closed | PaperFillEngineTest | No external tick dedup service |
| Quote fees, spread, slippage and exact decimal math | PaperFillEngineTest, PaperLedgerPostingTest | Fixed illustrative cost parameters |
| A typed fill must match persisted order identity, symbol, side and quantity | PaperFillRepositoryIntegrationTest | No public order execution endpoint |
| Malformed gross, fee or net debit rejected before persistence | PaperFillRepositoryIntegrationTest | Order-level DB bounds remain authoritative |
| Exact replay changes no money or ledger entries | PaperFillDatabaseIntegrationTest, PaperFillRepositoryIntegrationTest | Idempotent, not exactly-once distributed execution |
| Conflicting replay is rejected, not silently acknowledged | V11 migration, PaperFillDatabaseIntegrationTest | Replay after terminal cancellation/rejection still fails |
| Same-wallet concurrency cannot overspend | PaperFillConcurrencyIntegrationTest | PostgreSQL Testcontainers, not full Docker disruption |
| Buy/sell balances, ledger, turnover and fees reconcile across reconnect | PaperRoundTripReconciliationIntegrationTest | Service/container crash recovery not established |
| Read-only audit of persisted fills | audit-m6-paper-ledger.ps1 | USDT initial funding baseline is not an immutable funding ledger |

## Verify locally

Java 21, Docker Desktop and UTC JVM context:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:test --tests '*PaperFillEngineTest' --tests '*PaperMoneyTest' --tests '*PaperLedgerPostingTest' --tests '*PaperFillDatabaseIntegrationTest' --tests '*PaperFillRepositoryIntegrationTest' --tests '*PaperFillConcurrencyIntegrationTest' --tests '*PaperFillReconnectIntegrationTest' --tests '*PaperRoundTripReconciliationIntegrationTest' --tests '*PaperCommandDatabaseIntegrationTest' --tests '*PaperCommandControllerTest'
  .\scripts\verify-m6-schema.ps1
  .\scripts\audit-m6-paper-ledger.ps1
  .\scripts\accept-m6-readonly.ps1
}
finally {
  Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Run `./gradlew test` in CI. Scripts inspect the **private local stack**
without modifying wallets. Opt-in `run-m6-paper-acceptance.ps1` creates
a synthetic paper wallet/order and must be run only in a controlled isolated
loopback environment with the intended paper-command flag. Do not run against
public endpoints or shared wallets.

## Open milestone gates

1. Attach actual CI and local Docker-backed results, migration V11 checks,
   API/balances/reconnect evidence, and browser state after refresh.
2. Run controlled outage/restart exercises against an isolated, disposable stack
   and record results. JDBC reconnect does not prove broker/service crashes.
3. Verify real-world market tick provenance, ordering, precision and plausible
   execution costs. Synthetic ticks are not real fills.
4. Audit wallet funding history and USDT balance conservation: initial synthetic
   USDT seeding is currently not stored as an immutable funding-ledger entry.
5. M8 ownership/authentication remains a blocker for multi-user/public hosting;
   never confuse UUIDs with authorization tokens.

No strategy profitability or release readiness is claimed from these tests.
