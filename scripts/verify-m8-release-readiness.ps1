# M8 release evidence preflight (read-only). Does not start containers,
# mutate databases, place orders or claim that security gates have passed.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $root
Write-Host '=== M8 release readiness preflight (read-only) ==='

$required = @(
    'scripts/accept-m2.ps1',
    'scripts/accept-m3.ps1',
    'scripts/accept-m4-offline.ps1',
    'scripts/accept-m5-offline.ps1',
    'scripts/accept-m6-readonly.ps1',
    'historical-service/src/main/kotlin/com/trading/historical_service/evaluation/PaperEvaluationController.kt',
    'client-connection/src/components/portfolio/PaperPortfolioView.tsx'
)
$missing = @($required | Where-Object { -not (Test-Path (Join-Path $root $_)) })
if ($missing.Count -gt 0) { throw "Required project files missing: $($missing -join ', ')" }
Write-Host '[PASS] Expected milestone acceptance scripts and paper evaluation UI/API exist'

$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Git is unavailable' }
if ($branch -ne 'milestone/M8-portfolio-release') {
    throw "Expected M8 worktree; current branch: $branch"
}
Write-Host '[PASS] Running in M8 branch'

$compose = Get-Content (Join-Path $root 'docker-compose.yml') -Raw
if ($compose -match '(?m)^\s*-\s*["'']?0\.0\.0\.0:') {
    Write-Warning 'Compose declares explicit all-interface port bindings. Audit before release.'
}
if ($compose -match '(?m)^\s*-\s*["'']?[0-9]{2,5}:[0-9]{2,5}') {
    Write-Warning 'Compose has ports without loopback host binding. Docker may expose these to the network.'
}
Write-Host '[REVIEW] Port exposure is NOT automatically approved'

Write-Host '[REVIEW] Verify provider licensing, secrets/history, authentication, privacy and API rate limits'
Write-Host '[REVIEW] Verify backend/frontend CI and reproducible smoke tests'
Write-Host '[REVIEW] Capture current CPU/RAM, failure recovery and browser screenshots'
Write-Host '[REVIEW] Do NOT label missing mark history as verified strategy returns'
Write-Host '[REVIEW] Do NOT publish wallet APIs without ownership/authentication'
Write-Host '[PASS] Read-only preflight completed; manual release gates remain open'
