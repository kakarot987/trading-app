# Read-only M8 paper access acceptance. Assumes historical service is already
# running. Does not create wallets, mutate DB rows, or enable command endpoints.
$ErrorActionPreference = 'Stop'
$base = 'http://127.0.0.1:8084'
$walletId = '8526fb2f-ccf1-487b-9f4e-0094b412b45e'
Write-Host '=== M8 paper access acceptance (read-only) ==='

function Get-HttpStatus([string]$url, [string]$token = '') {
    try {
        $headers = @{}
        if ($token) { $headers['Authorization'] = "Bearer $token" }
        $response = Invoke-WebRequest -Uri $url -Headers $headers -TimeoutSec 10 -UseBasicParsing
        return [int]$response.StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

$health = Get-HttpStatus "$base/actuator/health"
if ($health -ne 200) { throw "Health endpoint expected 200, got $health" }
Write-Host '[PASS] Historical service health HTTP 200'

# Default production filter must be tested with a separate, explicitly
# configured instance. Do not assume a local demo bypass proves security.
Write-Host '[REVIEW] This instance may enable M8_PAPER_LOCAL_INSECURE_ENABLED'
Write-Host '[REVIEW] Verify default-deny 401 via unit test or isolated secure instance'
Write-Host '[REVIEW] Wallet ownership authorization remains unavailable'
Write-Host '[PASS] Read-only smoke completed; no wallet rows changed'
