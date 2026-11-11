# M12 — order mutation admission during wallet token rotation/revocation

This phase hardens the **private, simulated-paper** order-submission API after
M10 wallet-scoped bearer credentials and M11 atomic rotation/revocation. It
does not implement human identity, owner mapping, row-level security, or live
exchange trading.

## Rationale and contract

Previously, the WebFilter checked a scoped bearer before handing the
request to the order controller. A concurrent credential rotation/revocation
could commit **between** that initial check and the SQL order insert.

The `POST /api/v1/paper/wallets/{walletId}/orders` controller now:

- Validates the wallet capability again inside the database statement. The
  credential hash is bound as a SQL parameter, never logged or copied into
  the persisted paper order.
- Selects the exact active, unrevoked access row with PostgreSQL `FOR SHARE`
  before inserting. A competing token rotation/revocation `UPDATE` on
  the same access row cannot complete until the authorized insert statement
  releases its lock. A request arriving after revocation sees no matching
  access row and inserts nothing.
- Rechecks access on the existing client-order-key idempotent replay path.
  A stale token therefore cannot learn an order's stored status or alter it.
- Does not relax `M6_PAPER_COMMANDS_ENABLED=false`. Normal Compose still
  disables paper order staging, regardless of credentials.
- Preserves the expressly opted-in `M8_PAPER_LOCAL_INSECURE_ENABLED=true`
  path for isolated, private localhost demos. This bypass is not suitable
  for shared/public use and must not be interpreted as account authorization.

The access row lock is held **for the order insertion statement**. It is
not a blanket cancellation guarantee for operations already completed;
HTTP responses, read requests, and any other internal paper writes need
their own controls.

## Regression matrix

| Check | Automated evidence |
| --- | --- |
| Invalid/missing headers rejected before database access | PaperCommandControllerTest |
| An authorized wallet can insert/replay a matching paper order | PaperCommandDatabaseIntegrationTest |
| Reusing a client order key with a modified payload remains a conflict | PaperCommandDatabaseIntegrationTest |
| Shared bootstrap token or unrelated credential cannot access an order | PaperCommandDatabaseIntegrationTest |
| Rotation invalidates the original token for a new insert | PaperCommandDatabaseIntegrationTest |
| Revocation denies new inserts, while original committed orders persist | PaperCommandDatabaseIntegrationTest |
| Database-level record of changed token and revocation | PaperWalletCredentialLifecycleIntegrationTest |

## Verification

Use Java 21, Docker-backed PostgreSQL and the UTC JVM setting:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
    .\gradlew.bat :historical-service:test --tests '*PaperCommandControllerTest' --tests '*PaperCommandDatabaseIntegrationTest' --tests '*PaperWalletCredentialLifecycleIntegrationTest' --tests '*PaperApiAccessFilterTest'
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Run the complete backend CI suite and the existing static security gate.
For a real private service, verify results only against disposable wallets
and explicitly configured credentials. Do not place bearer tokens in URLs,
CI logs or screenshots.

## Remaining limitations

- The WebFilter still performs a separate early authorization check. The
  insert-time lock fixes the **order staging** transition only. Read-only
  endpoints, service-internal fill processors and report queries are not
  serializable against token revocation.
- No real user login, account ownership, RLS, session/device revocation,
  token recovery, rate limiting, TLS termination review, or audit alerts.
- An order inserted before a revocation commits remains valid; revocation
  does not cancel already staged paper orders.
- This is not evidence of real exchange execution or profitable trading.

No release approval, production-deployment decision or release tag is
conferred by passing these tests.
