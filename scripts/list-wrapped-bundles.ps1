# Parses Tycho target-resolution log output for wrapped Maven bundle symbolic names.
# Usage: mvn -DskipTests package 2>&1 | Tee-Object build.log; .\scripts\list-wrapped-bundles.ps1 build.log

param(
    [string]$LogFile = "build.log"
)

if (-not (Test-Path $LogFile)) {
    Write-Error "Log file not found: $LogFile"
    exit 1
}

Select-String -Path $LogFile -Pattern "is wrapped as a bundle with bundle symbolic name (.+)$" |
    ForEach-Object {
        if ($_.Matches[0].Groups.Count -ge 2) {
            $_.Matches[0].Groups[1].Value
        }
    } |
    Sort-Object -Unique
