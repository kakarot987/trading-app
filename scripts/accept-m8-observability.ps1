# Read-only observability smoke against an already-running local service.
# No container lifecycle actions, database writes, or credential output.
param([string]$BaseUrl = 'http://127.0.0.1:8084')
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
if ($base -notmatch '^http://(127\.0\.0\.1|localhost)(:\d+)?$') {
    throw 'This smoke script accepts loopback HTTP targets only'
}
Write-Host '=== M8 observability smoke (read-only, loopback only) ==='
function Get-Json([string]$path) {
    return Invoke-RestMethod -Method Get -Uri "$base$path" -TimeoutSec 15
}
$health = Get-Json '/actuator/health'
if ($health.status -ne 'UP') { throw "Health endpoint did not report UP: $($health.status)" }
Write-Host '[PASS] Actuator health reports UP'
$info = Get-Json '/actuator/info'
if ($null -eq $info) { throw 'Actuator info returned no JSON body' }
Write-Host '[PASS] Actuator info responds'
# Metrics should be unavailable over HTTP in the hardened M8 service.
try {
    $null = Invoke-WebRequest -Method Get -Uri "$base/actuator/metrics" -TimeoutSec 15 -UseBasicParsing
    throw 'Metrics are exposed over HTTP; expected HTTP 404'
} catch {
    if ($_.Exception.Message -like 'Metrics are exposed*') { throw }
    $response = $_.Exception.Response
    if ($null -eq $response -or [int]$response.StatusCode -ne 404) { throw }
}
Write-Host '[PASS] Actuator metrics are not exposed over HTTP (404)'
Write-Host '[REVIEW] Health/info exposure still needs production access policy'
Write-Host '[REVIEW] Metrics HTTP restriction does not establish alerting or dashboard validation'
Write-Host '[PASS] Read-only observability smoke complete; no rows or containers changed'
