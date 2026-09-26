$ErrorActionPreference = 'Stop'
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $output = & .\gradlew.bat projects --offline --console=plain 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join "`n") }
    $unexpected = $output | Select-String "Project ':(whisper|opusmt|mlkit|chat|gdrive)'"
    if ($unexpected) { throw "Retired modules re-entered the Gradle graph: $unexpected" }
    Write-Output 'PASS: Whisper, Opus-MT, ML Kit, Chat, and GDrive are excluded from the Gradle project graph.'
} finally {
    Pop-Location
}
