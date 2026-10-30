# Read-only M3 final acceptance against PostgreSQL.
# Checks 50 contiguous, source-labelled 4h candles and available feature values.
$ErrorActionPreference = 'Stop'
function Sql([string]$query) {
    $lines = $query | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL acceptance query failed' }
    return ($lines -join "`n").Trim()
}
$checks = @'
WITH expected AS (
  SELECT symbol FROM (VALUES ('BTCUSDT'), ('ETHUSDT')) AS s(symbol)
), coverage AS (
  SELECT e.symbol,
         count(c.open_time) AS bars,
         count(DISTINCT c.open_time) AS distinct_bars,
         min(c.open_time) AS first_open,
         max(c.open_time) AS last_open,
         count(c.open_time) FILTER (
           WHERE c.close_time <> c.open_time + 14400000
              OR c.open_time % 14400000 <> 0
              OR c.data_source <> 'binance_spot_klines'
              OR c.open <= 0 OR c.close <= 0 OR c.low <= 0
              OR c.high < greatest(c.open, c.close, c.low)
              OR c.low > least(c.open, c.close)
              OR c.volume < 0
         ) AS invalid_bars
  FROM expected e
  LEFT JOIN candles c ON c.symbol = e.symbol AND c.interval_trade = '240m'
  GROUP BY e.symbol
), snapshots AS (
  SELECT symbol, count(*) AS total,
         count(*) FILTER (
           WHERE feature_version <> 'technical-4h-v1'
              OR interval_trade <> '4h'
              OR horizon_hours <> 4
              OR available_at_ms < candle_close_time_ms
              OR available_at_ms > (extract(epoch FROM now()) * 1000)::bigint
              OR NOT (
                ema12::text NOT IN ('NaN','Infinity','-Infinity') AND ema26::text NOT IN ('NaN','Infinity','-Infinity') AND rsi14::text NOT IN ('NaN','Infinity','-Infinity')
                AND macd_line::text NOT IN ('NaN','Infinity','-Infinity') AND macd_signal::text NOT IN ('NaN','Infinity','-Infinity')
                AND macd_histogram::text NOT IN ('NaN','Infinity','-Infinity') AND volatility20::text NOT IN ('NaN','Infinity','-Infinity')
                AND volume_ratio20::text NOT IN ('NaN','Infinity','-Infinity')
              )
              OR rsi14 < 0 OR rsi14 > 100 OR volatility20 < 0
              OR volume_ratio20 < 0
         ) AS invalid_snapshots,
         count(*) FILTER (
           WHERE NOT EXISTS (
             SELECT 1 FROM candles c
             WHERE c.symbol = technical_feature_snapshots.symbol
               AND c.interval_trade = '240m'
               AND c.close_time = technical_feature_snapshots.candle_close_time_ms
           )
         ) AS missing_source_candle
  FROM technical_feature_snapshots
  GROUP BY symbol
)
SELECT e.symbol || '|' ||
 CASE WHEN c.bars >= 35
       AND c.distinct_bars = c.bars
       AND c.last_open - c.first_open = (c.bars - 1) * 14400000
       AND c.invalid_bars = 0
       AND coalesce(s.total, 0) > 0
       AND coalesce(s.invalid_snapshots, 0) = 0
       AND coalesce(s.missing_source_candle, 0) = 0
 THEN 'PASS' ELSE 'FAIL' END ||
 '|bars=' || c.bars || ',snapshots=' || coalesce(s.total, 0) ||
 ',invalid_candles=' || c.invalid_bars ||
 ',invalid_snapshots=' || coalesce(s.invalid_snapshots, 0) ||
 ',missing_source=' || coalesce(s.missing_source_candle, 0)
FROM expected e
JOIN coverage c USING(symbol)
LEFT JOIN snapshots s USING(symbol)
ORDER BY e.symbol;
'@
Write-Host '=== M3 read-only final acceptance ==='
$output = Sql $checks
$rows = @($output -split "`n")
foreach ($row in $rows) { Write-Host $row }
if ($rows.Count -ne 2 -or @($rows | Where-Object { $_ -notmatch '^(BTCUSDT|ETHUSDT)\|PASS\|' }).Count -gt 0) {
    throw 'M3 acceptance failed; inspect query output before merge.'
}
Write-Host '[PASS] Both symbols have contiguous sourced 4h candles and valid persisted feature snapshots.'
