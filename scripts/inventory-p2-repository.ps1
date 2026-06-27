# Lists bundle symbolic names from a Tycho p2 repository (plugins/*.jar).
# Usage: .\scripts\inventory-p2-repository.ps1
#        .\scripts\inventory-p2-repository.ps1 -RepositoryPath releng\...\target\repository
#        .\scripts\inventory-p2-repository.ps1 -OutFile docs\inventory\assistai-bundles.txt

param(
    [string]$RepositoryPath = "releng/com.rubberjam.eclipse.assistai.repository/target/repository",
    [string]$OutFile = ""
)

$pluginsDir = Join-Path $RepositoryPath "plugins"
if (-not (Test-Path $pluginsDir)) {
    Write-Error "Plugins directory not found: $pluginsDir. Run 'mvn verify' first."
    exit 1
}

$rows = @()
Get-ChildItem -Path $pluginsDir -Filter "*.jar" | Sort-Object Name | ForEach-Object {
    $name = $_.BaseName
    if ($name -match '^(.+)_(\d+\.\d+.*)$') {
        $rows += [PSCustomObject]@{
            SymbolicName = $Matches[1]
            Version      = $Matches[2]
            File         = $_.Name
            Category     = if ($Matches[1] -like 'com.rubberjam.*') { 'assistai' }
                           elseif ($Matches[1] -like 'wrapped.*') { 'wrapped' }
                           elseif ($Matches[1] -like 'org.eclipse.*') { 'eclipse' }
                           else { 'other' }
        }
    } else {
        $rows += [PSCustomObject]@{
            SymbolicName = $name
            Version      = ""
            File         = $_.Name
            Category     = 'other'
        }
    }
}

$summary = @(
    "AssistAI p2 bundle inventory",
    "Repository: $RepositoryPath",
    "Generated: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')",
    "Total bundles: $($rows.Count)",
    "  assistai: $(@($rows | Where-Object { $_.Category -eq 'assistai' }).Count)",
    "  wrapped:  $(@($rows | Where-Object { $_.Category -eq 'wrapped' }).Count)",
    "  eclipse:  $(@($rows | Where-Object { $_.Category -eq 'eclipse' }).Count)",
    "  other:    $(@($rows | Where-Object { $_.Category -eq 'other' }).Count)",
    ""
)

if ($OutFile -ne "") {
    $dir = Split-Path -Parent $OutFile
    if ($dir -ne "" -and -not (Test-Path $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    $summary | Set-Content -Path $OutFile -Encoding utf8
    $rows | Format-Table -AutoSize | Out-String | Add-Content -Path $OutFile -Encoding utf8
    Write-Host "Wrote inventory to $OutFile"
} else {
    $summary | ForEach-Object { Write-Host $_ }
    $rows | Format-Table -AutoSize
}
