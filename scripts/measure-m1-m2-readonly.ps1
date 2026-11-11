# Controlled, read-only sampling of existing local Docker/Kafka/Postgres.
# Observational metrics only: a fixed-rate producer and an equivalent-load
# before/after baseline must be supplied separately for M1 acceptance.
param(
    [ValidateRange(2,120)][int]$Samples = 6,
    [ValidateRange(1,3600)][int]$IntervalSeconds = 10,
    [string]$ReportPath = 'm1-m2-observation-report.json'
)
$ErrorActionPreference = 'Stop'
Write-Host '=== M1/M2 observational sampler (read-only) ==='

function DockerChecked([string[]]$CommandArgs) {
    $lines = @(& docker @CommandArgs 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Docker command failed ($($CommandArgs[0])); inspect local containers"
    }
    return $lines
}

$containers = @('kafka', 'postgres', 'redis', 'trade-processor',
    'historical-data-service', 'websocket-broadcaster', 'market-data-producer')
$active = @(DockerChecked -CommandArgs @('ps','--format','{{.Names}}'))
$selected = @($containers | Where-Object { $active -contains $_ })
foreach ($required in @('kafka','postgres','trade-processor')) {
    if ($selected -notcontains $required) {
        throw "Required container not running: $required"
    }
}
Write-Host "Sampling $($selected.Count) running project containers"

function Sample-Database {
    $result = @(DockerChecked -CommandArgs @(
        'exec','postgres','psql','-X','-A','-t','-v','ON_ERROR_STOP=1',
        '-U','trader','-d','trades','-c',
        'SELECT (SELECT count(*) FROM trades)::text || ''|'' || (SELECT count(*) FROM trade_outbox WHERE published_at IS NULL)::text || ''|'' || (SELECT count(*) FROM trade_quarantine)::text;'
    ))
    $row = @($result | ForEach-Object { $_.ToString().Trim() } |
        Where-Object { $_ -match '^\d+\|\d+\|\d+$' })
    if ($row.Count -ne 1) { throw 'Database counters unavailable or ambiguous' }
    $parts = $row[0] -split '\|'
    return @{ trades = [long]$parts[0]; pending_outbox = [long]$parts[1];
        quarantined = [long]$parts[2] }
}

function Sample-Lag {
    $lines = @(DockerChecked -CommandArgs @(
        'exec','kafka','/opt/kafka/bin/kafka-consumer-groups.sh',
        '--bootstrap-server','localhost:9092','--describe','--group','trade-processor'
    ))
    $rows = @($lines | Where-Object {
        $_ -match '^\s*\S+\s+\S+\s+\d+\s+\d+\s+\d+\s+\d+(?:\s|$)'
    })
    if ($rows.Count -eq 0) {
        throw 'No numeric Kafka partition rows: cannot measure consumer lag'
    }
    [long]$lag = 0
    foreach ($row in $rows) {
        $parts = $row.Trim() -split '\s+'
        $lag += [long]$parts[5]
    }
    return @{ total_lag = $lag; partitions = $rows.Count }
}

function Sample-Resources([string[]]$Names) {
    $command = @('stats','--no-stream','--format','{{json .}}') + $Names
    $lines = @(DockerChecked -CommandArgs $command)
    $records = @()
    foreach ($line in $lines) {
        $item = $line | ConvertFrom-Json
        $records += @{
            name = [string]$item.Name
            cpu_percent_text = [string]$item.CPUPerc
            memory_usage_text = [string]$item.MemUsage
            memory_percent_text = [string]$item.MemPerc
            pids = [string]$item.PIDs
        }
    }
    if ($records.Count -ne $Names.Count) {
        throw 'Resource snapshot is missing a requested running container'
    }
    return $records
}

$observations = @()
$started = [DateTimeOffset]::UtcNow
try {
    for ($i = 0; $i -lt $Samples; $i++) {
        $db = Sample-Database
        $kafka = Sample-Lag
        $resources = Sample-Resources $selected
        $observation = @{
            observed_at_utc = [DateTimeOffset]::UtcNow.ToString('o')
            database = $db
            consumer = $kafka
            container_resources = $resources
        }
        $observations += $observation
        Write-Host ("[{0}/{1}] persisted={2} pending={3} quarantine={4} kafka_lag={5}" -f
            ($i + 1), $Samples, $db.trades, $db.pending_outbox,
            $db.quarantined, $kafka.total_lag)
        if ($i -lt $Samples - 1) { Start-Sleep -Seconds $IntervalSeconds }
    }
} finally {
    $final = if ($observations.Count -gt 0) { $observations[-1] } else { $null }
    $initial = if ($observations.Count -gt 0) { $observations[0] } else { $null }
    $report = [ordered]@{
        status = if ($observations.Count -eq $Samples) { 'OBSERVED' } else { 'INCOMPLETE' }
        started_at_utc = $started.ToString('o')
        finished_at_utc = [DateTimeOffset]::UtcNow.ToString('o')
        requested_samples = $Samples
        collected_samples = $observations.Count
        sample_interval_seconds = $IntervalSeconds
        persisted_trade_delta = if ($initial -and $final) {
            $final.database.trades - $initial.database.trades
        } else { $null }
        observations = $observations
        limitations = @(
            'Observational snapshots only; no fixed-rate workload was generated.',
            'Container CPU percentages are not equivalent to host CPU utilization.',
            'Container memory excludes Docker Desktop and WSL host overhead.',
            'Trade count growth is not full pipeline throughput or latency.',
            'Lag snapshots alone do not prove recovery time or offset durability.',
            'No before/after comparison at equivalent input rates is established.'
        )
    }
    $report | ConvertTo-Json -Depth 12 | Set-Content -Encoding utf8 $ReportPath
    Write-Host "Wrote observational evidence: $ReportPath"
}
if ($observations.Count -ne $Samples) { throw 'Observational sample series incomplete' }
Write-Host '[PASS] M1/M2 read-only observational sampling completed (NOT milestone acceptance)'
