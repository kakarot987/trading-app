# Read-only M5 observation coverage. Historical candle backfills are not
# automatically valid historical feature observations.
$ErrorActionPreference = 'Stop'
$sql = @'
WITH observation AS (
 SELECT symbol, count(*) AS features,
        min(available_at_ms) AS first_observed,
        max(available_at_ms) AS last_observed
 FROM technical_feature_snapshots
 WHERE interval_trade='4h'
 GROUP BY symbol
), eligible AS (
 SELECT f.symbol, count(*) AS labeled
 FROM technical_feature_snapshots f
 JOIN candles current_c ON current_c.symbol=f.symbol
     AND current_c.interval_trade='240m'
     AND current_c.close_time=f.candle_close_time_ms
 JOIN candles next_c ON next_c.symbol=f.symbol
     AND next_c.interval_trade='240m'
     AND next_c.close_time=f.candle_close_time_ms + 14400000
 WHERE f.interval_trade='4h'
   AND f.available_at_ms >= f.candle_close_time_ms
   AND f.available_at_ms < f.candle_close_time_ms + 14400000
 GROUP BY f.symbol
)
SELECT s.symbol || ' | observed_features=' || coalesce(o.features,0)
  || ' | labeled_next_bars=' || coalesce(e.labeled,0)
  || ' | first_available_ms=' || coalesce(o.first_observed::text,'none')
  || ' | last_available_ms=' || coalesce(o.last_observed::text,'none')
FROM (VALUES ('BTCUSDT'),('ETHUSDT')) s(symbol)
LEFT JOIN observation o ON o.symbol=s.symbol
LEFT JOIN eligible e ON e.symbol=s.symbol
ORDER BY s.symbol;
'@
Write-Host '=== M5 read-only historical observation coverage ==='
$sql | & docker.exe exec -i postgres psql -X -v ON_ERROR_STOP=1 -U trader -d trades
if ($LASTEXITCODE -ne 0) { throw 'M5 coverage query failed' }
Write-Host 'These are coverage counts, not evidence of trained-model quality.'
