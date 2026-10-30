# Read-only M3 preflight. Does not mutate PostgreSQL, Kafka or containers.
$ErrorActionPreference = 'Stop'
$passed = 0
$warned = 0
function Check([string]$label, [bool]$ok, [string]$detail = '') {
    if ($ok) { $script:passed++; Write-Host "[PASS] $label" }
    else { $script:warned++; Write-Host "[PENDING] $label $detail" }
}
function Query([string]$statement) {
    $result = $statement | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades
    if ($LASTEXITCODE -ne 0) { throw "PostgreSQL query failed" }
    return @($result)
}
Write-Host "=== M3 read-only preflight ==="
$ps = & docker.exe compose -p trading-app ps --status running --services
if ($LASTEXITCODE -ne 0) { throw 'Docker Compose inspection failed' }
Check 'PostgreSQL running' ('postgres' -in $ps)
Check 'Historical-data service running' ('historical-data-service' -in $ps)
if ('postgres' -notin $ps) { throw 'PostgreSQL must be running for the remaining checks' }

$tables = (Query "SELECT tablename FROM pg_tables WHERE schemaname='public' AND tablename IN ('trades','candles','technical_feature_snapshots') ORDER BY tablename;")
Check 'Trades table present' ('trades' -in $tables)
Check 'Candles table present' ('candles' -in $tables)
Check 'M3 feature snapshot migration applied' ('technical_feature_snapshots' -in $tables) '(requires historical-service M3 image)'

if ('candles' -in $tables) {
    Write-Host "`n=== Four-hour candle coverage (existing database) ==="
    Query @'
SELECT symbol || ' : candles=' || count(*) ||
       ', first=' || coalesce(min(open_time)::text,'n/a') ||
       ', latest_close=' || coalesce(max(close_time)::text,'n/a')
FROM candles WHERE interval_trade = '240m' AND symbol IN ('BTCUSDT','ETHUSDT')
GROUP BY symbol ORDER BY symbol;
'@ | ForEach-Object { Write-Host $_ }
    foreach ($symbol in @('BTCUSDT', 'ETHUSDT')) {
        $n = @(Query "SELECT COUNT(*) FROM candles WHERE interval_trade='240m' AND symbol='$symbol';")[-1]
        Check "$symbol has >=35 total 4h candles (contiguity checked separately)" ([long]$n -ge 35) "(found $n)"
    }
}
if ('technical_feature_snapshots' -in $tables) {
    Write-Host "`n=== Persisted technical feature snapshots ==="
    Query @'
SELECT symbol || ' : count=' || count(*) ||
       ', last_available_at_ms=' || max(available_at_ms)
FROM technical_feature_snapshots
GROUP BY symbol ORDER BY symbol;
'@ | ForEach-Object { Write-Host $_ }
    foreach ($symbol in @('BTCUSDT', 'ETHUSDT')) {
        $n = @(Query "SELECT COUNT(*) FROM technical_feature_snapshots WHERE symbol='$symbol';")[-1]
        Check "$symbol has persisted snapshots" ([long]$n -gt 0) '(needs 35 contiguous completed 4h bars and scheduled capture)'
    }
}
Write-Host "`nSummary: $passed passed, $warned pending. No changes made."
