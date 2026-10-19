@echo off
REM ============================================================
REM   Mock Interview System - one-click launcher
REM   This folder bundles its own Java 17 runtime (jre) so no
REM   Java install is required on the target machine.
REM   Just double-click this file: it starts the backend and
REM   auto-opens the web page. Close this window to stop.
REM ============================================================

title Mock Interview System - Launcher

set "PORT=8080"
cd /d "%~dp0"

set "JAVA=%~dp0jre\bin\java.exe"
set "APP=%~dp0app.jar"
set "URL=http://localhost:%PORT%/"
set "HEALTH=http://localhost:%PORT%/api/session/stats"

if not exist "%JAVA%" goto missing_java
if not exist "%APP%" goto missing_app

echo.
echo ============================================
echo       Mock Interview System - Launcher
echo ============================================
echo.

REM Check whether the service is already running to avoid a double start.
powershell -NoProfile -ExecutionPolicy Bypass -Command "try{ $r=Invoke-WebRequest -Uri '%HEALTH%' -UseBasicParsing -TimeoutSec 2; if($r.StatusCode -eq 200){'RUNNING'}else{'WAIT'} }catch{'WAIT'}" > "%TEMP%\fms_ping.txt" 2>nul
set /p PING=<"%TEMP%\fms_ping.txt"
del "%TEMP%\fms_ping.txt" >nul 2>&1

if "%PING%"=="RUNNING" goto already_running

echo Starting the backend service...
echo This window is the service window. Closing it will stop the service.
echo.

start "" /B "%JAVA%" -jar "%APP%"

echo Waiting for the service to be ready. The browser will open automatically.
echo The first start may take a few seconds to initialize the database.
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='%URL%'; $ok=$false; for($i=0;$i -lt 120;$i++){ try{ $r=Invoke-WebRequest -Uri $u -UseBasicParsing -TimeoutSec 2; if($r.StatusCode -eq 200){ $ok=$true; break } }catch{}; Start-Sleep -Milliseconds 500 }; if($ok){ Start-Process $u } else { Write-Output 'TIMEOUT' }"

echo.
echo ------------------------------------------------------------
echo   Service started: %URL%
echo   If the browser did not open, please visit: %URL%
echo   To stop the service, close this window.
echo ------------------------------------------------------------
echo.
goto end

:already_running
echo The service is already running. Opening the page for you...
start "" "%URL%"
goto end

:missing_java
echo.
echo [ERROR] Bundled Java runtime not found: jre\bin\java.exe
echo Please keep the whole folder intact, including the jre folder and app.jar.
echo.
goto end

:missing_app
echo.
echo [ERROR] app.jar not found. Please keep the whole folder intact.
echo.
goto end

:end
pause
