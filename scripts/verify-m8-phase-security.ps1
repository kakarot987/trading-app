# M8 static release security gate. Read-only; no Docker process or secret access.
# CI uses this to reject regressions in the private-demo security defaults.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
function Require-Pattern([string]$name, [string]$content, [string]$pattern) {
    if ($content -notmatch $pattern) { throw "[FAIL] $name" }
    Write-Host "[PASS] $name"
}
function Reject-Pattern([string]$name, [string]$content, [string]$pattern) {
    if ($content -match $pattern) { throw "[FAIL] $name" }
    Write-Host "[PASS] $name"
}
$compose = Get-Content (Join-Path $root 'docker-compose.yml') -Raw
$filter = Get-Content (Join-Path $root 'historical-service/src/main/kotlin/com/trading/historical_service/config/PaperApiAccessFilter.kt') -Raw
$commands = Get-Content (Join-Path $root 'historical-service/src/main/kotlin/com/trading/historical_service/paper/PaperCommandController.kt') -Raw
$config = Get-Content (Join-Path $root 'historical-service/src/main/resources/application.yaml') -Raw
Write-Host '=== M8 static private-demo security gate (read-only) ==='

Require-Pattern 'Insecure local demo is default OFF in Compose' $compose '(?m)^\s*M8_PAPER_LOCAL_INSECURE_ENABLED:\s*\$\{M8_PAPER_LOCAL_INSECURE_ENABLED:-false\}'
Require-Pattern 'Paper command mutations are forced OFF in regular Compose' $compose '(?m)^\s*M6_PAPER_COMMANDS_ENABLED:\s*["'']false["'']'
Require-Pattern 'Paper token supplied by runtime environment, not hardcoded' $compose '(?m)^\s*M8_PAPER_API_TOKEN:\s*\$\{M8_PAPER_API_TOKEN:-\}'
Reject-Pattern 'No default-ON paper demo bypass' $compose '(?m)^\s*M8_PAPER_LOCAL_INSECURE_ENABLED:\s*["'']?true["'']?\s*(?:#.*)?$'
Reject-Pattern 'No automatic paper command opt-in' $compose '(?m)^\s*M6_PAPER_COMMANDS_ENABLED:\s*["'']?true["'']?\s*(?:#.*)?$'
Require-Pattern 'Paper APIs reject unauthorized requests' $filter 'HttpStatus\.UNAUTHORIZED'
Require-Pattern 'Paper API responses prohibit caching' $filter 'Cache-Control["''],\s*["'']no-store'
Require-Pattern 'Paper API rejects duplicated bearer headers' $filter 'auth\.singleOrNull\(\)'
Require-Pattern 'Paper command controller defaults OFF' $commands 'M6_PAPER_COMMANDS_ENABLED:false'
Require-Pattern 'Actuator exposes health and info only' $config '(?m)^\s*include:\s*health,info\s*$'

# Parse each Compose published port declaration, not internal listener bindings.
$published = @([regex]::Matches($compose, '(?m)^\s*-\s*["'']?((?:\d{1,3}\.){3}\d{1,3}|\[::1\])?:?\d{2,5}:\d{2,5}(?:/(?:tcp|udp))?["'']?\s*(?:#.*)?$'))
if ($published.Count -eq 0) { throw '[FAIL] Expected published Compose port mappings not found' }
foreach ($port in $published) {
    if ($port.Value -notmatch '127\.0\.0\.1:') {
        throw "[FAIL] Non-loopback published port mapping in Docker Compose: $($port.Value.Trim())"
    }
}
Write-Host "[PASS] All $($published.Count) published Compose port mappings explicitly bind IPv4 loopback"

$tracked = @(& git -C $root ls-files)
if ($LASTEXITCODE -ne 0) { throw 'Git tracked file audit unavailable' }
$envFiles = @($tracked | Where-Object {
    $_ -match '(^|/)\.env($|\.)' -and $_ -notmatch '(^|/)\.env\.example$'
})
if ($envFiles.Count -gt 0) { throw '[FAIL] Tracked non-example .env file(s)' }
Write-Host '[PASS] No tracked non-example .env paths'
Write-Host '[REVIEW] This static check cannot prove wallet ownership, running-container exposure or credential rotation'
Write-Host '[PASS] M8 static preflight passed; NOT a public-release approval'
