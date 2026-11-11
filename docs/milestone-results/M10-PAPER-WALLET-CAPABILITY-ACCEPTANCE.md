# M10 — wallet-scoped capability security for private paper trading

**Scope:** security hardening for a *single-user/private* paper trading demo.
This is not an OIDC login system, full account ownership, a public multi-user
authorization boundary, production approval, or real-money trading.

## Implementation

- V12 creates `paper_wallet_access` with per-wallet SHA-256 token fingerprints,
  unique, revocable and stored separately from the public wallet UUID.
- Explicit, separate administrative/bootstrap bearer `M8_PAPER_API_TOKEN`
  may provision a paper wallet **only if** `M6_PAPER_COMMANDS_ENABLED=true`.
  It does **not** grant access to any wallet's balances, orders or evaluation.
- The creator receives a cryptographically random 256-bit `pw_` capability
  on the initial create response **once**; its raw value is never stored in
  PostgreSQL. Securely capture it at provisioning. No lost-token recovery
  or identity-based reissuance workflow is implemented.
- Every wallet-bound request checks the exact wallet's unrevoked fingerprint,
  including read-only portfolio/evaluation and opt-in order submission.
  Cross-wallet requests, invalid credentials and SQL failures fail closed.
- Preexisting wallets have no generated credential and remain inaccessible
  in secure mode. **Do not automatically assign legacy wallets to one caller**.
- Browser token entry is password-style and memory-only; no tokens in local
  storage, URLs or API logs. Reload requires re-entry.
- The explicitly insecure `M8_PAPER_LOCAL_INSECURE_ENABLED=true` bypass
  remains an isolated localhost development-only option; it is not an
  acceptable authentication mechanism for public deployment.
- Normal Compose still disables paper mutations and the bypass by default.

## Test evidence

| Check | Test | Scope |
| --- | --- | --- |
| No/duplicate/malformed credential denied | PaperApiAccessFilterTest | Unit |
| Bootstrap bearer cannot access a wallet | PaperApiAccessFilterTest | Unit |
| Wallet-only capability covers balances/orders/evaluation and order POST | PaperApiAccessFilterTest | Unit |
| Cross-wallet capability and revoked token denied | PaperWalletAccessIntegrationTest | Disposable PostgreSQL |
| Legacy wallets remain unclaimed | PaperWalletAccessIntegrationTest | Disposable PostgreSQL |
| Token generation and hashed-at-rest storage | PaperWalletAccessIntegrationTest, PaperCommandDatabaseIntegrationTest | Disposable PostgreSQL |
| Browser uses in-memory token, not localStorage | Playwright paper-portfolio.spec.mjs | Mock API, not deployed auth |

## Verification

With Docker Desktop and Java 21, run:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:test --tests '*PaperApiAccessFilterTest' --tests '*PaperWalletAccessIntegrationTest' --tests '*PaperCommandDatabaseIntegrationTest' --tests '*PaperCommandControllerTest'
}
finally { Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue }
```

CI runs the full backend suite, frontend compile/lint and mocked Playwright
smoke where changed. Use an isolated disposable stack for real API checks.
Do not expose tokens in test logs, screenshots, shell history or CI artifacts.

## Limitations requiring further work

1. A bearer capability proves possession, **not who the human or customer is**.
   Real identity, owner enrollment, delegation, loss/recovery, rotation, audit
   log, session lifecycle, CSRF considerations and rate limiting are missing.
2. A token may be copied by a holder; there is no per-device/session
   attribution or ownership recovery. TLS and a trusted browser/proxy are
   required outside localhost, and the insecure demo bypass must be off.
3. Existing unclaimed wallets must not be made reachable by a bootstrap
   credential; use a separately approved migration and verified identity
   mapping before any migration of actual user data.
4. Authorization is applied at the HTTP boundary and not PostgreSQL row-level
   security; direct DB access must remain tightly protected.
5. Initial synthetic USDT funding still lacks an immutable funding ledger;
   no investment return or safety claims follow from this patch.

No public deployment or production-readiness decision is made by this PR.
