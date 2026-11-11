# M14 — consistent snapshot for paper-wallet evaluations

This phase narrows the remaining **read consistency** gap after M13's
transaction-bound wallet authorization. Scope: private simulated paper wallets;
no real-money exchange orders or public release decision.

## Implementation

- `PaperWalletReadGuard` now starts a fresh
  `REPEATABLE READ` transaction for each secure-mode portfolio/evaluation
  request. The query of the current wallet capability obtains a PostgreSQL
  `FOR SHARE` row lock, which is retained through the protected read.
- The read transaction is intentionally **not** marked `readOnly`:
  PostgreSQL rejects `SELECT FOR SHARE` inside a read-only transaction.
  Application queries under the guard are SELECT-only. The credential row
  lock coordinates with M11 rotation/revocation UPDATE operations.
- The evaluation endpoint executes the persisted-activity query **before**
  its inventory/ledger query, using `flatMap` instead of parallel `Mono.zip`.
  This avoids simultaneous statements on one transactional R2DBC connection.
  Both reads see the same transaction-level MVCC snapshot.
- Wallet balances and order history also inherit the same isolation level.
  The distinct, explicitly opted-in `M8_PAPER_LOCAL_INSECURE_ENABLED=true`
  development bypass still skips this guard; its results are not evidence of
  secure-mode consistency.

## Evidence and regression

`PaperWalletReadGuardIntegrationTest` uses disposable PostgreSQL and two
independent connections. It reads a wallet balance under the guarded
transaction, commits a balance change from a separate JDBC connection,
then reads again before the first transaction ends. Both guarded reads must
observe the original value; the next transaction must observe the newly
committed value. This is a behavioral assertion of snapshot consistency,
not merely a configuration check.

Previously introduced tests continue to verify rotated/revoked credential
denial, cross-wallet isolation and paper evaluation ledger reconciliation.

### Local verification

Java 21 and Docker-backed PostgreSQL:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:test --tests '*PaperWalletReadGuardIntegrationTest' --tests '*PersistedPaperInventoryRepositoryIntegrationTest' --tests '*PaperCommandDatabaseIntegrationTest' --tests '*PaperWalletCredentialLifecycleIntegrationTest'
}
finally {
  Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

The full backend CI and static security gate must also be reviewed.

## Important limits

1. A snapshot is consistent **within one guarded transaction**, not a
   guarantee of correct real-world portfolio marks, trading profits or
   eventual source completeness.
2. An already admitted read can complete before a competing credential
   revocation commits. This is defined transactional ordering, not
   retroactive cancellation.
3. The bypass is intentionally insecure for isolated local demos.
   New database clients or routes that do not use the read guard remain
   outside this protection.
4. Wallet capability possession is not verified human identity or account
   ownership. No public multi-user deployment, credential recovery,
   rate-limiting or production release is authorized by this PR.
5. Repeatable-read transactions can encounter serialization errors under
   concurrent modifications; callers must not turn those errors into
   successful or partially trusted responses.

The user/operator must evaluate operational acceptance separately from the
automated test outcomes.
