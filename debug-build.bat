@echo off
cd /d "%~dp0"
echo ============================================
echo   Terra Confluence - Diagnostic Build
echo ============================================
echo.

rem --- Reuse the workspace gradle cache ---
set "GRADLE_USER_HOME=%~dp0..\.gradle-home"

echo Running: gradlew.bat build --no-daemon --info
echo Log will be written to build_log.txt
echo.
echo Please WAIT about 5 minutes (let it stall), then close this window.
echo The log file is saved automatically - no need to copy anything.
echo.
call gradlew.bat build --no-daemon --info > build_log.txt 2>&1
echo.
echo Done. Exit code %ERRORLEVEL%
echo Log saved to: %~dp0build_log.txt
pause
