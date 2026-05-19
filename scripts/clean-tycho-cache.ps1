param(
    [switch]$IncludeMavenTransferCache
)

$ErrorActionPreference = "Stop"

$tychoCache = Join-Path $HOME ".m2\repository\.cache\tycho"

if (Test-Path $tychoCache) {
    Remove-Item -Recurse -Force $tychoCache
    Write-Host "Removed Tycho p2 cache: $tychoCache"
} else {
    Write-Host "Tycho p2 cache not found: $tychoCache"
}

if ($IncludeMavenTransferCache) {
    $lastUpdatedFiles = Get-ChildItem -Path (Join-Path $HOME ".m2\repository") -Filter "*.lastUpdated" -Recurse -ErrorAction SilentlyContinue
    foreach ($file in $lastUpdatedFiles) {
        Remove-Item -Force $file.FullName
    }

    Write-Host "Removed Maven .lastUpdated transfer markers."
}
