# Lists bundle symbolic names from a Tycho p2 repository (plugins/*.jar).
# Usage: .\scripts\inventory-p2-repository.ps1
#        .\scripts\inventory-p2-repository.ps1 -RepositoryPath releng\...\target\repository

param(
    [string]$RepositoryPath = "releng\com.rubberjam.eclipse.assistai.repository/target/repository"
)

$pluginsDir = Join-Path $RepositoryPath "plugins"
if (-not (Test-Path $pluginsDir)) {
    Write-Error "Plugins directory not found: $pluginsDir. Run 'mvn verify' first."
    exit 1
}

$rows = @()
Get-ChildItem -Path $pluginsDir -Filter "*.jar" | Sort-Object Name | ForEach-Object {
    $name = $_.BaseName
    # Tycho jar names: symbolicName_version.jar (version may contain dots)
    if ($name -match '^(.+)_(\d+\.\d+.*)$') {
        $rows += [PSCustomObject]@{
            SymbolicName = $Matches[1]
            Version      = $Matches[2]
            File         = $_.Name
        }
    } else {
        $rows += [PSCustomObject]@{
            SymbolicName = $name
            Version      = ""
            File         = $_.Name
        }
    }
}

$rows | Format-Table -AutoSize
Write-Host ""
Write-Host "Total bundles: $($rows.Count)"
$wrapped = $rows | Where-Object { $_.SymbolicName -like 'wrapped.*' -or $_.SymbolicName -like 'org.*' -and $_.SymbolicName -notlike 'org.eclipse.*' }
Write-Host "Wrapped / third-party (heuristic): $($wrapped.Count)"
