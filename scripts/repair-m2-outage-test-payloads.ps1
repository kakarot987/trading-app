# Repairs only pending records produced by scripts/accept-m2-kafka-outage.ps1.
# Preserves trade rows, Kafka offsets and all non-test outbox records.
$ErrorActionPreference = 'Stop'
$sql = @'
BEGIN;
UPDATE trade_outbox AS o
SET payload = json_build_object(
    'tradeId', t.trade_id, 'symbol', t.symbol, 'price', t.price,
    'volume', t.volume, 'timestamp', t.trade_timestamp,
    'processedAt', t.processed_at, 'latencyMs', t.latency_ms,
    'notionalValue', t.price * t.volume,
    'isBuy', t.side = 'BUY'
)::text
FROM trades AS t
WHERE o.trade_id = t.trade_id
  AND o.published_at IS NULL
  AND o.trade_id LIKE 'M2OUTAGE-%'
  AND t.exchange = 'M2_OUTAGE_ACCEPTANCE';
SELECT COUNT(*) AS remaining_pending_outage_tests
FROM trade_outbox
WHERE published_at IS NULL AND trade_id LIKE 'M2OUTAGE-%';
COMMIT;
'@
$sql | & docker.exe exec -i postgres psql -X -v ON_ERROR_STOP=1 -U trader -d trades
if ($LASTEXITCODE -ne 0) { throw 'Outage-test repair failed. No further tests should run.' }
Write-Host 'Only pending M2 outage-test outbox payloads were repaired. Allow the publisher to drain them.'
