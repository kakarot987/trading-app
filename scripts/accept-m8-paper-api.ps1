# Read-only loopback API smoke after M8 deployment.
# Does not create wallets, execute orders, or expose bearer tokens.
param(
    [string]$BaseUrl = 'http://127.0.0.1:8084',
    [string]$WalletId = '8526fb2f-ccf1-487b-9f4e-0094b412b45e',
    [switch]$ExpectDemoBypass
)
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
if ($base -notmatch '^http://(127\.0\.0\.1|localhost)(:\d+)?$') {
    throw 'Only loopback HTTP is permitted'
}
if ($WalletId -notmatch '^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$') {
    throw 'Invalid wallet UUID'
}
Write-Host '=== M8 paper API smoke (read-only) ==='
$url = "$base/api/v1/paper/wallets/$WalletId/evaluation"
try {
    $response = Invoke-WebRequest -Uri $url -TimeoutSec 15 -UseBasicParsing
    $status = [int]$response.StatusCode
    $body = $response.Content | ConvertFrom-Json
} catch {
    if ($null -eq $_.Exception.Response) { throw }
    $status = [int]$_.Exception.Response.StatusCode
}
if ($ExpectDemoBypass) {
    if ($status -ne 200) { throw "Expected demo-bypass HTTP 200, got $status" }
    if ($null -eq $body.activity -or $null -eq $body.limitations) {
        throw 'Missing activity or limitations fields'
    }
    Write-Host '[PASS] Explicit localhost demo response includes activity and limitations'
} else {
    if ($status -ne 401) { throw "Expected default-deny HTTP 401, got $status" }
    Write-Host '[PASS] Unauthenticated paper endpoint denied with HTTP 401'
}
Write-Host '[PASS] Read-only paper API smoke complete'
