# Read-only M9 portfolio API contract smoke. Uses GET only and never creates wallets/orders.
# Demo mode is an explicit opt-in; secure mode should return 401 without a token.
param(
    [string]$BaseUrl = 'http://127.0.0.1:8084',
    [string]$WalletId = '8526fb2f-ccf1-487b-9f4e-0094b412b45e',
    [switch]$ExpectDemoBypass
)
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/')
if ($base -notmatch '^http://(127\.0\.0\.1|localhost)(:\d+)?$') {
    throw 'Only loopback HTTP targets are permitted'
}
if ($WalletId -notmatch '^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$') {
    throw 'Invalid wallet UUID'
}
$prefix = "$base/api/v1/paper/wallets/$WalletId"
Write-Host '=== M9 paper portfolio contract smoke (GET-only, localhost) ==='

function Get-Status([string]$url) {
    try {
        $response = Invoke-WebRequest -Uri $url -Method Get -UseBasicParsing -TimeoutSec 15
        return @{ Status = [int]$response.StatusCode; Body = $response.Content }
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        return @{ Status = [int]$_.Exception.Response.StatusCode; Body = $null }
    }
}

$endpoints = @(
    "$prefix/balances",
    "$prefix/orders?limit=50",
    "$prefix/evaluation"
)
$responses = @($endpoints | ForEach-Object { Get-Status $_ })
if (-not $ExpectDemoBypass) {
    foreach ($item in $responses) {
        if ($item.Status -ne 401) { throw "Expected HTTP 401 for all paper endpoints, got HTTP $($item.Status)" }
    }
    Write-Host '[PASS] All three paper portfolio endpoints default-deny unauthenticated GETs'
} else {
    foreach ($item in $responses) {
        if ($item.Status -ne 200) { throw "Expected HTTP 200 in explicitly enabled demo mode, got HTTP $($item.Status)" }
    }
    $balances = @($responses[0].Body | ConvertFrom-Json)
    $orders = @($responses[1].Body | ConvertFrom-Json)
    $evaluation = $responses[2].Body | ConvertFrom-Json
    if ($null -eq $evaluation.activity -or $null -eq $evaluation.limitations) {
        throw 'Evaluation contract missing activity or limitations'
    }
    $activity = $evaluation.activity
    if ($null -eq $activity.fillCount -or $null -eq $activity.tradedNotionalUsdt -or $null -eq $activity.paidFeesUsdt) {
        throw 'Evaluation contract missing required activity fields'
    }
    if ([int]$activity.fillCount -lt 0) { throw 'Negative persisted fill count' }
    foreach ($balance in $balances) {
        if ([string]::IsNullOrWhiteSpace([string]$balance.asset) -or $null -eq $balance.available) {
            throw 'Balance contract missing asset or available'
        }
    }
    foreach ($order in $orders) {
        if ([string]::IsNullOrWhiteSpace([string]$order.orderId) -or
            [string]::IsNullOrWhiteSpace([string]$order.symbol) -or
            [string]::IsNullOrWhiteSpace([string]$order.status)) {
            throw 'Order contract missing required fields'
        }
    }
    if ([int]$activity.fillCount -eq 0) {
        if ([decimal]$activity.tradedNotionalUsdt -ne 0 -or [decimal]$activity.paidFeesUsdt -ne 0) {
            throw 'Zero fills unexpectedly report nonzero turnover or fees'
        }
        if (@($evaluation.limitations).Count -eq 0) {
            throw 'Zero-fill evaluation should explain unavailable metrics'
        }
    }
    Write-Host "[PASS] GET balances ($($balances.Count)), orders ($($orders.Count)), and evaluation contracts"
    Write-Host "[PASS] Persisted fill count: $($activity.fillCount); zero-fill reporting guarded"
}
Write-Host '[PASS] Read-only portfolio smoke finished; no POST requests or data mutations'
Write-Host '[REVIEW] This is not a real browser test or per-wallet ownership authorization'
