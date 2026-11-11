# M16 — consolidated private-demo acceptance tooling

The M1–M15 implementation work is merged into `main` through PR #32,
and CI verifies source-level tests. **That does not constitute a passing
end-to-end runtime acceptance or public production authorization.**

This package supplies a **read-only Python standard-library runner**, offline
regression tests, CI and a structured JSON evidence record. It deliberately
does not start containers, kill dependencies, mutate wallets, rotate secrets,
submit paper orders or call exchange providers.

## What the runner does

| Mode | Observes | Does NOT prove |
| --- | --- | --- |
| `--mode offline` | M1–M8 manifest references and existence of M10–M15 review files | That live infrastructure is healthy, data is licensed or models predict |
| `--mode live` | Historical-service health; 401 anonymous wallet access; optional scoped-token balances/orders/evaluation/funding audit; cross-wallet denial | Real trading profitability, external wallet owner identity, crash recovery |
| `--check-docker` | Running named project containers have loopback-only published ports | WSL/host firewall, restored snapshots, other uninspected containers, private networks |

The live runner refuses non-loopback URLs, HTTPS-to-remote redirects and
proxy forwarding. It performs **GET requests only** to the local historical
service, with bounded responses. A capability is read from an environment
variable, **never a command argument**, and is not written to the evidence
file. The report stores no raw wallet UUID or order history. Set restrictive
file permissions and do not upload real-wallet reports automatically.

The summary distinguishes `PASS`, `FAIL`, `NOT_RUN`. A pass means only
that the specific check ran and its asserted response matched; it is not
a production acceptance verdict. If the wallet token is absent, the
wallet-data checks are marked `NOT_RUN` unless `--require-wallet` is set.

## Usage

Offline reference validation, suitable for any checkout with Python 3.12+:

```powershell
python -m unittest discover -s tests -p test_private_demo_acceptance.py -v
python scripts/private_demo_acceptance.py --mode offline --report private-demo-evidence.json
```

An **existing, isolated local instance**, with the M8 insecure-demo
bypass switched OFF, may be inspected without making any mutations:

```powershell
# Supply the existing wallet's scoped credential using an appropriate
# secure local secret manager or a non-persisted environment variable.
# Do not put the secret in source control or shell history.
python scripts/private_demo_acceptance.py --mode live `
  --base-url http://127.0.0.1:8084 `
  --wallet-id '<UUID-of-an-authorized-test-wallet>' `
  --token-env PRIVATE_PAPER_WALLET_TOKEN `
  --require-wallet --check-docker `
  --report private-demo-evidence.json
```

When running live mode without `--require-wallet`, the report explicitly
marks authenticated API checks unperformed; it cannot stand in for full
wallet acceptance. Do not run this against a shared or public endpoint.
If `M8_PAPER_LOCAL_INSECURE_ENABLED=true`, the anonymous checks must fail.

### Inspecting M15 funding results

`RECONCILED` means the *reported* initial synthetic USDT plus stored
USDT quote movements match the reported available USDT balance. It does
not prove fair market prices, complete equity marking or returns.

`LEGACY_UNVERIFIED` is retained when pre-V14 wallet funding lacks an
immutable record. It must **not** be converted to confirmed funding by
guessing from the latest balance. A `MISMATCH` is reported as a check failure.

The API endpoints are distinct HTTP reads; an active wallet may change
between them even when each endpoint uses a repeatable-read DB transaction.
If concurrent paper fills are possible, run a controlled acceptance fixture,
or investigate any cross-response mismatch rather than reclassifying it.

## Further work that requires a real operator or external evidence

1. **M1–M2:** controlled fixed-rate throughput, peak host/container CPU and
   RAM, broker/database/service outages, durable replay, poison message
   treatment, consumer lag and incident response measurements.
2. **M3–M5:** real timestamp-vintage exchange data, news-provider terms and
   permissions, independently labeled relevance/sentiment examples, real
   untouched signal holdout, calibration and expiry safeguards.
3. **M6–M7:** wallet funding reconciliation on real private test data, restart
   evidence, complete contemporaneous equity marks, cost-adjusted matching
   benchmarks and prospective paper-forward performance.
4. **M8+ security:** verified user identity and wallet ownership, recovery,
   rate limiting, login/session controls, TLS, alerting, key rotation and
   security/privacy review. A `pw_` bearer capability only proves possession.
5. **Release:** genuine browser/mobile tests against approved infrastructure,
   deployment/backup rehearsal, provider/legal approvals, operations runbooks,
   explicit accountable human acceptance and scope-specific release decision.

GitHub CI tests **this tooling** and static references only. It does not
execute production/private stack checks, receive user credentials, or
make release decisions.

### Local test evidence at PR creation

The acceptance runner's Python unit tests passed in a local isolated
workspace with offline/mock fixtures (14 tests). The repository was not
fully cloned into that environment, so Java/Gradle, the actual Docker
stack and provider credentials were not exercised there. The new GitHub
CI workflow is the source of truth for checks on the PR branch.
