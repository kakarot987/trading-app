# M5 signal lifecycle, calibration and evaluation — phase acceptance

This phase-level PR hardens the existing **research-only** 4h signal engine.
No order placement, model activation, exchange execution, or MILESTONES.md updates.

## Automated acceptance matrix

| Invariant | Automated check | Limitation |
| --- | --- | --- |
| Expiry inclusive at exact boundary | SignalContractTest | Not an order scheduler or cancellation mechanism |
| As-of queries exclude future signals and optionally expired signals | ResearchSignalStoreIntegrationTest | Historic signals remain accessible by default for audit |
| Duplicate signal insert cannot change original rationale | ResearchSignalStoreIntegrationTest | Existing natural-key uniqueness remains |
| Evaluated feature observations match prediction-time metadata | CalibratedWalkForwardTest | Upstream timestamps must be trustworthy |
| Revised current candle / late label fail closed | WalkForwardDatasetTest | No immutable candle-vintage archive exists |
| Database evaluation excludes foreign, unknown and future candle provenance | HistoricalEvaluationRepository query | Dedicated SQL integration verification remains open |
| Calibrator never sees holdout labels | CalibratedWalkForwardTest, ProbabilityCalibrationTest | Baseline does not establish skill |
| Dataset fingerprint and baseline metrics reproducible | CalibratedWalkForwardTest | Fingerprint is not a signed source audit |

## Verification

With Docker Desktop and Java 21, from the feature branch:

```powershell
$env:JAVA_TOOL_OPTIONS = '-Duser.timezone=UTC'
try {
    .\gradlew.bat :historical-service:test --tests '*SignalContractTest' --tests '*ResearchSignalControllerTest' --tests '*ResearchSignalStoreIntegrationTest' --tests '*WalkForwardDatasetTest' --tests '*CalibratedWalkForwardTest' --tests '*ProbabilityCalibrationTest' --tests '*WalkForwardMetricsTest' --tests '*WalkForwardRunnerTest'
    .\scripts\verify-m5-data.ps1
    .\scripts\accept-m5-offline.ps1
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

Run `./gradlew test` in backend CI. PowerShell scripts require a private local
stack and are read-only. The research API retains historical records by default;
request `activeOnly=true` for unexpired signals only.

## Remaining gates before full phase acceptance

1. Obtain adequate *real* BTCUSDT/ETHUSDT timely observed feature history;
   historical imports must not be backdated as original-time observations.
2. Record sample sizes, UTC windows, symbols, class balance, model/feature
   versions, dataset SHA-256, calibration and holdout boundaries, and baseline
   Brier/log-loss/accuracy comparisons. Inspect abstention, drift, regimes.
3. Attach actual Docker-backed test and CI evidence, live API/SQL coverage
   evidence, and confirm research signals never issue orders.
4. Before claiming faithful historic candle vintages, implement an immutable
   revision archive; exclusion of late revisions avoids look-ahead but does
   not recreate previously seen old revisions.
5. Do not claim calibration skill, profitable trading, automated strategy
   activation, or release readiness from synthetic fixtures.

**Status: code candidate; local operational and representative real-data
acceptance remain open.**
