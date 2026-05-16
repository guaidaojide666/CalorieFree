@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File ".\scripts\push_to_github.ps1" %*
if errorlevel 1 (
    echo.
    echo Upload failed. Please check the error message above.
    pause
    exit /b 1
)
echo.
echo Upload completed.
pause
