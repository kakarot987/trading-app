# Controlled M2 outage acceptance: temporarily stops the shared Kafka broker.
# Run ONLY when interruption of Kafka consumers/producers is acceptable.
# Never drops tables, deletes volumes, or resets offsets.
$ErrorActionPreference = 'Stop'
$runId = 'M2OUTAGE-' + [guid]::NewGuid().ToString('N')
$kafkaStopped = $false
$results = [ordered]@{}
function Invoke-DockerNative([string[]]$Arguments) {
    $output = & docker.exe @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "docker $($Arguments -join ' ') failed: $($output -join ' ')" }
    return ($output -join "`n").Trim()
}
function Sql([string]$Query) {
    return Invoke-DockerNative -Arguments @('exec','postgres','psql','-v','ON_ERROR_STOP=1','-U','trader','-d','trades','-t','-A','-c',$Query)
}
function Wait-For([string]$Name, [scriptblock]$Predicate, [int]$Seconds = 120) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        try {
            if (& $Predicate) {
                $results[$Name] = 'PASS'
                Write-Host "[PASS] $Name"
                return
            }
        } catch {
            Write-Host "Waiting for $Name : $($_.Exception.Message)"
        }
        Start-Sleep -Seconds 3
    }
    throw "Timed out: $Name ($Seconds seconds)"
}
try {
    Write-Host "Outage acceptance run: $runId"
    $running = (Invoke-DockerNative -Arguments @('ps','--format','{{.Names}}')) -split "`n"
    foreach ($container in @('kafka','postgres','trade-processor')) {
        if ($running -notcontains $container) { throw "Required container not running: $container" }
    }
    if ((Sql "SELECT to_regclass('public.trade_outbox') IS NOT NULL;") -ne 't') {
        throw 'M2 outbox migration is missing'
    }
    # Confirm the broker is healthy before causing an intentional interruption.
    if ((Invoke-DockerNative -Arguments @('inspect','kafka','--format','{{.State.Health.Status}}')) -ne 'healthy') {
        throw 'Kafka is not healthy; refusing outage test'
    }
    Write-Host 'Stopping Kafka broker (shared Kafka clients will temporarily disconnect)...'
    Invoke-DockerNative -Arguments @('stop','kafka') | Out-Null
    $kafkaStopped = $true
    $results['Kafka broker stopped'] = 'PASS'
    Write-Host '[PASS] Kafka broker stopped'

    # Create a real trade and matching outbox intent while the broker is offline.
    # This simulates the state between DB commit and successful Kafka publication.
    $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $payload = @{
        tradeId=$runId; symbol='BTCUSDT'; price=70000.0; volume=0.001
        timestamp=$timestamp; processedAt=$timestamp; latencyMs=0L
        notionalValue=70.0; isBuy=$true
    } | ConvertTo-Json -Compress
    $escapedPayload = $payload.Replace("'","''")
    $sql = @"
WITH inserted AS (
 INSERT INTO trades (trade_id, symbol, exchange, price, volume, trade_timestamp, side, processed_at, latency_ms)
 VALUES ('$runId', 'BTCUSDT', 'M2_OUTAGE_ACCEPTANCE', 70000.0, 0.001, $timestamp, 'BUY', $timestamp, 0)
 RETURNING trade_id
)
INSERT INTO trade_outbox(trade_id, payload)
SELECT trade_id, '$escapedPayload' FROM inserted;
"@
    Sql $sql | Out-Null
    $pending = Sql "SELECT COUNT(*) FROM trade_outbox WHERE trade_id = '$runId' AND published_at IS NULL;"
    if ($pending -ne '1') { throw "Expected one pending outbox entry while Kafka offline, got: $pending" }
    $results['Outbox persists during broker outage'] = 'PASS'
    Write-Host '[PASS] Outbox persists during broker outage'
} catch {
    $results['failure'] = $_.Exception.Message
    Write-Host "[FAIL] $($_.Exception.Message)"
} finally {
    if ($kafkaStopped) {
        Write-Host 'Restoring Kafka broker...'
        try {
            Invoke-DockerNative -Arguments @('start','kafka') | Out-Null
            $results['Kafka restart command'] = 'PASS'
            Write-Host '[PASS] Kafka restart command'
        } catch {
            $results['Kafka restart command'] = 'FAIL: ' + $_.Exception.Message
            Write-Host "[FAIL] COULD NOT RESTART KAFKA: $($_.Exception.Message)"
        }
    }
}
if (-not $results.Contains('failure') -and $results['Kafka restart command'] -eq 'PASS') {
    try {
        Wait-For 'Kafka healthy after restart' {
            (Invoke-DockerNative -Arguments @('inspect','kafka','--format','{{.State.Health.Status}}')) -eq 'healthy'
        } 180
        Wait-For 'Pending outbox published after recovery' {
            (Sql "SELECT COUNT(*) FROM trade_outbox WHERE trade_id = '$runId' AND published_at IS NOT NULL;") -eq '1'
        } 180
    } catch {
        $results['failure'] = $_.Exception.Message
        Write-Host "[FAIL] $($_.Exception.Message)"
    }
}
$report = [ordered]@{
    run_id = $runId
    checked_at_utc = (Get-Date).ToUniversalTime().ToString('o')
    results = $results
}
$report | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 'm2-kafka-outage-report.json'
Write-Host 'Report: m2-kafka-outage-report.json'
if ($results.Contains('failure') -or $results['Kafka restart command'] -like 'FAIL*') { exit 1 }
Write-Host 'Kafka outage/recovery acceptance passed.'
