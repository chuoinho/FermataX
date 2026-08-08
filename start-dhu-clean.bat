@echo off
setlocal EnableExtensions DisableDelayedExpansion

rem Usage: start-dhu-clean.bat [adb-device-serial]
rem Stops stale DHU/FermataX processes without clearing app data, then opens Google DHU.

set "SERIAL=%~1"
call :find_sdk
if errorlevel 1 exit /b 1

set "ADB=%SDK%\platform-tools\adb.exe"
set "DHU=%SDK%\extras\google\auto\desktop-head-unit.exe"

if not exist "%ADB%" (
  echo [ERROR] adb.exe was not found: "%ADB%"
  exit /b 1
)

if not exist "%DHU%" (
  echo [ERROR] Google Desktop Head Unit was not found: "%DHU%"
  echo Install it from Android Studio ^> SDK Tools ^> Android Auto Desktop Head Unit Emulator.
  exit /b 1
)

"%ADB%" start-server >nul
if not defined SERIAL call :find_single_device
if errorlevel 1 exit /b 1

echo Using ADB device: %SERIAL%
"%ADB%" -s "%SERIAL%" get-state | findstr /r /x "device" >nul
if errorlevel 1 (
  echo [ERROR] Device "%SERIAL%" is not ready. Check USB debugging authorization.
  exit /b 1
)

echo Stopping stale DHU and FermataX processes...
taskkill /IM desktop-head-unit.exe /T /F >nul 2>&1
"%ADB%" -s "%SERIAL%" shell am force-stop me.app.fermataX >nul 2>&1
"%ADB%" -s "%SERIAL%" shell am force-stop me.app.fermataX.auto >nul 2>&1

echo Resetting Android Auto transport forwarding...
"%ADB%" -s "%SERIAL%" forward --remove tcp:5277 >nul 2>&1
"%ADB%" -s "%SERIAL%" forward tcp:5277 tcp:5277
if errorlevel 1 (
  echo [ERROR] Could not forward tcp:5277. Another ADB session may still own it.
  exit /b 1
)

echo Starting Google DHU...
start "Google DHU" "%DHU%"
echo.
echo DHU started. If it stays on "Waiting for phone", enable "Start head unit server"
echo in Android Auto developer settings on the phone, then run this script again.
exit /b 0

:find_sdk
set "SDK=%ANDROID_SDK_ROOT%"
if defined SDK if exist "%SDK%\platform-tools\adb.exe" exit /b 0

set "SDK=%ANDROID_HOME%"
if defined SDK if exist "%SDK%\platform-tools\adb.exe" exit /b 0

set "SDK=%LOCALAPPDATA%\Android\Sdk"
if exist "%SDK%\platform-tools\adb.exe" exit /b 0

echo [ERROR] Android SDK was not found. Set ANDROID_SDK_ROOT or ANDROID_HOME.
exit /b 1

:find_single_device
set "FOUND="
for /f "skip=1 tokens=1,2" %%A in ('"%ADB%" devices') do (
  if "%%B"=="device" (
    if defined FOUND (
      echo [ERROR] More than one ADB device is connected. Run: %~nx0 ^<serial^>
      exit /b 1
    )
    set "FOUND=%%A"
  )
)

if not defined FOUND (
  echo [ERROR] No authorized ADB device found. Connect a phone and accept its USB-debugging prompt.
  exit /b 1
)

set "SERIAL=%FOUND%"
exit /b 0
