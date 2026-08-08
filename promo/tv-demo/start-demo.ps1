param(
	[int]$Port = 8765
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Output = Join-Path $Root 'output'
$PidFile = Join-Path $Root '.server.pid'

if (-not (Test-Path -LiteralPath (Join-Path $Output 'fermatax-demo.m3u'))) {
	throw 'Demo output is missing. Run build-demo.ps1 first.'
}

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
	throw 'adb is not available.'
}

if (-not (Get-Command python -ErrorAction SilentlyContinue)) {
	throw 'python is not available.'
}

$device = adb get-state 2>$null
if ($LASTEXITCODE -ne 0 -or $device.Trim() -ne 'device') {
	throw 'No ready ADB device is connected.'
}

adb reverse "tcp:$Port" "tcp:$Port"
if ($LASTEXITCODE -ne 0) {
	throw 'Failed to configure adb reverse.'
}

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) {
	Write-Output "Port $Port is already being served."
} else {
	$process = Start-Process -FilePath 'python' -ArgumentList @('-m', 'http.server', "$Port", '--bind', '127.0.0.1', '--directory', $Output) -WindowStyle Hidden -PassThru
	Set-Content -LiteralPath $PidFile -Value $process.Id -Encoding ASCII
	Start-Sleep -Milliseconds 800
	if ($process.HasExited) {
		throw 'The demo HTTP server exited during startup.'
	}
	Write-Output "Started demo HTTP server with PID $($process.Id)."
}

Write-Output "Add this M3U source in FermataX TV: http://127.0.0.1:$Port/fermatax-demo.m3u"
