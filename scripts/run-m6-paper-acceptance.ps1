# Opt-in local-only paper-command acceptance.
# Starts a SEPARATE historical-service container on 127.0.0.1:18084.
# Leaves the existing historical-data-service untouched. Test wallet persists.
$ErrorActionPreference = 'Stop'
$name = 'm6-paper-acceptance'
$env:M6_PAPER_TEST_PORT = '18084'
$compose = @('compose','-p','trading-app','--env-file','..\trading-app\.env')
$existing = & docker.exe ps -a --format '{{.Names}}' --filter "name=^$name$"
if ($LASTEXITCODE -ne 0) { throw 'Could not inspect Docker containers' }
if ($existing -contains $name) { throw "Container $name already exists. Inspect it before removing it." }
try {
    & docker.exe @compose run -d --no-deps --name $name -p '127.0.0.1:18084:8084' -e 'M6_PAPER_COMMANDS_ENABLED=true' historical-data-service
    if ($LASTEXITCODE -ne 0) { throw 'Could not start isolated paper acceptance container' }
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 2
        try {
            $null = Invoke-RestMethod -Uri 'http://127.0.0.1:18084/api/v1/paper/wallets/00000000-0000-0000-0000-000000000000/balances' -TimeoutSec 3
            $ready = $true
            break
        } catch { }
    }
    if (-not $ready) { throw 'Acceptance container did not become ready' }
    & .\scripts\accept-m6-paper-commands.ps1
    if (-not $?) { throw 'Paper command acceptance failed' }
} finally {
    & docker.exe rm -f $name | Out-Null
    Remove-Item Env:\M6_PAPER_TEST_PORT -ErrorAction SilentlyContinue
    Write-Host 'Dedicated paper acceptance container removed; normal service unchanged.'
}
