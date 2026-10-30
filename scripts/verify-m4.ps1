# Read-only M4 preflight; safe before and after news migration V7.
$ErrorActionPreference = 'Stop'
$pass = 0; $pending = 0
function Check([string]$label, [bool]$condition) {
    if ($condition) { $script:pass++; Write-Host "[PASS] $label" }
    else { $script:pending++; Write-Host "[PENDING] $label" }
}
function Sql([string]$query) {
    $lines = $query | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades
    if ($LASTEXITCODE -ne 0) { throw 'Read-only PostgreSQL query failed' }
    return ($lines -join "`n").Trim()
}
Write-Host '=== M4 read-only deployment preflight ==='
$services = @(& docker.exe compose -p trading-app ps --status running --services)
if ($LASTEXITCODE -ne 0) { throw 'Docker inspection failed' }
Check 'PostgreSQL running' ('postgres' -in $services)
Check 'Historical service running' ('historical-data-service' -in $services)
if ('postgres' -notin $services) { throw 'PostgreSQL unavailable' }
$tables = Sql "SELECT table_name FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('news_articles','news_article_symbols') ORDER BY table_name;"
Check 'M4 news_articles table exists (V7)' ($tables -split "`n" -contains 'news_articles')
Check 'M4 news_article_symbols table exists (V7)' ($tables -split "`n" -contains 'news_article_symbols')
if ($tables -split "`n" -contains 'news_articles') {
    Write-Host '=== News storage state ==='
    Write-Host (Sql 'SELECT count(*)::text || '' articles, '' || count(DISTINCT deduplication_key)::text || '' unique keys'' FROM news_articles;')
    $invalid = Sql 'SELECT count(*) FROM news_articles WHERE observed_at < published_at;'
    Check 'Stored publication/observation chronology valid' ([long]$invalid -eq 0)
}
Write-Host "Summary: $pass passed, $pending pending. No changes made."
