# Consolidated M1-M8 evidence-reference inventory (read-only).
# Checks manifest structure and referenced files only; does not evaluate
# real-world acceptance evidence, change any service, or decide deployment.
param([string]$ReportPath = 'milestone-evidence-inventory.json')
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $root 'docs/milestone-acceptance-manifest.json'
$manifest = Get-Content -LiteralPath $source -Raw | ConvertFrom-Json -Depth 40
$expected = @('M1','M2','M3','M4','M5','M6','M7','M8')

if ($manifest.schemaVersion -ne 1) {
    throw 'Unsupported manifest version'
}
$phases = @($manifest.phases)
$actual = @($phases | ForEach-Object { [string]$_.id })
if (($actual -join ',') -ne ($expected -join ',')) {
    throw 'Expected exactly eight ordered phases, M1 through M8'
}

$knownGates = @{}
$inventory = @()
foreach ($phase in $phases) {
    $paths = @($phase.evidencePaths)
    $gates = @($phase.openGates)
    if ([string]::IsNullOrWhiteSpace([string]$phase.title) -or
        @($phase.prs).Count -eq 0 -or
        $paths.Count -eq 0 -or
        $gates.Count -eq 0) {
        throw "Incomplete phase inventory for $($phase.id)"
    }
    if (@($paths | Select-Object -Unique).Count -ne $paths.Count) {
        throw "Duplicate reference paths for $($phase.id)"
    }
    $verifiedPaths = @()
    foreach ($relative in $paths) {
        $path = [string]$relative
        if ($path -notmatch '^[a-zA-Z0-9_./-]+$' -or $path.Contains('..') -or
            $path.StartsWith('/')) {
            throw "Invalid relative evidence path: $path"
        }
        $full = Join-Path $root $path
        if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
            throw "Referenced evidence file is missing: $path"
        }
        $verifiedPaths += $path
    }
    $requirements = @()
    foreach ($gate in $gates) {
        $key = [string]$gate.id
        if ($key -notmatch '^m[1-8]-[a-z0-9-]+$' -or
            $knownGates.ContainsKey($key) -or
            [string]::IsNullOrWhiteSpace([string]$gate.requiredEvidence) -or
            [string]$gate.category -notin @('operational','security','data','external')) {
            throw "Invalid or duplicate evidence requirement: $key"
        }
        $knownGates[$key] = $true
        $requirements += [ordered]@{
            id = $key
            category = [string]$gate.category
            description = [string]$gate.requiredEvidence
        }
    }
    $inventory += [ordered]@{
        phase = [string]$phase.id
        title = [string]$phase.title
        implementation_pr_references = @($phase.prs)
        existing_document_paths = @($verifiedPaths)
        evidence_requirements_to_review = @($requirements)
    }
    Write-Host "[PASS] $($phase.id) references: $($verifiedPaths.Count) files, $($requirements.Count) evidence requirements"
}

$sha = (& git -C $root rev-parse HEAD 2>$null)
if ($LASTEXITCODE -ne 0) { throw 'Unable to read Git checkout SHA' }
$report = [ordered]@{
    schema_version = 1
    observed_at_utc = [DateTimeOffset]::UtcNow.ToString('o')
    checked_commit = ([string]$sha).Trim()
    inventory_check = 'passed'
    checked_scope = 'manifest structure and referenced file existence'
    checks_not_performed = @('runtime tests', 'external approvals', 'human review',
        'production authorization', 'model skill validation')
    phase_inventory = @($inventory)
    requirement_count = $knownGates.Count
}
$target = if ([System.IO.Path]::IsPathRooted($ReportPath)) {
    $ReportPath
} else {
    Join-Path $root $ReportPath
}
$report | ConvertTo-Json -Depth 40 |
    Set-Content -LiteralPath $target -Encoding utf8
Write-Host "[PASS] Reference inventory exported to $target"
Write-Host '[INFO] No live checks, acceptance decisions or deployment decisions were made.'
