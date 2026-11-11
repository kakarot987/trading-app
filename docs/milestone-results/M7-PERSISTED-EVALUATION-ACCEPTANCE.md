# M7 paper evaluation — persisted-fill acceptance

This single M7 phase PR connects existing evaluation components to **actual
committed simulated fills** while declining to invent price marks, equity
curves, Sharpe ratios or strategy profitability. No live exchange execution,
no model activation and no updates to `docs/MILESTONES.md`.

## Code-level acceptance matrix

| Requirement | Verification | Remaining limitation |
| --- | --- | --- |
| Realized weighted-average inventory P&L from chronological persisted fills | PaperRealizedInventoryTest | No unrealized mark-to-market P&L |
| Quote fees included in acquisition and disposal basis | PaperRealizedInventoryTest | Actual exchange fills are not involved |
| Complete fill / ledger quantities and quote reconciliation | PersistedPaperInventoryRepositoryIntegrationTest | Initial USDT funding has no immutable funding-entry ledger |
| Duplicate, invalid, unordered or ambiguous same-time fills rejected | PaperRealizedInventoryTest | Same-time mixed-side fills require order provenance |
| Read-only evaluation endpoint returns realized inventory with no invented equity | PersistedPaperInventoryRepositoryIntegrationTest | Strategy returns and drawdown still unavailable |
| Extra or mismatched ledger movements fail closed | PersistedPaperInventoryRepositoryIntegrationTest | Audit only examines paper fill postings |
| Fee-aware hypothetical buy-and-hold benchmark is explicit | PaperBenchmarkTest | Default zero benchmark costs remain an explicit assumption |
| Benchmark and strategy equity share one exact observed window | PaperEvaluationReportTest | No end-to-end historical mark series implemented |
| Empty wallet reports no realized positions and no equity metrics | PersistedPaperInventoryRepositoryIntegrationTest | A zero-fill paper demo is not performance evidence |

## Run locally

Java 21, Docker, UTC:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
    .\gradlew.bat :historical-service:test --tests '*PaperRealizedInventoryTest' --tests '*PersistedPaperInventoryRepositoryIntegrationTest' --tests '*PaperEvaluationReportTest' --tests '*PaperBenchmarkTest' --tests '*PaperPerformanceTest' --tests '*PaperRiskMetricsTest' --tests '*PaperInventoryPnlTest' --tests '*PaperCostAndExposureTest'
    .\scripts\audit-m6-paper-ledger.ps1
    .\scripts\accept-m9-paper-portfolio.ps1 -ExpectDemoBypass
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

The optional portfolio smoke is for a **private** local-demo environment
only and requires an appropriate synthetic wallet. The script's default wallet
ID must be replaced with the actual isolated wallet when needed.
Run the complete backend Gradle suite in PR CI as well.

## Phase acceptance still open

1. Collect verifiable market marks observed at or before every valuation time,
   using consistent BTC/ETH snapshots, and store a complete marked equity curve.
   Do not construct an equity curve from cash movement alone.
2. Compare contemporaneous buy-and-hold and technical-rule baselines over the
   identical window and initial capital, declaring fee/slippage assumptions.
3. Report drawdown, exposure, regime failures, missing data, and a genuinely
   prospective paper-forward track separately from backtests. Disclose sample
   size, UTC period, model version and real transaction counts.
4. Attach CI, local Docker, browser smoke and runtime evidence. Current tests
   use deterministic fixtures and isolated PostgreSQL, not a demonstrated
   strategy track record.
5. Require per-wallet authorization before exposing evaluations publicly.
   UUIDs do not prove ownership.

**Current assessment:** M7 code-level foundation is extended, but predictive
edge, real-world benchmark results, full marked-equity performance and release
acceptance are **not** established.
