param(
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

$arguments = @(
    "-pl",
    "plugins/com.rubberjam.eclipse.plugin.assistai.main",
    "-DskipTests",
    "verify"
)

if (-not $AllowLocalArtifacts) {
    $arguments = @("-Dtycho.localArtifacts=ignore") + $arguments
}

& $maven @arguments
exit $LASTEXITCODE
