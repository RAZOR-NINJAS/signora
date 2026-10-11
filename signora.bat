@echo off
setlocal
set "SCRIPT_DIR=%~dp0"

if exist "%SCRIPT_DIR%\.venv\Scripts\signora.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\signora.exe" %*
) else if exist "%SCRIPT_DIR%\.venv\Scripts\python.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\python.exe" "%SCRIPT_DIR%\src\signora\cli.py" %*
) else (
    echo [ERROR] Signora virtual environment not found in %SCRIPT_DIR%\.venv.
    echo Please run powershell -ExecutionPolicy Bypass -File .\install.ps1 first.
    pause
    exit /b 1
)
