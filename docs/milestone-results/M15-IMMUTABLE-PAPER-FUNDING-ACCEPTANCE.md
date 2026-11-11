# M15 — immutable synthetic USDT funding provenance

A private paper wallet previously received initial synthetic USDT through a
balance insert, without an immutable record of *why* its balance was funded.
This phase introduces provenance for **new** wallets and a read-only
reconciliation endpoint. It does not create a real deposit mechanism.

## Implementation and safety

- Flyway V14 adds `paper_wallet_funding` with an immutable, wallet-scoped
  initial USDT event. An existing V9 trigger function rejects mutation or
  deletion. No invented historical funding is backfilled.
- `POST /api/v1/paper/wallets` creates a wallet, starting USDT balance,
  scoped credential and initial funding event in one PostgreSQL statement.
  The existing paper-command opt-in stays required.
- `GET /api/v1/paper/wallets/{walletId}/funding-audit` is **read-only** and
  uses the M14 authenticated repeatable-read guard. It compares initial USDT
  funding plus committed USDT quote ledger movements against the available
  USDT balance.
- `RECONCILED` means these stored values agree, `MISMATCH` means they do not,
  and `LEGACY_UNVERIFIED` means no original funding event exists. A missing
  record is **not** proof that a pre-V14 wallet received zero funding.
- This is purely simulated USDT, not exchange-held money or a trading return.

## Tests

`PaperFundingRepositoryIntegrationTest` with disposable PostgreSQL verifies
a new wallet's funding, a pre-V14 wallet remaining unverified, failed UPDATE /
DELETE attempts against immutable funding, and detection of an unjustified
balance change. Existing paper command / wallet / credential tests apply V14.

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:compileKotlin :historical-service:compileTestKotlin
  .\gradlew.bat :historical-service:test --tests '*PaperFundingRepositoryIntegrationTest' --tests '*PaperCommandDatabaseIntegrationTest' --tests '*PaperWalletReadGuardIntegrationTest' --tests '*PaperWalletCredentialLifecycleIntegrationTest'
}
finally {
  Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

## Still not proven

- Existing wallets cannot have their original funding reconstructed from
  the latest balance. Keep them in `LEGACY_UNVERIFIED` state unless independently
  verified through an authorized migration.
- USDT conservation is one part of accounting, not proof of market-price
  correctness, whole-wallet equity marks or profitable trading.
- Live recovery, operational load, provider licensing and authenticated user
  ownership require separate evidence.
- This PR does not authorize a public deployment or real-money execution.

**Acceptance:** compile, backend CI and disposable database tests should be
reviewed before merger; passing tests establish only the verified code scope.
