param(
    [switch]$SkipTests,
    [switch]$AllowLocalArtifacts
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$mvnw = Join-Path $repoRoot "mvnw.cmd"

if (Test-Path $mvnw) {
    $maven = $mvnw
} else {
    $maven = "mvn"
}

$arguments = @("clean", "verify")

if ($SkipTests) {
    $arguments = @("-DskipTests") + $arguments
}

if (-not $AllowLocalArtifacts) {
    $arguments = @("-Dtycho.localArtifacts=ignore") + $arguments
}

& $maven @arguments
exit $LASTEXITCODE
