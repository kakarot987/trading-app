# Read-only M1/M2 acceptance runner. Does not publish events or stop services.
# Uses actual DB, Kafka consumer group and Docker-state evidence.
param(
    [switch]$IncludeSampling,
    [ValidateRange(2,120)][int]$Samples = 6,
    [ValidateRange(1,3600)][int]$IntervalSeconds = 10
)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    Write-Host '=== M1/M2 acceptance prerequisites: read-only ==='
    & (Join-Path $root 'scripts/verify-m2.ps1')
    if (-not $?) { throw 'M2 read-only verification failed' }
    & (Join-Path $root 'scripts/inspect-m9-kafka-replay.ps1')
    if (-not $?) { throw 'Kafka replay inspection failed' }
    if ($IncludeSampling) {
        & (Join-Path $root 'scripts/measure-m1-m2-readonly.ps1') -Samples $Samples -IntervalSeconds $IntervalSeconds
        if (-not $?) { throw 'M1/M2 observation sampling failed' }
    } else {
        Write-Host '[REVIEW] Add -IncludeSampling to collect chronological lag/resource snapshots'
    }
    Write-Host '[PASS] Read-only prerequisite checks completed'
    Write-Host '[REVIEW] Does not validate controlled outages, true fixed-rate throughput or external provider key rotation'
} finally {
    Pop-Location
}
