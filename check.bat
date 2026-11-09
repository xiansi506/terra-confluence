@echo off
rem ============================================================
rem  Fast compile check for the refactoring loop.
rem  Only compiles Java (includes the Mixin annotation processor);
rem  it does NOT package a jar, so it finishes in seconds.
rem
rem  Use this after EVERY small change:
rem      check.bat
rem  A green "[OK] COMPILE PASSED" means your change still builds.
rem  For a real release jar use build.bat instead.
rem ============================================================
cd /d "%~dp0"

rem --- reuse the workspace gradle cache (all downloads already done) ---
set "GRADLE_USER_HOME=%~dp0..\.gradle-home"

echo Running: gradlew.bat compileJava --offline --no-daemon
echo.
call gradlew.bat compileJava --offline --no-daemon
set "RESULT=%ERRORLEVEL%"

echo.
if "%RESULT%"=="0" (
    echo [OK] COMPILE PASSED - safe to continue / commit.
) else (
    echo [FAILED] COMPILE ERROR - exit code %RESULT%
    echo Scroll up for the "error:" lines. Nothing is committed yet, so you can
    echo also undo everything with:  git checkout -- .
)
exit /b %RESULT%
