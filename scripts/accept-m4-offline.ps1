# Read-only M4 API smoke test; no writes, provider calls, or credentials.
$ErrorActionPreference = 'Stop'
$root = 'http://localhost:8084/api/v1/news'
$cutoff = [uri]::EscapeDataString([DateTimeOffset]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'))
Write-Host '=== M4 offline API acceptance ==='
foreach ($symbol in @('BTCUSDT', 'ETHUSDT')) {
    $uri = '{0}?symbol={1}&asOf={2}&limit=20' -f $root,$symbol,$cutoff
    $response = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 15
    if ($response.StatusCode -ne 200) { throw "$symbol returned HTTP $($response.StatusCode)" }
    $payload = ConvertFrom-Json -InputObject $response.Content
    if ($null -ne $payload -and $payload -isnot [array]) { throw "$symbol response must be a JSON array" }
    Write-Host "[PASS] $symbol as-of API returned HTTP 200 and array"
}
foreach ($query in @(
    ('symbol=DOGEUSDT&asOf={0}&limit=20' -f $cutoff),
    ('symbol=BTCUSDT&asOf={0}&limit=0' -f $cutoff)
)) {
    $uri = '{0}?{1}' -f $root,$query
    try {
        $null = Invoke-WebRequest -UseBasicParsing -Uri $uri -TimeoutSec 15
        throw 'Expected HTTP 400, received success'
    } catch [System.Net.WebException] {
        $status = [int]$_.Exception.Response.StatusCode
        if ($status -ne 400) { throw "Expected HTTP 400, got $status" }
        Write-Host '[PASS] Invalid request rejected with HTTP 400'
    }
}
Write-Host '[PASS] Offline news API acceptance complete.'
