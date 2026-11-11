# Read-only Kafka consumer group/replay diagnostics for the local Compose stack.
# Never resets offsets, produces messages, recreates containers or reads .env.
param(
    [string]$KafkaContainer = 'kafka',
    [string]$ConsumerGroup = 'trade-processor'
)
$ErrorActionPreference = 'Stop'
if ($KafkaContainer -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$' -or
    $ConsumerGroup -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') {
    throw 'Invalid Kafka container or consumer group name'
}
Write-Host '=== M9 Kafka consumer replay diagnostic (read-only) ==='
$running = & docker inspect --format '{{.State.Running}}' $KafkaContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $running -ne 'true') {
    throw "Kafka container '$KafkaContainer' is not running"
}
Write-Host "[PASS] Kafka container $KafkaContainer running"
$command = '/opt/kafka/bin/kafka-consumer-groups.sh'
$details = @(& docker exec $KafkaContainer $command --bootstrap-server kafka:9092 --describe --group $ConsumerGroup 2>&1)
if ($LASTEXITCODE -ne 0) {
    $details | ForEach-Object { Write-Host $_ }
    throw 'Kafka group inspection failed; check broker/group readiness'
}
$details | ForEach-Object { Write-Host $_ }
$partitionRows = @($details | Where-Object {
    $_ -match '^\s*\S+\s+\S+\s+\d+\s+\d+\s+\d+\s+\d+\s+'
})
if ($partitionRows.Count -eq 0) {
    Write-Host '[REVIEW] No numeric partition-offset rows found; group may be inactive or uninitialized'
} else {
    $lags = @()
    foreach ($line in $partitionRows) {
        $parts = @($line.Trim() -split '\s+')
        $lags += [long]$parts[5]
    }
    $totalLag = ($lags | Measure-Object -Sum).Sum
    Write-Host "[INFO] Observed consumer group lag: $totalLag across $($lags.Count) partitions"
}
Write-Host '[REVIEW] Snapshot only: not evidence of bounded recovery, offset durability or exactly-once semantics'
Write-Host '[REVIEW] Investigate repeated offset resets and unexpected replay against database idempotency'
Write-Host '[PASS] Kafka inspection complete; no offsets or data changed'
