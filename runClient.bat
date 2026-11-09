@echo off
cd /d "%~dp0"
echo ============================================
echo   Terra Confluence - Run Client
echo ============================================
echo.

echo Running: gradlew.bat runClient --no-daemon
echo (The game window will open. Close it when done testing.)
echo.
call gradlew.bat runClient --no-daemon -Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
    echo [OK] Client exited normally.
) else (
    echo [FAILED] Client exited with code %RESULT%.
    echo Please copy the log above and send it to the author.
)
echo.
pause
