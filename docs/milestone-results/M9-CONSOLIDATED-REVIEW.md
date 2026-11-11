# M1–M8 consolidated implementation and evidence review

**Scope:** `milestone/M9-acceptance-reliability`, November 11, 2026.

This is a review of implementation and CI evidence, not an acceptance
decision or an instruction to approve or reject deployment. Only the human
owner/operator can evaluate evidence from a specific target environment and
apply their release policy.

## GitHub implementation and CI review

| Phase | Most recent merged phase PR | CI result verified | Further evidence to review |
| --- | --- | --- | --- |
| M1–M2 | [#24](https://github.com/kakarot987/trading-app/pull/24) | Final backend tests and static security gate: success | Equivalent-load resource profile, isolated outages, consumer lag/drainage, credentials |
| M3 | [#18](https://github.com/kakarot987/trading-app/pull/18) | Backend tests: success | Real exchange late/backfill alignment and candle vintages |
| M4 | [#19](https://github.com/kakarot987/trading-app/pull/19) | Backend tests: success | Provider terms and representative labeled sentiment set |
| M5 | [#20](https://github.com/kakarot987/trading-app/pull/20) | Backend tests: success | Real untouched holdout, calibration, abstention and data version proof |
| M6 | [#21](https://github.com/kakarot987/trading-app/pull/21) | Backend tests: success | Controlled restart, persisted ledger/funding and tick provenance |
| M7 | [#22](https://github.com/kakarot987/trading-app/pull/22) | Backend tests: success | Marked equity series and same-window cost-adjusted benchmark |
| M8 | [#23](https://github.com/kakarot987/trading-app/pull/23) | Backend, frontend, Playwright and static security CI: success | Wallet ownership, real local port inspection, privacy, operations and device QA |

These CI results were retrieved from GitHub for the PR head commits.
They verify that CI reported success in its configured scope. They do not
verify production identity, provider licensing, strategy returns or operating
characteristics on the actual destination environment.

## Reproducible inventory

`docs/milestone-acceptance-manifest.json` lists phase-to-PR references,
known evidence-document paths and evidence requirements. Run:

```powershell
./scripts/check-consolidated-acceptance.ps1
```

The command validates references and writes
`milestone-evidence-inventory.json`. Its success means only that the
referenced files exist and the inventory is well formed. It neither runs
the listed acceptance scripts nor determines their results.

For a running **private local stack**, separate operator-controlled checks
remain available in the individual `docs/milestone-results/` phase plans.
Some scripts publish test events or interrupt Kafka and should only be run
after the operator has planned an isolated test environment.

## Review notes

- The milestone branch currently contains the phase PRs; `main` has not
  automatically incorporated the branch.
- The current Compose implementation defaults private paper endpoints to
  deny unauthenticated requests, but a shared bearer token is not per-wallet
  ownership authorization.
- Portfolio returns, real-market performance, an immutable source-vintage
  archive and provider authorization require independent evidence.
- A reviewer should record each check's date, commit, environment, outcome,
  logs/screenshots, reviewer and any accepted residual risk before making
  a release decision.

Do not infer a deployment decision from this reference inventory.
