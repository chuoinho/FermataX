[CmdletBinding()]
param(
    [string]$DeviceSerial = "15c36230",
    [switch]$SkipBuild = $false,
    [switch]$SkipInstall = $false
)

$ErrorActionPreference = "Stop"

Write-Host "=== Car Display Server Smoke Test ===" -ForegroundColor Cyan
Write-Host "Target Device: $DeviceSerial"

# 1. Check ADB connectivity and device state
Write-Host "`n[1/5] Checking device connectivity..." -ForegroundColor Yellow
$devices = adb devices -l | Out-String
if ($devices -notmatch $DeviceSerial) {
    Write-Error "Device $DeviceSerial is not connected or unauthorized. adb devices output:`n$devices"
    exit 1
}

$rootId = adb -s $DeviceSerial shell "su -c 'id'" 2>&1
if ($rootId -notmatch "uid=0\(root\)") {
    Write-Error "Failed to obtain root shell on device $DeviceSerial. Output:`n$rootId"
    exit 1
}
Write-Host "Device is connected and root verified: $rootId" -ForegroundColor Green

# 2. Build debug APK if requested
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $repoRoot

$apkPath = Join-Path $repoRoot "fermata\build\outputs\apk\auto\debug\fermata-2.1-me.app.fermataX.auto-auto-debug.apk"

if (-not $SkipBuild) {
    Write-Host "`n[2/5] Building :fermata:assembleAutoDebug..." -ForegroundColor Yellow
    $env:ALLOW_LEGACY_TEST_SIGNING = "true"
    $env:APP_ID_SFX = ".test"
    
    $gradlew = Join-Path $repoRoot "gradlew.bat"
    $prevPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $buildOutput = & $gradlew :fermata:assembleAutoDebug 2>&1
    $buildExitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevPreference
    if ($buildExitCode -ne 0) {
        Write-Host ($buildOutput -join "`n")
        Write-Error "Build failed with exit code $buildExitCode"
        exit 1
    }
    Write-Host "Build succeeded." -ForegroundColor Green
} else {
    Write-Host "`n[2/5] Skipping build as requested." -ForegroundColor Yellow
}

if (-not (Test-Path $apkPath)) {
    Write-Error "APK file not found at: $apkPath"
    exit 1
}

# 3. Install APK to target device
$packageName = "me.app.fermataX.auto.debug"
if (-not $SkipInstall) {
    Write-Host "`n[3/5] Installing $apkPath to $DeviceSerial..." -ForegroundColor Yellow
    $prevPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $installOutput = adb -s $DeviceSerial install -r -d $apkPath 2>&1
    $installExitCode = $LASTEXITCODE
    $ErrorActionPreference = $prevPreference
    $installOutputText = ($installOutput | Out-String)
    if ($installExitCode -ne 0 -or $installOutputText -notmatch "Success") {
        Write-Host ($installOutput -join "`n")
        Write-Error "Failed to install APK to $DeviceSerial"
        exit 1
    }
    Write-Host "Installation succeeded." -ForegroundColor Green
} else {
    Write-Host "`n[3/5] Skipping install as requested." -ForegroundColor Yellow
}

# 4. Resolve installed APK path on device
Write-Host "`n[4/5] Querying APK path on device..." -ForegroundColor Yellow
$pmPathOutput = adb -s $DeviceSerial shell "pm path $packageName" 2>&1
$deviceApkPath = ($pmPathOutput -split "`n" | Where-Object { $_ -match "^package:" } | Select-Object -First 1) -replace "^package:\s*", ""
$deviceApkPath = $deviceApkPath.Trim()

if ([string]::IsNullOrWhiteSpace($deviceApkPath)) {
    Write-Error "Could not resolve installed APK path for $packageName on device. Output: $pmPathOutput"
    exit 1
}
Write-Host "Device APK Path: $deviceApkPath" -ForegroundColor Green

# 5. Run CarDisplayServer selftest
Write-Host "`n[5/5] Running CarDisplayServer selftest via su app_process..." -ForegroundColor Yellow
$mainClass = "me.app.fermatax.auto.carapps.server.Main"
$cmd = "su -c 'CLASSPATH=$deviceApkPath app_process / $mainClass --selftest'"
Write-Host "Command: $cmd"

$prevPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$testOutput = & adb -s $DeviceSerial shell $cmd 2>&1
$testExitCode = $LASTEXITCODE
$ErrorActionPreference = $prevPreference

Write-Host "`n--- Device Output ---" -ForegroundColor Gray
Write-Host ($testOutput -join "`n")
Write-Host "--- End Device Output (Exit Code: $testExitCode) ---`n" -ForegroundColor Gray

# Evaluate test output
$checks = [ordered]@{
    "Main class executed without crash"     = ($testExitCode -eq 0 -and $testOutput -notmatch "ClassNotFoundException" -and $testOutput -notmatch "AndroidRuntime")
    "Capabilities verified (0x1f / 31)"    = ($testOutput -match "CAPABILITIES=0x1f|capabilities=31|capabilities=0x1f")
    "VirtualDisplay created"               = ($testOutput -match "ATTACHED displayId=(\d+)")
    "Display flags match 0x7d4b (API 36)"  = ($testOutput -match "DISPLAY_FLAGS=0x7d4b")
    "Dumpsys display confirmed"            = ($testOutput -match "DUMPSYS_DISPLAY_CONFIRMED=true")
    "IME policy local set"                 = ($testOutput -match "IME_POLICY_OK=true")
    "Touch and key injection verified"     = ($testOutput -match "INJECT_TOUCH_OK=true" -and $testOutput -match "INJECT_KEY_OK=true")
    "Panel power toggle verified"          = ($testOutput -match "PANEL_POWER_OFF_OK=true" -and $testOutput -match "PANEL_POWER_ON_OK=true")
    "WakeLock held during panel off"       = ($testOutput -match "WAKELOCK_HELD=true")
    "Display released cleanly"             = ($testOutput -match "RELEASE_OK=true")
    "Overall selftest succeeded"           = ($testOutput -match "SELFTEST_SUCCESS")
}

Write-Host "=== SMOKE TEST RESULTS ===" -ForegroundColor Cyan
$allPassed = $true
foreach ($name in $checks.Keys) {
    $passed = $checks[$name]
    if ($passed) {
        Write-Host "  [PASS] $name" -ForegroundColor Green
    } else {
        Write-Host "  [FAIL] $name" -ForegroundColor Red
        $allPassed = $false
    }
}

if ($allPassed) {
    Write-Host "`nALL CHECKS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`nONE OR MORE CHECKS FAILED." -ForegroundColor Red
    exit 1
}
