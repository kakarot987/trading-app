# Read-only M2 acceptance preflight. Does not publish, stop, restart, or delete data.
# Run from repository root after deploying the M2 trade-processor image.
$ErrorActionPreference = 'Stop'
$failures = New-Object 'System.Collections.Generic.List[string]'
$results = [ordered]@{}

function Invoke-DockerChecked {
    param([string[]] $Arguments)
    $output = & docker @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "docker $($Arguments -join ' ') failed: $($output -join ' ')"
    }
    return ($output -join "`n")
}

function Check {
    param([string] $Name, [scriptblock] $Action)
    try {
        $value = & $Action
        $results[$Name] = @{ status = 'PASS'; details = [string]$value }
        Write-Host "[PASS] $Name : $value"
    } catch {
        $failures.Add("$Name : $($_.Exception.Message)")
        $results[$Name] = @{ status = 'FAIL'; details = $_.Exception.Message }
        Write-Host "[FAIL] $Name : $($_.Exception.Message)"
    }
}

Check 'required containers' {
    $output = Invoke-DockerChecked @('ps','--format','{{.Names}}')
    $names = $output -split "`n"
    foreach ($name in @('kafka','postgres','trade-processor')) {
        if ($names -notcontains $name) { throw "$name is not running" }
    }
    'kafka, postgres, trade-processor running'
}

Check 'PostgreSQL available' {
    $result = Invoke-DockerChecked @('exec','postgres','pg_isready','-U','trader','-d','trades')
    if ($result -notmatch 'accepting connections') { throw $result }
    $result
}

Check 'M2 schema migrated' {
    $query = "SELECT to_regclass('public.trade_outbox') IS NOT NULL AND to_regclass('public.trade_quarantine') IS NOT NULL;"
    $result = Invoke-DockerChecked @('exec','postgres','psql','-U','trader','-d','trades','-t','-A','-c',$query)
    if ($result.Trim() -ne 't') { throw 'M2 tables missing; deploy M2 service/migrations before acceptance' }
    'outbox and quarantine present'
}

Check 'unique trade IDs' {
    $query = "SELECT COUNT(*) FROM (SELECT trade_id FROM trades GROUP BY trade_id HAVING COUNT(*) > 1) duplicates;"
    $result = Invoke-DockerChecked @('exec','postgres','psql','-U','trader','-d','trades','-t','-A','-c',$query)
    if ($result.Trim() -ne '0') { throw "$result duplicate trade IDs found" }
    'no duplicate trade IDs'
}

Check 'pending outbox count' {
    $query = 'SELECT COUNT(*) FROM trade_outbox WHERE published_at IS NULL;'
    $result = Invoke-DockerChecked @('exec','postgres','psql','-U','trader','-d','trades','-t','-A','-c',$query)
    if ($result.Trim() -notmatch '^\d+$') { throw "Unexpected count: $result" }
    "$($result.Trim()) pending (informational; not necessarily a failure)"
}

Check 'quarantine count' {
    $query = 'SELECT COUNT(*) FROM trade_quarantine;'
    $result = Invoke-DockerChecked @('exec','postgres','psql','-U','trader','-d','trades','-t','-A','-c',$query)
    "$($result.Trim()) quarantined (informational)"
}

Check 'Kafka group status' {
    $result = Invoke-DockerChecked @('exec','kafka','/opt/kafka/bin/kafka-consumer-groups.sh','--bootstrap-server','localhost:9092','--describe','--group','trade-processor')
    if ($result -notmatch 'trade-events') { throw "trade-events group not assigned: $result" }
    $result
}

$report = [ordered]@{
    checked_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    results = $results
    failures = @($failures)
}
$path = Join-Path (Get-Location) 'm2-verification-report.json'
$report | ConvertTo-Json -Depth 6 | Set-Content -Encoding UTF8 $path
Write-Host "Report: $path"
if ($failures.Count -gt 0) { exit 1 }
Write-Host 'Read-only preflight passed. Fault-injection acceptance remains separate.'
