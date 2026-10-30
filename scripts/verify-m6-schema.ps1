# Read-only M6 migration and ledger schema verification.
$ErrorActionPreference = 'Stop'
$sql = @'
SELECT
 CASE WHEN EXISTS (
   SELECT 1 FROM flyway_schema_history
   WHERE version='9' AND success
 ) AND EXISTS (
   SELECT 1 FROM flyway_schema_history
   WHERE version='10' AND success
 ) THEN 'PASS' ELSE 'FAIL' END || '|Flyway V9 and V10';
SELECT CASE WHEN to_regclass('public.paper_wallets') IS NOT NULL
 AND to_regclass('public.paper_orders') IS NOT NULL
 AND to_regclass('public.paper_balances') IS NOT NULL
 AND to_regclass('public.paper_fills') IS NOT NULL
 AND to_regclass('public.paper_ledger_entries') IS NOT NULL
 THEN 'PASS' ELSE 'FAIL' END || '|Paper trading tables';
SELECT CASE WHEN to_regprocedure('public.paper_post_fill(uuid,bigint,numeric,numeric,numeric)') IS NOT NULL
 THEN 'PASS' ELSE 'FAIL' END || '|Atomic fill function';
SELECT CASE WHEN EXISTS (
 SELECT 1 FROM pg_trigger
 WHERE tgrelid='public.paper_ledger_entries'::regclass
 AND tgname='trg_paper_ledger_immutable' AND NOT tgisinternal
) THEN 'PASS' ELSE 'FAIL' END || '|Immutable ledger trigger';
'@
$result = @($sql | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades)
if ($LASTEXITCODE -ne 0) { throw 'M6 schema query failed' }
$checks = @($result | Where-Object { $_ -match '^(PASS|FAIL)\|' })
$checks | ForEach-Object { Write-Host $_ }
if ($checks.Count -ne 4 -or @($checks | Where-Object { $_ -match '^FAIL\|' }).Count -gt 0) {
    throw 'M6 schema checks incomplete or failed'
}
Write-Host '[PASS] M6 migration and paper ledger schema verified; no data modified.'
