# M13 — transactional paper-wallet read admission

This phase narrows the rotation/revocation race for the **private paper-wallet read API**.

## Scope

`PaperWalletReadGuard` uses a reactive PostgreSQL transaction for each wallet
read. The first query checks the current unrevoked wallet-scoped token hash,
acquiring a `FOR SHARE` lock on that wallet's access row. The transaction
continues through the requested balances, orders or evaluation query.

- `GET /api/v1/paper/wallets/{walletId}/balances`
- `GET /api/v1/paper/wallets/{walletId}/orders`
- `GET /api/v1/paper/wallets/{walletId}/evaluation`

The access lock means M11 rotation or revocation (which `UPDATE`s the same
row) cannot commit in the middle of an admitted read transaction. A request
admitted after a rotation or revocation using an old token is denied. A
request that was already transactionally admitted may finish before the
rotation/revocation commits; this is normal serialization, not retroactive
cancellation. No token is returned or logged by the guard.

The explicitly opted-in local-insecure demo bypass remains unchanged; it
is not an acceptable basis for security claims. The normal Compose config
still defaults to denying anonymous paper requests and disabling paper
command mutation.

## Automated checks

- `PaperWalletReadGuardIntegrationTest`: disposable PostgreSQL verifies
  successful scoped reads, cross-wallet denial, post-rotation old-token
  denial, revocation denial, and unchanged access to a second wallet.
- `PaperPortfolioControllerTest`: validates order-query limit before DB.
- `PersistedPaperInventoryRepositoryIntegrationTest`: existing read-only
  evaluation and reconciliation fixture continues to exercise the API
  under explicitly isolated local-demo mode.
- Existing `PaperApiAccessFilterTest`, `PaperCommandDatabaseIntegrationTest`
  and M11 credential lifecycle tests remain relevant regression checks.

## Reproduction

Java 21, Docker, UTC JVM:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:test --tests '*PaperWalletReadGuardIntegrationTest' --tests '*PaperPortfolioControllerTest' --tests '*PersistedPaperInventoryRepositoryIntegrationTest' --tests '*PaperApiAccessFilterTest' --tests '*PaperCommandDatabaseIntegrationTest'
}
finally {
  Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Run the complete backend CI as well. A PostgreSQL-backed integration test
is required; mock-only HTTP tests cannot prove row locking.

## Remaining limitations

1. **No human identity:** possession of a private wallet capability still
   cannot establish account ownership or meet public multi-user auth needs.
2. **Transaction boundaries are route-specific:** any new paper-wallet
   controller/read path must use an equivalent guard; internal database
   clients and external direct SQL access are outside this layer.
3. **Not snapshot-proof by itself:** the evaluation combines activity and
   inventory reads that may observe concurrent paper fills under default
   READ COMMITTED behavior. A verified consistent-snapshot evaluation
   requires a documented isolation strategy.
4. **Operations remain private:** token recovery, rate limiting, HTTPS/TLS,
   incident monitoring, log redaction, and public identity federation are
   separate follow-on tasks. No real-money exchange routing is enabled.

A passing CI suite verifies only tested code behavior and does not
constitute release authorization or operational acceptance.
