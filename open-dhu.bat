@echo off
setlocal EnableExtensions EnableDelayedExpansion

set "SDK=%LOCALAPPDATA%\Android\Sdk"
set "ADB=%SDK%\platform-tools\adb.exe"
set "DHU=%SDK%\extras\google\auto\desktop-head-unit.exe"
set "CONFIG_DIR=%SDK%\extras\google\auto\config"

if not exist "%ADB%" (
  echo [ERROR] adb.exe not found:
  echo %ADB%
  pause
  exit /b 1
)

if not exist "%DHU%" (
  echo [ERROR] desktop-head-unit.exe not found:
  echo %DHU%
  pause
  exit /b 1
)

echo.
echo FermataX DHU launcher
echo --------------------
echo 1. 800x480 - Default
echo 2. 800x480 - 6-inch margins
echo 3. 1280x720 - HD
echo 4. 1280x720 - Wide display
echo 5. 1920x1080 - Full HD
echo 6. 1280x720 - All DHU capabilities
echo.
choice /c 123456 /n /m "Select screen preset [1-6]: "
set "PRESET=%errorlevel%"

if "%PRESET%"=="1" set "CONFIG=%CONFIG_DIR%\default.ini"
if "%PRESET%"=="2" set "CONFIG=%CONFIG_DIR%\default_6in.ini"
if "%PRESET%"=="3" set "CONFIG=%CONFIG_DIR%\default_720p.ini"
if "%PRESET%"=="4" set "CONFIG=%CONFIG_DIR%\default_wide.ini"
if "%PRESET%"=="5" set "CONFIG=%CONFIG_DIR%\default_1080p.ini"
if "%PRESET%"=="6" set "CONFIG=%CONFIG_DIR%\all_720p.ini"

if not exist "%CONFIG%" (
  echo [ERROR] DHU config not found:
  echo %CONFIG%
  pause
  exit /b 1
)

set "SERIAL=%~1"
if defined SERIAL goto validate_device

set /a DEVICE_COUNT=0
for /f "skip=1 tokens=1,2" %%A in ('""%ADB%" devices"') do (
  if "%%B"=="device" (
    set /a DEVICE_COUNT+=1
    set "DEVICE_!DEVICE_COUNT!=%%A"
  )
)

if %DEVICE_COUNT%==0 goto no_device
if %DEVICE_COUNT%==1 (
  set "SERIAL=!DEVICE_1!"
  goto validate_device
)

echo.
echo Multiple ADB connections found ^(USB, wireless or mDNS^):
for /l %%N in (1,1,%DEVICE_COUNT%) do echo %%N. !DEVICE_%%N!
if %DEVICE_COUNT% GTR 9 (
  echo Run open-dhu.bat with the desired serial or IP:port as its argument.
  pause
  exit /b 1
)

:choose_device
choice /c 123456789 /n /m "Select device number: "
set "DEVICE_INDEX=%errorlevel%"
if %DEVICE_INDEX% GTR %DEVICE_COUNT% goto choose_device
for /l %%N in (1,1,%DEVICE_COUNT%) do (
  if "%%N"=="%DEVICE_INDEX%" set "SERIAL=!DEVICE_%%N!"
)

:validate_device
"%ADB%" -s "%SERIAL%" get-state 1>nul 2>nul
if errorlevel 1 (
  if not "%SERIAL::=%"=="%SERIAL%" (
    echo Connecting to %SERIAL%...
    "%ADB%" connect "%SERIAL%"
  )
  "%ADB%" -s "%SERIAL%" get-state 1>nul 2>nul
  if errorlevel 1 goto no_device
)
goto device_ready

:no_device
echo [ERROR] No authorized ADB device is connected, or the selected device is unavailable.
echo Enable USB debugging or pair the phone using Wireless debugging.
echo For wireless, you can run: open-dhu.bat IP:port
echo Start Android Auto Head Unit Server on the selected phone.
pause
exit /b 1

:device_ready
echo.
echo Using ADB device: %SERIAL%

echo.
echo Closing old DHU session...
taskkill /f /im desktop-head-unit.exe 1>nul 2>nul

echo Resetting ADB forward on port 5277...
"%ADB%" -s "%SERIAL%" forward --remove tcp:5277 1>nul 2>nul
"%ADB%" -s "%SERIAL%" forward tcp:5277 tcp:5277 1>nul
if errorlevel 1 (
  echo [ERROR] Failed to forward ADB port 5277.
  pause
  exit /b 1
)

echo Starting DHU with:
echo %CONFIG%
start "FermataX DHU" "%DHU%" -c "%CONFIG%" -i touch -a 5277

endlocal
exit /b 0
