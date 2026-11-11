# Non-destructive verification AFTER a separately planned dependency restart.
# Does not restart containers, mutate databases, print credentials or place orders.
param(
    [string]$HistoricalUrl = 'http://127.0.0.1:8084',
    [string]$ProcessorUrl = 'http://127.0.0.1:8081',
    [string]$WalletId = '8526fb2f-ccf1-487b-9f4e-0094b412b45e',
    [switch]$ExpectLocalDemoBypass
)
$ErrorActionPreference = 'Stop'
foreach ($base in @($HistoricalUrl, $ProcessorUrl)) {
    if ($base.TrimEnd('/') -notmatch '^http://(127\.0\.0\.1|localhost)(:\d+)?$') {
        throw 'Recovery smoke only permits loopback HTTP targets'
    }
}
if ($WalletId -notmatch '^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$') {
    throw 'Invalid wallet ID'
}
$historical = $HistoricalUrl.TrimEnd('/')
$processor = $ProcessorUrl.TrimEnd('/')
Write-Host '=== M9 dependency recovery smoke (read-only) ==='
function Check-Health([string]$base, [string]$name) {
    $response = Invoke-RestMethod -Method Get -Uri "$base/actuator/health" -TimeoutSec 15
    if ($response.status -ne 'UP') { throw "$name health is not UP" }
    Write-Host "[PASS] $name health UP"
}
Check-Health $historical 'Historical service'
Check-Health $processor 'Trade processor'

# Secure mode should deny unauthenticated access; the separately enabled local
# demo should be checked against actual DB reads (not health alone).
$url = "$historical/api/v1/paper/wallets/$WalletId/evaluation"
try {
    $response = Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing -TimeoutSec 15
    $status = [int]$response.StatusCode
    $report = $response.Content | ConvertFrom-Json
} catch {
    if ($null -eq $_.Exception.Response) { throw }
    $status = [int]$_.Exception.Response.StatusCode
}
if ($ExpectLocalDemoBypass) {
    if ($status -ne 200 -or $null -eq $report.activity -or $null -eq $report.limitations) {
        throw "Expected complete local demo evaluation (200), received HTTP $status"
    }
    Write-Host '[PASS] Paper evaluation DB read returned 200 with activity and limitations'
} else {
    if ($status -ne 401) { throw "Expected default-deny 401, got HTTP $status" }
    Write-Host '[PASS] Secure paper endpoint rejected anonymous GET (401)'
}
Write-Host '[REVIEW] Health and portfolio reads do not prove Kafka replay, consumer lag or Redis cache reconstruction'
Write-Host '[PASS] Non-destructive recovery smoke finished'
