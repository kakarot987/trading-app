# Read-only audit of persisted simulated fills and double-entry movement.
# Does NOT initiate orders, change balances or connect to an exchange.
$ErrorActionPreference = 'Stop'
Write-Host '=== M6 persisted paper ledger consistency (read only) ==='
$sql = @'
WITH anomalies AS (
  SELECT f.fill_id FROM paper_fills f
  JOIN paper_orders o ON o.order_id=f.order_id
  LEFT JOIN paper_ledger_entries base ON base.fill_id=f.fill_id
    AND base.asset=replace(o.symbol,'USDT','')
  LEFT JOIN paper_ledger_entries quote ON quote.fill_id=f.fill_id
    AND quote.asset='USDT'
  WHERE o.status <> 'FILLED'
    OR f.tick_time_ms <= o.submitted_at_ms
    OR f.quantity <> o.quantity
    OR f.gross_quote <> round(f.quantity*f.execution_price,8)
    OR base.entry_id IS NULL OR quote.entry_id IS NULL
    OR base.wallet_id <> o.wallet_id OR quote.wallet_id <> o.wallet_id
    OR base.amount <> CASE WHEN o.side='BUY' THEN f.quantity ELSE -f.quantity END
    OR quote.amount <> CASE WHEN o.side='BUY'
                           THEN -f.gross_quote-f.fee_quote
                           ELSE f.gross_quote-f.fee_quote END
), orphan_entries AS (
  SELECT l.entry_id FROM paper_ledger_entries l
  LEFT JOIN paper_fills f ON f.fill_id=l.fill_id
  LEFT JOIN paper_orders o ON o.order_id=f.order_id
  WHERE o.order_id IS NULL OR o.wallet_id <> l.wallet_id
), invalid_orders AS (
  SELECT o.order_id FROM paper_orders o
  LEFT JOIN paper_fills f ON f.order_id=o.order_id
  WHERE (o.status='FILLED' AND f.fill_id IS NULL)
     OR (o.status<>'FILLED' AND f.fill_id IS NOT NULL)
), unbalanced_base AS (
  SELECT b.wallet_id,b.asset FROM paper_balances b
  WHERE b.asset IN ('BTC','ETH')
    AND b.available <> coalesce((
      SELECT sum(l.amount) FROM paper_ledger_entries l
      WHERE l.wallet_id=b.wallet_id AND l.asset=b.asset
    ),0)
), missing_base AS (
  SELECT l.wallet_id,l.asset FROM paper_ledger_entries l
  LEFT JOIN paper_balances b ON b.wallet_id=l.wallet_id AND b.asset=l.asset
  WHERE l.asset IN ('BTC','ETH') AND b.wallet_id IS NULL
  GROUP BY l.wallet_id,l.asset
)
SELECT CASE WHEN (SELECT count(*) FROM anomalies)=0
  AND (SELECT count(*) FROM orphan_entries)=0
  AND (SELECT count(*) FROM invalid_orders)=0
  AND (SELECT count(*) FROM unbalanced_base)=0
  AND (SELECT count(*) FROM missing_base)=0
  THEN 'PASS' ELSE 'FAIL' END || '|Paper fills, order states and base asset ledger';
'@
$result = @($sql | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades)
if ($LASTEXITCODE -ne 0) { throw 'M6 read-only ledger audit query failed' }
$checks = @($result | ForEach-Object { $_.ToString().Trim() } | Where-Object { $_ -match '^(PASS|FAIL)\|' })
$checks | ForEach-Object { Write-Host $_ }
if ($checks.Count -ne 1 -or $checks[0] -notmatch '^PASS\|') {
    throw 'M6 ledger consistency check failed'
}
Write-Host '[PASS] Read-only M6 ledger consistency audit finished.'
