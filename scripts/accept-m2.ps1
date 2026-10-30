# M2 live acceptance: writes uniquely tagged test events to existing Kafka.
# Does not stop/restart containers, drop tables, reset offsets, or change sources.
$ErrorActionPreference = 'Stop'
$runId = 'M2ACCEPT-' + [guid]::NewGuid().ToString('N')
$validId = "$runId-VALID"
$invalidId = "$runId-INVALID"
$results = [ordered]@{}
function Docker {
    param([string[]]$Arguments, [string]$InputText)
    if ($PSBoundParameters.ContainsKey('InputText')) {
        $output = $InputText | & docker @Arguments 2>&1
    } else {
        $output = & docker @Arguments 2>&1
    }
    if ($LASTEXITCODE -ne 0) { throw "docker $($Arguments -join ' ') failed: $($output -join ' ')" }
    return ($output -join "`n").Trim()
}
function Sql([string]$Query) {
    return Docker -Arguments @('exec','postgres','psql','-U','trader','-d','trades','-t','-A','-c',$Query)
}
function WaitFor([string]$Label, [scriptblock]$Predicate, [int]$Seconds = 60) {
    $deadline = (Get-Date).AddSeconds($Seconds)
    while ((Get-Date) -lt $deadline) {
        if (& $Predicate) { Write-Host "[PASS] $Label"; $results[$Label] = 'PASS'; return }
        Start-Sleep -Seconds 2
    }
    throw "$Label not confirmed within $Seconds seconds"
}
function Produce([string]$Json) {
    # Standard input is forwarded to kafka-console-producer using docker -i.
    Docker -Arguments @('exec','-i','kafka','/opt/kafka/bin/kafka-console-producer.sh','--bootstrap-server','localhost:9092','--topic','trade-events') -InputText $Json | Out-Null
}
try {
    Write-Host "Acceptance run: $runId"
    if (-not ((Docker -Arguments @('ps','--format','{{.Names}}')) -split "`n" -contains 'trade-processor')) {
        throw 'trade-processor is not running'
    }
    if ((Sql "SELECT to_regclass('public.trade_outbox') IS NOT NULL AND to_regclass('public.trade_quarantine') IS NOT NULL;") -ne 't') {
        throw 'M2 schema is not installed'
    }
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $valid = @{symbol='BTCUSDT';exchange='M2_ACCEPTANCE';price=70000.0;volume=0.001;tradeTimestamp=$now;side='BUY';trade_id=$validId} | ConvertTo-Json -Compress
    $invalid = @{symbol='BTCUSDT';exchange=$invalidId;price=70000.0;volume=0.001;tradeTimestamp=$now;side='BUY';trade_id=''} | ConvertTo-Json -Compress

    Write-Host 'Publishing valid trade and duplicate replay'
    Produce $valid
    Produce $valid
    WaitFor 'Exactly one persisted valid trade' {
        (Sql "SELECT COUNT(*) FROM trades WHERE trade_id = '$validId';") -eq '1'
    }
    WaitFor 'Valid trade outbox published' {
        (Sql "SELECT COUNT(*) FROM trade_outbox WHERE trade_id = '$validId' AND published_at IS NOT NULL;") -eq '1'
    }
    $duplicateCount = Sql "SELECT COUNT(*) FROM trade_outbox WHERE trade_id = '$validId';"
    if ($duplicateCount -ne '1') { throw "Duplicate produced $duplicateCount outbox entries" }
    $results['Duplicate replay prevented'] = 'PASS'
    Write-Host '[PASS] Duplicate replay prevented'

    Write-Host 'Publishing invalid-ID trade'
    Produce $invalid
    WaitFor 'Invalid trade quarantined' {
        (Sql "SELECT COUNT(*) FROM trade_quarantine WHERE event_payload LIKE '%$runId-INVALID%';") -gt 0
    }

    # Confirm a later valid event can flow past the invalid one.
    $laterId = "$runId-AFTER"
    $later = @{symbol='BTCUSDT';exchange='M2_ACCEPTANCE';price=70001.0;volume=0.001;tradeTimestamp=$now;side='BUY';trade_id=$laterId} | ConvertTo-Json -Compress
    Produce $later
    WaitFor 'Valid event after quarantine processed' {
        (Sql "SELECT COUNT(*) FROM trades WHERE trade_id = '$laterId';") -eq '1'
    }

    Write-Host 'Live processing, deduplication and invalid-ID quarantine checks passed.'
    Write-Host 'Broker-outage fault injection NOT run (would disrupt other services).'
} catch {
    Write-Host "[FAIL] $($_.Exception.Message)"
    $results['failure'] = $_.Exception.Message
    exit 1
} finally {
    $report = [ordered]@{run_id=$runId;checked_at_utc=(Get-Date).ToUniversalTime().ToString('o');results=$results}
    $report | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 'm2-live-acceptance-report.json'
    Write-Host 'Report: m2-live-acceptance-report.json'
}
