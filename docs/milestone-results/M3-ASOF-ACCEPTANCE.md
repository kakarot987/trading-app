# M3 technical features — as-of acceptance and limitations

This is the test plan for the phase-level M3 hardening changes in PR #18.
It does **not** change `docs/MILESTONES.md` or claim that all live acceptance
criteria have been independently completed.

## Technical invariants

| Condition | Required behavior | Automated evidence |
| --- | --- | --- |
| UTC 4h boundaries | Only closed, aligned intervals contribute | `CandleSeriesTest`, `FeatureSnapshotTest` |
| Duplicate trade replay | Stable trade IDs do not double-count OHLCV | `CandleSeriesTest`, `FourHourCandleWriterIntegrationTest` |
| Late trade arriving before recompute | A prior 4h bar is updated, not inserted twice | `FourHourCandleWriterIntegrationTest` |
| Missing historical candle | Indicators refuse gaps, rather than imputing | `FeatureSnapshotTest`, `FeatureSnapshotServiceIntegrationTest` |
| Future bar/gap/outlier | Earlier as-of features remain unchanged | `FeatureSnapshotTest` |
| Mixed candle sources | Foreign source cannot fill a missing local-trade bar | `FeatureSnapshotServiceIntegrationTest` |
| Unknown observation time | Excluded from as-of read rather than presumed timely | `FeatureSnapshotServiceIntegrationTest` |
| Late revision | Not incorporated into an earlier as-of feature read | `FeatureSnapshotServiceIntegrationTest` |
| Idempotent snapshot persistence | Same observation is not duplicated; later observation preserved | `FeatureSnapshotWriterIntegrationTest` |
| Snapshot freshness | Stale/future snapshots are rejected for scheduled capture | `FeatureSnapshotFreshnessTest` |

## Run locally

From the M9 feature worktree with Docker Desktop enabled:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Duser.timezone=UTC"
try {
    .\gradlew.bat :historical-service:test --tests "*CandleSeriesTest" --tests "*FeatureSnapshotTest" --tests "*FeatureSnapshotServiceIntegrationTest" --tests "*FourHourCandleWriterIntegrationTest" --tests "*FeatureSnapshotWriterIntegrationTest" --tests "*FeatureSnapshotFreshnessTest"
}
finally {
    Remove-Item Env:\JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
}
```

The integration tests start disposable Testcontainers PostgreSQL instances.
They do not mutate the running trading database.

## Important provenance limitation

The `candles` table stores **one current row per candle**, not a revision
history. The feature query now requires `data_source = 'local_trades'` and a
non-null `source_observed_at_ms` no later than its observation cutoff.

If a candle was revised *after* a historical cutoff, the current row is
excluded from that earlier as-of calculation. This is conservative and avoids
leakage, but it cannot reconstruct the earlier historical revision. Accurate
vintage-by-vintage offline backtests would require an immutable candle
observation/revision archive and as-of selection of the latest previously
observed version. Older legacy candles without observation timestamps are
also excluded until rebuilt with trustworthy provenance.

## Remaining manual/external acceptance

- Verify real provider publication/observation timestamps and cross-source
  alignment on representative BTCUSDT and ETHUSDT samples.
- Exercise actual late arrivals/backfill and inspect a fully timestamped
  sample dataset, including source revisions.
- Measure performance and correctness under the real local ingestion workload.
- Record environment, dataset, sample size, failures and reproducible outputs
  before treating the entire M3 exit criterion as accepted.
