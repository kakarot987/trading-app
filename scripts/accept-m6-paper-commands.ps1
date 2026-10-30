# Opt-in M6 paper-command acceptance. Creates a dedicated synthetic wallet
# and one NEW paper order. No exchange connection or real-money execution.
# This script is intentionally NOT part of the default read-only acceptance.
$ErrorActionPreference = 'Stop'
$port = if ($env:M6_PAPER_TEST_PORT) { [int]$env:M6_PAPER_TEST_PORT } else { 18084 }
$base = "http://127.0.0.1:$port/api/v1/paper"
Write-Host '=== M6 controlled synthetic wallet/order acceptance ==='
Write-Host 'Requires a separately configured local instance with M6_PAPER_COMMANDS_ENABLED=true.'
$wallet = Invoke-RestMethod -Method Post -Uri "$base/wallets" -ContentType 'application/json' -Body '{"initialUsdt":"100.00000000"}' -TimeoutSec 20
if (-not $wallet.walletId) { throw 'Wallet creation returned no ID' }
$walletId = [string]$wallet.walletId
Write-Host "[PASS] Dedicated synthetic wallet created: $walletId"
$key = "m6-test-$([guid]::NewGuid().ToString('N'))"
$submitted = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$request = @{ clientOrderId=$key; symbol='BTCUSDT'; side='BUY'; quantity='0.01000000'; submittedAtMs=$submitted } | ConvertTo-Json -Compress
$endpoint = "$base/wallets/$walletId/orders"
$first = Invoke-RestMethod -Method Post -Uri $endpoint -ContentType 'application/json' -Body $request -TimeoutSec 20
$replay = Invoke-RestMethod -Method Post -Uri $endpoint -ContentType 'application/json' -Body $request -TimeoutSec 20
if ($first.orderId -ne $replay.orderId -or $first.status -ne 'NEW' -or $replay.status -ne 'NEW') {
    throw 'Order idempotency or initial NEW state failed'
}
Write-Host '[PASS] Identical paper order replay returns original NEW order'
$balances = @(Invoke-RestMethod -Uri "$base/wallets/$walletId/balances" -TimeoutSec 20)
$cash = @($balances | Where-Object { $_.asset -eq 'USDT' })
if ($cash.Count -ne 1 -or [decimal]$cash[0].available -ne [decimal]100) {
    throw 'Paper wallet balance changed unexpectedly'
}
Write-Host '[PASS] Staged paper order does not debit initial USDT'
$orders = @(Invoke-RestMethod -Uri "$base/wallets/$walletId/orders?limit=20" -TimeoutSec 20)
if (@($orders | Where-Object { $_.clientOrderId -eq $key }).Count -ne 1) {
    throw 'Expected exactly one paper order record'
}
Write-Host '[PASS] Exactly one persisted order for synthetic client key'
Write-Host "[PASS] M6 synthetic staging acceptance complete. Test wallet retained for inspection: $walletId"
