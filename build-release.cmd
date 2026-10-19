@echo off
REM ============================================================
REM   Build the distributable package for the Mock Interview System
REM   This BUILD machine needs: Node.js + npm, Maven wrapper, JDK 17.
REM   Set JAVA_HOME to a JDK 17 path before running.
REM   The end user only needs the output folder (Java is bundled).
REM ============================================================
setlocal
cd /d "%~dp0"

set "REL=release\fake-mianshi"
set "APP_SRC=backend\target\fake-mianshi-backend-0.0.1-SNAPSHOT.jar"

echo.
echo ============================================================
echo   Building Mock Interview System release package
echo ============================================================
echo.

echo [1/5] Building the frontend ...
pushd frontend
call npm run build
if errorlevel 1 goto err_frontend
popd

echo.
echo [2/5] Copying the frontend build into the backend static resources ...
REM /MIR (not /E): the static folder is a build mirror of frontend\dist.
REM With /E the previous build's content-hashed chunks are never removed, so
REM every release kept shipping dead weight from older builds.
robocopy frontend\dist backend\src\main\resources\static /MIR /NFL /NDL /NJH /NJS >nul
set "RB=%errorlevel%"
if %RB% GEQ 8 goto err_sync

echo.
echo [3/5] Building the backend fat jar ...
REM "clean" is required, not cosmetic: src/main/resources/static is git-ignored
REM and only copied into target/classes on build. Without clean, assets deleted
REM from the static mirror stay in target/classes and get packed into the jar,
REM so releases shipped dead chunks from older frontend builds.
pushd backend
call mvnw.cmd clean package -DskipTests
if errorlevel 1 goto err_backend
popd

echo.
echo [4/5] Creating the bundled Java 17 runtime with jlink ...
set "JDK=%JAVA_HOME%"
if not defined JDK goto no_jdk
if not exist "%JDK%\bin\jlink.exe" goto no_jdk
if exist "%REL%\jre" rmdir /s /q "%REL%\jre"
"%JDK%\bin\jlink.exe" --add-modules java.base,java.compiler,java.datatransfer,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.transaction.xa,java.xml,java.xml.crypto,jdk.crypto.ec,jdk.unsupported,jdk.zipfs,jdk.net,jdk.management,jdk.naming.dns,jdk.crypto.cryptoki --strip-debug --no-man-pages --no-header-files --compress=2 --output "%REL%\jre"
if errorlevel 1 goto err_jlink

echo.
echo [5/5] Assembling the release folder ...
if not exist "%REL%" mkdir "%REL%"
copy /y "%APP_SRC%" "%REL%\app.jar" >nul
copy /y packaging\start.cmd "%REL%\start.cmd" >nul
copy /y packaging\README.txt "%REL%\README.txt" >nul

echo.
echo ============================================================
echo   OK. The distributable package is in:  %REL%
echo ============================================================
exit /b 0

:err_frontend
echo [FAIL] Frontend build failed. Is Node.js installed?
goto finish

:err_sync
echo [FAIL] Failed to copy the frontend build into the backend resources.
goto finish

:err_backend
echo [FAIL] Backend build failed.
goto finish

:no_jdk
echo [FAIL] JDK 17 not found. Please set JAVA_HOME to a JDK 17 path.
goto finish

:err_jlink
echo [FAIL] jlink failed. Please confirm JAVA_HOME points to a JDK 17.
goto finish

:finish
echo.
echo Build aborted. See the messages above.
exit /b 1
