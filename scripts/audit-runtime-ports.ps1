# Read-only inspection of ACTUAL running Compose container host port bindings.
# Does not print environment values, recreate containers, or change data.
param([string]$Project = 'trading-app')
$ErrorActionPreference = 'Stop'
if ($Project -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_-]*$') { throw 'Invalid Compose project name' }
Write-Host '=== Runtime Docker port exposure audit (read-only) ==='
$ids = @(& docker ps --filter "label=com.docker.compose.project=$Project" --format '{{.ID}}')
if ($LASTEXITCODE -ne 0) { throw 'docker ps failed' }
$ids = @($ids | Where-Object { $_ -match '^[a-f0-9]+$' })
if ($ids.Count -eq 0) { throw "No running containers found for Compose project '$Project'" }
$checked = 0
$errors = @()
foreach ($id in $ids) {
    $raw = & docker inspect --format '{{json .HostConfig.PortBindings}}' $id
    if ($LASTEXITCODE -ne 0) { throw "docker inspect failed for $id" }
    $container = & docker inspect --format '{{.Name}}' $id
    if ($LASTEXITCODE -ne 0) { throw "docker inspect name failed for $id" }
    $bindings = $raw | ConvertFrom-Json
    if ($null -eq $bindings) { continue }
    foreach ($port in $bindings.PSObject.Properties) {
        foreach ($binding in @($port.Value)) {
            if ($null -eq $binding) { continue }
            $checked++
            if ($binding.HostIp -ne '127.0.0.1') {
                $errors += "$container $($port.Name) is bound to '$($binding.HostIp)' (expected 127.0.0.1)"
            }
        }
    }
}
if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Host "[FAIL] $_" }
    throw 'Running container port exposure is not loopback-only; do not expose demo APIs'
}
if ($checked -eq 0) { Write-Host '[REVIEW] No published host ports found' }
else { Write-Host "[PASS] All $checked published host bindings on running project containers are IPv4 loopback" }
Write-Host '[REVIEW] Does not audit other Compose projects, container-to-container access or IPv6 routing'
Write-Host '[REVIEW] Local paper API demo bypass remains insecure for untrusted networks'
Write-Host '[PASS] Runtime audit complete; containers and data unchanged'
