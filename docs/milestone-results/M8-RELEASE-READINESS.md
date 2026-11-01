# M8 portfolio release readiness — evidence and open gates

This is an evidence log, **not** a production security certification, trading performance claim or approval to tag a release.

## Verified checks reported during M8

| Check | Evidence | Assessment |
|---|---|---|
| Local M8 release preflight | `scripts/verify-m8-release-readiness.ps1` user output | PASS; review gates remain |
| Tracked environment files and rendered Compose ports | `scripts/audit-m8-config.ps1` user output | PASS for current tracked files and rendered port mappings |
| Git history secret detection | Gitleaks 8.30.1, 258 commits, ~1.23 MB, zero findings as reported | PASS for scanner scope; does not prove rotation |
| Database password fallback | Removed `traderpass` default from trade-processor Spring config | Implemented; backend tests passed |
| Gradle tests | User reported `gradlew test` successful under UTC JVM setting | PASS; local environment workaround needed |
| Paper API filter | Unit tests and isolated M8 container checks | No-token 401; valid bearer 200; health 200 |
| Service health and observability | `scripts/accept-m8-observability.ps1` user output | Health UP, info responds, JVM memory and uptime available |
| Compose host bindings | M8 branch uses loopback host binding for nine published ports | Implemented; existing containers retain old bindings until recreated |

## Open release blockers

1. **Multi-user authorization:** One shared bearer token does not authenticate a wallet owner; local demo bypass remains explicitly enabled in M8 Compose. No public deployment until a verified ownership and identity model exists.
2. **Exposure:** Existing running containers may still publish prior all-interface ports. Do not claim the running stack is loopback-restricted without inspecting `docker ps` after a controlled recreation.
3. **Monitoring:** Metrics existence is not alerting, uptime monitoring, retention, log redaction, or dashboard validation.
4. **Provider compliance:** Confirm exchange/news API feed terms, attribution, key rotation/revocation history, and privacy review. A clean scanner result is not proof of absence of past exposure.
5. **End-to-end tests:** Browser/mobile smoke, restart recovery, load/CPU/RAM comparisons, and full release regression evidence remain outstanding.
6. **Performance evidence:** Paper synthetic orders without recorded fills and timestamp-consistent marks do not support realized strategy returns, benchmark superiority, or calibrated predictive claims.
7. **Release documentation:** Architecture decisions, screenshots, runbooks, user-facing limitations, and honest benchmark tables require separate review.

## Allowed deployment statement

Private, simulated-paper-trading development demo only. No exchange execution. API authorization is insufficient for public multi-user access.

## Release gate

Do not create a production/release tag until open blockers are resolved or explicitly and appropriately scoped out of a non-public preview release, with verifiable evidence and approval.

This file intentionally does not modify `docs/MILESTONES.md`.
