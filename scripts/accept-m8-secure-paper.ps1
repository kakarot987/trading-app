# Read-only smoke against an already-running *secure* local service.
# This script never enables demo bypass, reveals tokens or creates paper orders.
param(
    [string]$BaseUrl = 'http://127.0.0.1:8084',
    [string]$WalletId = '00000000-0000-0000-0000-000000000000'
)
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
if ($base -notmatch '^http://(127\.0\.0\.1|localhost)(:\d+)?$') {
    throw 'Only loopback HTTP destinations are permitted'
}
if ($WalletId -notmatch '^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$') {
    throw 'Invalid wallet UUID'
}
Write-Host '=== M8 default-deny API and security headers (read only) ==='
foreach ($path in @(
    "/api/v1/paper/wallets/$WalletId/balances",
    "/api/v1/paper/wallets/$WalletId/orders?limit=20",
    "/api/v1/paper/wallets/$WalletId/evaluation",
    "/api/v1/paper/wallets"
)) {
    $response = $null
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Method Get -Uri "$base$path" -TimeoutSec 15
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $response = $_.Exception.Response
    }
    if ([int]$response.StatusCode -ne 401) {
        throw "Unauthenticated paper API expected 401: $path"
    }
    if ($response.Headers['Cache-Control'] -notmatch 'no-store') {
        throw "Paper API is not marked no-store: $path"
    }
    Write-Host "[PASS] Anonymous paper GET blocked and non-cacheable: $path"
}
$health = Invoke-RestMethod -UseBasicParsing -Method Get -Uri "$base/actuator/health" -TimeoutSec 15
if ($health.status -ne 'UP') { throw 'Historical service health not UP' }
Write-Host '[PASS] Health endpoint reachable without a paper bearer token'
Write-Host '[REVIEW] Shared token is not wallet ownership; no public deployment'
