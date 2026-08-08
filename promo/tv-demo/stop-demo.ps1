param(
	[int]$Port = 8765
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$PidFile = Join-Path $Root '.server.pid'

if (Test-Path -LiteralPath $PidFile) {
	$serverPid = [int](Get-Content -LiteralPath $PidFile -Raw)
	$process = Get-CimInstance Win32_Process -Filter "ProcessId = $serverPid" -ErrorAction SilentlyContinue
	if ($process -and ($process.Name -match '^python(\.exe)?$') -and ($process.CommandLine -like '*http.server*')) {
		Stop-Process -Id $serverPid
		Write-Output "Stopped demo HTTP server PID $serverPid."
	}
	Remove-Item -LiteralPath $PidFile -Force
}

if (Get-Command adb -ErrorAction SilentlyContinue) {
	adb reverse --remove "tcp:$Port" 2>$null
}
