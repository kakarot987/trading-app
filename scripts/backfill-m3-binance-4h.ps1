# Imports verified, closed Binance spot 4h candles for BTCUSDT and ETHUSDT.
# Prerequisite: M3 historical-data-service deployed (Flyway V6 applied).
# No local trade rows, Kafka offsets, volumes, or existing non-4h bars are deleted.
param([switch]$Apply, [int]$Bars = 50)
$ErrorActionPreference = 'Stop'
if ($Bars -lt 35 -or $Bars -gt 500) { throw 'Bars must be between 35 and 500.' }
$interval = [long](4 * 60 * 60 * 1000)
$cutoff = [long]([math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() / $interval) * $interval)
$from = $cutoff - $Bars * $interval
$sqlCheck = "SELECT COUNT(*) FROM information_schema.columns WHERE table_name='candles' AND column_name='data_source';"
$count = ($sqlCheck | docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades)
if ($LASTEXITCODE -ne 0 -or [string]$count -ne '1') { throw 'M3 V6 provenance migration is missing. Deploy M3 historical service first.' }
foreach ($symbol in @('BTCUSDT','ETHUSDT')) {
    $url = "https://api.binance.com/api/v3/klines?symbol=$symbol&interval=4h&startTime=$from&endTime=$($cutoff - 1)&limit=$Bars"
    Write-Host "Fetching $symbol closed 4h candles from Binance spot API..."
    # Binance returns an array of arrays. Invoke-RestMethod can wrap the entire
    # outer array as one pipeline object in Windows PowerShell 5.1.
    # Parse raw JSON and preserve the outer array explicitly.
    $response = Invoke-WebRequest -Method Get -Uri $url -UseBasicParsing -TimeoutSec 30
    # Collect each outer kline item emitted by ConvertFrom-Json in Windows PowerShell 5.1.
    $data = @(ConvertFrom-Json -InputObject $response.Content)
    if ($data -isnot [array] -or $data.Count -ne $Bars) {
        $actual = if ($data -is [array]) { $data.Count } else { 'non-array response' }
        throw "$symbol response has $actual bars, expected $Bars. Nothing written for this symbol."
    }
    if ($data[0] -isnot [array] -or $data[0].Count -lt 11) {
        throw "$symbol response is not a Binance kline array. Nothing written."
    }
    $items = @()
    for ($i=0; $i -lt $data.Count; $i++) {
        $k = $data[$i]
        $open = [long]$k[0]
        if ($open -ne ($from + $i * $interval) -or [long]$k[6] -ne ($open + $interval - 1)) {
            throw "$symbol failed UTC alignment or consecutive 4h validation at index $i"
        }
        $o = [double]::Parse([string]$k[1],[cultureinfo]::InvariantCulture)
        $h = [double]::Parse([string]$k[2],[cultureinfo]::InvariantCulture)
        $l = [double]::Parse([string]$k[3],[cultureinfo]::InvariantCulture)
        $c = [double]::Parse([string]$k[4],[cultureinfo]::InvariantCulture)
        $v = [double]::Parse([string]$k[5],[cultureinfo]::InvariantCulture)
        if ($l -le 0 -or $h -lt $l -or $o -lt $l -or $o -gt $h -or $c -lt $l -or $c -gt $h -or $v -lt 0) {
            throw "$symbol invalid OHLCV at index $i"
        }
        $items += [ordered]@{
            symbol=$symbol; open_time=$open; close_time=($open + $interval)
            open=[string]$k[1]; high=[string]$k[2]; low=[string]$k[3]
            close=[string]$k[4]; volume=[string]$k[5]; trades=[int]$k[8]
            buy_volume=[string]$k[9]; notional_value=[string]$k[7]
        }
    }
    Write-Host "Validated $($items.Count) consecutive $symbol candles ending UTC $([DateTimeOffset]::FromUnixTimeMilliseconds($cutoff))."
    if (-not $Apply) {
        Write-Host "[DRY RUN] No DB changes. Run with -Apply after reviewing both symbols."
        continue
    }
    $json = ConvertTo-Json -InputObject @($items) -Compress -Depth 5
    $quotedJson = $json.Replace("'","''")
    $observed = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $sql = @"
BEGIN;
WITH source AS (
    SELECT * FROM jsonb_to_recordset('$quotedJson'::jsonb) AS x(
        symbol text, open_time bigint, close_time bigint,
        open numeric, high numeric, low numeric, close numeric,
        volume numeric, trades int, buy_volume numeric, notional_value numeric
    )
)
INSERT INTO candles (
    symbol, interval_trade, open_time, close_time, open, high, low,
    close, volume, trades, buy_volume, notional_value, data_source, source_observed_at_ms
)
SELECT symbol, '240m', open_time, close_time, open, high, low,
       close, volume, trades, buy_volume, notional_value,
       'binance_spot_klines', $observed
FROM source
ON CONFLICT (symbol, interval_trade, open_time)
DO UPDATE SET close_time=EXCLUDED.close_time, open=EXCLUDED.open,
 high=EXCLUDED.high, low=EXCLUDED.low, close=EXCLUDED.close,
 volume=EXCLUDED.volume, trades=EXCLUDED.trades,
 buy_volume=EXCLUDED.buy_volume, notional_value=EXCLUDED.notional_value,
 data_source=EXCLUDED.data_source,
 source_observed_at_ms=EXCLUDED.source_observed_at_ms;
COMMIT;
"@
    $sql | docker.exe exec -i postgres psql -X -v ON_ERROR_STOP=1 -U trader -d trades
    if ($LASTEXITCODE -ne 0) { throw "Backfill failed for $symbol." }
    Write-Host "[PASS] Imported verified Binance 4h candles for $symbol"
}
