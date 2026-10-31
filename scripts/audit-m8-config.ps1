# Read-only M8 config audit. Checks tracked paths and rendered service ports.
# Does not print secret values, read .env contents, or change running containers.
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    Write-Host '=== M8 configuration audit (read-only) ==='
    $tracked = @(& git ls-files)
    if ($LASTEXITCODE -ne 0) { throw 'git ls-files failed' }
    $sensitive = @($tracked | Where-Object {
        $_ -match '(^|/)\.env($|\.)' -and $_ -notmatch '(^|/)\.env\.example$'
    })
    if ($sensitive.Count -gt 0) {
        Write-Warning "Potential environment files tracked: $($sensitive -join ', ')"
        throw 'Tracked environment files require manual review'
    }
    Write-Host '[PASS] No tracked non-example .env files'

    $ports = @(& docker compose -f (Join-Path $root 'docker-compose.yml') config --format json --no-interpolate 2>$null)
    if ($LASTEXITCODE -ne 0) {
        Write-Warning 'Cannot render Compose config without your local Docker Compose environment'
    } else {
        $rendered = ($ports -join [Environment]::NewLine) | ConvertFrom-Json
        $nonLocal = @()
        foreach ($service in $rendered.services.PSObject.Properties) {
            foreach ($mapping in @($service.Value.ports)) {
                if ($null -eq $mapping) { continue }
                if ($mapping.host_ip -ne '127.0.0.1') {
                    $nonLocal += "$($service.Name): published port without 127.0.0.1 binding"
                }
            }
        }
        if ($nonLocal.Count -gt 0) { throw ($nonLocal -join '; ') }
        Write-Host '[PASS] Published ports in rendered Compose config use IPv4 loopback'
    }
    Write-Host '[REVIEW] This does not audit Git history or prove secrets were rotated'
    Write-Host '[REVIEW] Wallet API requires ownership/auth before public deployment'
    Write-Host '[REVIEW] Provider licensing, logging redaction and dependencies require separate review'
    Write-Host '[PASS] Configuration audit completed without modifying data'
} finally {
    Pop-Location
}
