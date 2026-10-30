# M5 read-only deployment acceptance. No writes or model activation.
$ErrorActionPreference = 'Stop'
Write-Host '=== M5 offline research signal acceptance ==='
$sql = "SELECT CASE WHEN to_regclass('public.research_signals') IS NOT NULL AND EXISTS (SELECT 1 FROM flyway_schema_history WHERE version = '8' AND success) THEN 'PASS' ELSE 'FAIL' END || '|V8 research_signals table';"
$result = $sql | & docker.exe exec -i postgres psql -X -A -t -v ON_ERROR_STOP=1 -U trader -d trades
$cleanResult = @($result | ForEach-Object { $_.ToString().Trim() })
if ($LASTEXITCODE -ne 0 -or $cleanResult -notcontains 'PASS|V8 research_signals table') {
    throw "V8 migration verification failed: $($cleanResult -join ", ")"
}
Write-Host '[PASS] V8 research_signals table present'
$base = 'http://localhost:8084/api/v1/research/signals'
$asOf = [uri]::EscapeDataString([DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))
foreach ($symbol in @('BTCUSDT','ETHUSDT')) {
    $url = '{0}?symbol={1}&asOf={2}&limit=20' -f $base,$symbol,$asOf
    $response = Invoke-WebRequest -UseBasicParsing -Uri $url -TimeoutSec 15
    if ($response.StatusCode -ne 200) { throw "$symbol returned HTTP $($response.StatusCode)" }
    $data = ConvertFrom-Json -InputObject $response.Content
    if ($null -ne $data -and $data -isnot [array]) { throw "$symbol returned non-array JSON" }
    Write-Host "[PASS] $symbol research signals API returns an array"
}
foreach ($query in @(
    ('symbol=DOGEUSDT&asOf={0}&limit=20' -f $asOf),
    ('symbol=BTCUSDT&asOf={0}&limit=0' -f $asOf)
)) {
    try {
        $null = Invoke-WebRequest -UseBasicParsing -Uri ('{0}?{1}' -f $base,$query) -TimeoutSec 15
        throw 'Expected 400 but request succeeded'
    } catch [System.Net.WebException] {
        if ([int]$_.Exception.Response.StatusCode -ne 400) { throw 'Expected HTTP 400' }
        Write-Host '[PASS] Invalid request rejected'
    }
}
Write-Host '[PASS] M5 read-only deployment acceptance finished.'
