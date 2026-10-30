# M6 read-only deployment acceptance. Does not create wallets, orders or fills.
$ErrorActionPreference = 'Stop'
Write-Host '=== M6 read-only acceptance ==='
& .\scripts\verify-m6-schema.ps1
if ($LASTEXITCODE -ne 0) { throw 'M6 schema verification failed' }
$unknownWallet = '00000000-0000-0000-0000-000000000000'
$base = "http://localhost:8084/api/v1/paper/wallets/$unknownWallet"
foreach ($path in @('balances', 'orders?limit=20')) {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$base/$path" -TimeoutSec 20
    if ($response.StatusCode -ne 200) { throw "Unexpected HTTP status for $path" }
    $json = ConvertFrom-Json -InputObject $response.Content
    if ($null -ne $json -and $json -isnot [array]) { throw "$path did not return JSON array" }
    Write-Host "[PASS] $path read-only endpoint HTTP 200"
}
try {
    $null = Invoke-WebRequest -UseBasicParsing -Uri "$base/orders?limit=0" -TimeoutSec 20
    throw 'Expected invalid limit to fail'
} catch [System.Net.WebException] {
    if ([int]$_.Exception.Response.StatusCode -ne 400) { throw 'Expected HTTP 400 on invalid limit' }
    Write-Host '[PASS] Invalid history limit rejected'
}
try {
    $null = Invoke-WebRequest -UseBasicParsing -Method Post -ContentType 'application/json' -Body '{"initialUsdt":"100"}' -Uri 'http://localhost:8084/api/v1/paper/wallets' -TimeoutSec 20
    throw 'Paper commands unexpectedly enabled'
} catch [System.Net.WebException] {
    if ([int]$_.Exception.Response.StatusCode -ne 403) { throw 'Expected HTTP 403 when paper commands disabled' }
    Write-Host '[PASS] Paper commands disabled by default'
}
Write-Host '[PASS] M6 read-only acceptance complete. No rows changed.'
