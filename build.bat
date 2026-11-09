@echo off
cd /d "%~dp0"
echo ============================================
echo   Terra Confluence - Build Script
echo ============================================
echo.

rem --- Reuse the workspace gradle cache if present (all downloads done) ---
set "GRADLE_USER_HOME=%USERPROFILE%\.gradle"
if exist "%~dp0..\.gradle-home\wrapper\dists" set "GRADLE_USER_HOME=%~dp0..\.gradle-home"

echo Using gradle cache: %GRADLE_USER_HOME%
echo.
echo Running: gradlew.bat build --no-daemon
echo (Do NOT close this window while it runs. The slow-looking 14% step
echo  "Resolve dependencies of :compileClasspath" is ForgeGradle merging the
echo  client+server jars locally - normal, takes 5-15 minutes on first run.)
echo.
call gradlew.bat build --no-daemon -Dorg.gradle.internal.http.connectionTimeout=120000 -Dorg.gradle.internal.http.socketTimeout=120000
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
    echo [OK] Build successful. Jar is in build\libs\ .
) else (
    echo [FAILED] Build failed with exit code %RESULT%.
    echo Please copy the log above and send it to the author.
)
echo.
pause
