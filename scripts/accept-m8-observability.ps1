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
$metrics = Get-Json '/actuator/metrics'
if (-not ($metrics.names -contains 'jvm.memory.used')) {
    throw 'Expected JVM memory metric missing'
}
if (-not ($metrics.names -contains 'process.uptime')) {
    throw 'Expected process uptime metric missing'
}
Write-Host '[PASS] JVM memory and process uptime metrics available'
Write-Host '[REVIEW] Health and metrics endpoint exposure should be restricted in production'
Write-Host '[REVIEW] Metrics presence does not establish alerting, retention or dashboard validation'
Write-Host '[PASS] Read-only observability smoke complete; no rows or containers changed'
