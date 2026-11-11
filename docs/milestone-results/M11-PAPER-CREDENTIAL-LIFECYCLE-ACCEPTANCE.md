# M11 — paper-wallet credential lifecycle (private demo)

This phase adds wallet-scoped **rotation**, **irreversible revocation** and an
append-only lifecycle audit to the M10 private paper-wallet capability model.
It is not real user identity, tenancy, production security certification or
trading release approval.

## API contracts

The request must supply exactly one `Authorization: Bearer pw_...` header,
and the capability must match the wallet UUID in the URL. The shared bootstrap
`M8_PAPER_API_TOKEN` cannot rotate or revoke existing wallets.

| Action | Endpoint | Result |
| --- | --- | --- |
| Rotate capability | `POST /api/v1/paper/wallets/{walletId}/credential/rotate` | 200: `{"walletToken":"pw_..."}` (new token **once**) |
| Revoke capability | `POST /api/v1/paper/wallets/{walletId}/credential/revoke` | 204: no response body |
| Invalid, expired/replaced or revoked capability | Either URL | 401 (no mutation) |

These endpoints remain usable while `M6_PAPER_COMMANDS_ENABLED=false`.
Paper *order* mutations retain their separate opt-in flag. The default-deny
filter applies identically to credential routes. `M8_PAPER_LOCAL_INSECURE_ENABLED=true`
still bypasses HTTP authorization for deliberately isolated local debugging;
do not use that bypass to claim security acceptance.

## Database invariants

- V13 adds `credential_version` to V12 access rows, initially 1, plus
  `paper_wallet_credential_events` (`ROTATED` or `REVOKED`,
  monotonically increasing version and event timestamp).
- Rotation changes one wallet's hash **only if its current unrevoked hash
  matches**. The new random capability is returned only when the SQL update
  **and** audit insert commit. The old token immediately stops working for
  subsequent database authorization checks.
- Revocation sets `revoked_at` and appends an event in the same statement.
  Repeat or stale requests fail. Revoked credentials cannot rotate or revive
  themselves.
- Audit records are append-only via a PostgreSQL trigger; events do not store
  raw or hashed token material. The V12 provisioning operation happened before
  this audit existed, so V13 does **not invent historical issuance events**.
- Legacy/unclaimed wallets do not receive new credentials. Existing wallets
  keep their current access record and hash when V13 migrates.

## Validation

Run Java 21 + Docker:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
  .\gradlew.bat :historical-service:test --tests '*PaperWalletCredentialLifecycleIntegrationTest' --tests '*PaperWalletCredentialControllerTest' --tests '*PaperWalletAccessIntegrationTest' --tests '*PaperApiAccessFilterTest'
}
finally {
  Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Then review all backend CI (including Flyway migration), and run secure-mode
HTTP checks only against an approved disposable local stack. Avoid recording
tokens in screenshots, terminal histories, CI logs or artifacts.

## Operational and security limitations

1. **Bearer token possession is not human identity.** There is no login
   service, delegated ownership, user recovery or public tenancy model.
2. **No token recovery after a lost rotation response.** Rotating commits
   before returning a one-time token; if the response is lost, the caller
   cannot retrieve the secret. Future work requires a verified owner recovery
   process rather than a bootstrap-admin bypass.
3. A request already authorized before a rotation could proceed after
   revocation. Strict atomic revocation of *all* wallet operations requires
   authorization bound to each DB mutation/transaction or DB row-level
   security and cannot be inferred from this WebFilter alone.
4. There is no credential-expiration schedule, per-device revocation, abuse
   throttling, authenticated security principal, durable request correlation
   or alerting on anomalous credential use.
5. TLS outside localhost, session security, audit retention and approved
   operational incident-response procedures remain separate requirements.

No automatic migration, release tag, public deployment or live exchange order
is created by this phase. The user/operator determines acceptance after
reviewing real evidence; passing tests alone do not grant release approval.
