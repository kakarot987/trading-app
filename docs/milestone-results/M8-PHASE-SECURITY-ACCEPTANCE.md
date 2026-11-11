# M8 — private demo security and operational acceptance

This consolidated M8 phase PR defaults the simulated-paper service to
**deny unauthenticated access**, hardens HTTP caching, adds static security
checks in CI, and documents local recovery and operational acceptance.
It does not implement public multi-user wallet ownership and **does not
authorize a public release**.

## Implemented code-level verification

| Control | Evidence | Remaining risk |
| --- | --- | --- |
| Compose demo bypass defaults OFF | verify-m8-phase-security.ps1 | Running old containers need recreation |
| Normal Compose forces paper commands OFF | verify-m8-phase-security.ps1 | Separate opt-in development runner still permits synthetic writes |
| Paper bearer header must be single and well-formed | PaperApiAccessFilterTest | Shared bearer token cannot authorize a wallet owner |
| Paper responses marked no-store, no-cache and nosniff | PaperApiAccessFilterTest | Reverse proxies require separate inspection |
| Published Compose host ports bound to loopback | verify-m8-phase-security.ps1 | Other projects and actual runtime bindings need audit |
| No tracked non-example .env files | verify-m8-phase-security.ps1 | History scanning and credential rotation are separate |
| Actuator limited to health/info over HTTP | verify-m8-phase-security.ps1 | Monitoring, retention and alerting not yet proven |
| Unauthenticated real local paper API returns 401 | accept-m8-secure-paper.ps1 | Requires an already-running secure service |
| Browser mocked-API read-only regression | Frontend browser-smoke workflow | Mock network requests are not backend integration |

## CI and local commands

On the one phase branch, first check static gates and full Gradle/React builds:

```powershell
.\scripts\verify-m8-phase-security.ps1
.\scripts\audit-m8-config.ps1
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
    .\gradlew.bat test --no-daemon --console=plain
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
Push-Location client-connection
try {
    npm ci
    npm run lint
    npm run build
}
finally { Pop-Location }
```

For a **private local secure** service running after a deliberate controlled
recreation with `M8_PAPER_LOCAL_INSECURE_ENABLED=false`:

```powershell
.\scripts\audit-runtime-ports.ps1
.\scripts\accept-m8-secure-paper.ps1
.\scripts\accept-m8-observability.ps1
.\scripts\accept-m9-dependency-recovery.ps1
.\scripts\audit-m6-paper-ledger.ps1
```

For an expressly approved **isolated loopback demo** only, opt in to
`M8_PAPER_LOCAL_INSECURE_ENABLED=true` for that run and revert afterwards.
Neither the bypass nor a shared bearer token grants per-wallet authorization.
Local M8 secure-mode checks should not be marked failed simply because a
deliberately configured isolated demo returns HTTP 200; run the secure
and demo modes separately and record each configuration.

## Remaining release blockers

1. **Identity and ownership:** protect every wallet read/write with actual
   authenticated owner checks, not just a shared token or UUID.
2. **Operational proof:** inspect recreated Docker bindings and run genuine
   dependency failure/recovery and bounded Kafka consumer lag tests; measure
   fixed-rate CPU/RAM load including Docker Desktop/WSL overhead.
3. **Observability:** alerting, uptime, retention, incident runbooks,
   redacted traces and credential-safe HTTP error logs need review.
4. **Compliance/secrets:** provider license/quota/attribution, key revocation
   and rotation, Git-history scan, SBOM/dependency review.
5. **User-facing QA:** actual browser screenshots, mobile acceptance,
   accessibility, error states and private demo regression evidence.
6. **Performance truth:** no prospective paper track record, contemporary
   equity marks or verified profitable strategy yet.

No public release tag or production-readiness claim is authorized by this PR.
Keep `docs/MILESTONES.md` unchanged until evidence has been independently
reviewed.
