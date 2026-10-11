# ==============================================================================
# Signora — Zero-Prerequisite Windows Installer
#
# Designed for barebones Windows 10/11 laptops without Python, Git, or Admin rights.
# Run from PowerShell:
#   irm https://raw.githubusercontent.com/RAZOR-NINJAS/signora/main/install.ps1 | iex
# Or locally:
#   powershell -ExecutionPolicy Bypass -File .\install.ps1
# ==============================================================================

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "  ███████╗██╗ ██████╗ ███╗   ██╗ ██████╗ ██████╗  █████╗ " -ForegroundColor Cyan
Write-Host "  ██╔════╝██║██╔════╝ ████╗  ██║██╔═══██╗██╔══██╗██╔══██╗" -ForegroundColor Cyan
Write-Host "  ███████╗██║██║  ███╗██╔██╗ ██║██║   ██║██████╔╝███████║" -ForegroundColor Cyan
Write-Host "  ╚════██║██║██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══██║" -ForegroundColor Cyan
Write-Host "  ███████║██║╚██████╔╝██║ ╚████║╚██████╔╝██║  ██║██║  ██║" -ForegroundColor Cyan
Write-Host "  ╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  Real-time sign language recognition. 100% local. Nothing leaves your machine." -ForegroundColor White
Write-Host ""

# 1. Determine Target Installation Directory
$repoUrl = "https://github.com/RAZOR-NINJAS/signora.git"
$zipUrl = "https://github.com/RAZOR-NINJAS/signora/archive/refs/heads/main.zip"

if (Test-Path "$PSScriptRoot\pyproject.toml") {
    $installDir = $PSScriptRoot
} elseif (Test-Path ".\pyproject.toml") {
    $installDir = (Get-Item ".").FullName
} else {
    $installDir = "$env:LOCALAPPDATA\Signora"
    if (-not (Test-Path $installDir)) {
        New-Item -ItemType Directory -Path $installDir -Force | Out-Null
    }
}

Write-Host "[1/6] Target directory: $installDir" -ForegroundColor Green

# 2. Fetch or Verify Source Code
if (-not (Test-Path "$installDir\pyproject.toml")) {
    Write-Host "[2/6] Downloading Signora source tree..." -ForegroundColor Cyan
    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCmd) {
        Write-Host "      Cloning repository via git..." -ForegroundColor Gray
        git clone $repoUrl $installDir
    } else {
        Write-Host "      Downloading source zip via curl/Invoke-WebRequest..." -ForegroundColor Gray
        $zipFile = "$env:TEMP\signora-main.zip"
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
        Expand-Archive -Path $zipFile -DestinationPath "$env:TEMP\signora-extract" -Force
        $extractedRoot = Get-ChildItem "$env:TEMP\signora-extract" | Select-Object -First 1
        Copy-Item -Path "$($extractedRoot.FullName)\*" -Destination $installDir -Recurse -Force
        Remove-Item -Path $zipFile -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\signora-extract" -Recurse -Force -ErrorAction SilentlyContinue
    }
} else {
    Write-Host "[2/6] Source tree verified: pyproject.toml present" -ForegroundColor Green
}

Set-Location $installDir

# 3. Setup Lightweight 'uv' Package Manager (No Admin Needed)
Write-Host "[3/6] Configuring standalone Python manager (uv)..." -ForegroundColor Cyan
$binDir = "$env:LOCALAPPDATA\bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
}

$uvExe = "$binDir\uv.exe"
$uvCmd = Get-Command uv -ErrorAction SilentlyContinue

if (-not $uvCmd -and -not (Test-Path $uvExe)) {
    Write-Host "      Downloading standalone uv binary (portable, non-admin)..." -ForegroundColor Gray
    $uvZip = "$env:TEMP\uv-windows.zip"
    $uvUrl = "https://github.com/astral-sh/uv/releases/latest/download/uv-x86_64-pc-windows-msvc.zip"
    try {
        Invoke-WebRequest -Uri $uvUrl -OutFile $uvZip -UseBasicParsing
        Expand-Archive -Path $uvZip -DestinationPath "$env:TEMP\uv-extract" -Force
        Move-Item -Path "$env:TEMP\uv-extract\uv.exe" -Destination $uvExe -Force
        Remove-Item -Path $uvZip -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\uv-extract" -Recurse -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "      uv download failed, will fallback to system Python if available." -ForegroundColor Yellow
    }
}

if (Test-Path $uvExe) {
    $uvBin = $uvExe
} elseif ($uvCmd) {
    $uvBin = $uvCmd.Source
} else {
    $uvBin = $null
}

# 4. Provision Python 3.11 Runtime
$venvDir = "$installDir\.venv"
$venvPy = "$venvDir\Scripts\python.exe"

if ($uvBin) {
    Write-Host "      Provisioning Python 3.11 via uv..." -ForegroundColor Gray
    & $uvBin python install 3.11
    if (-not (Test-Path $venvDir)) {
        Write-Host "[4/6] Creating virtual environment (.venv)..." -ForegroundColor Cyan
        & $uvBin venv --python 3.11 $venvDir
    } else {
        Write-Host "[4/6] Existing virtual environment found." -ForegroundColor Green
    }
    
    # 5. Install Dependencies (CPU-Only PyTorch)
    Write-Host "[5/6] Installing dependencies (CPU-optimized PyTorch)..." -ForegroundColor Cyan
    & $uvBin pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    & $uvBin pip install -e . --quiet
} else {
    # Fallback when uv is unavailable
    Write-Host "      Searching for installed Python 3.11..." -ForegroundColor Gray
    $sysPy = (Get-Command python -ErrorAction SilentlyContinue)
    if (-not $sysPy) {
        Write-Host "[Error] Python 3.11 and uv were not found. Please install Python 3.11 from python.org." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $venvDir)) {
        Write-Host "[4/6] Creating virtual environment (.venv)..." -ForegroundColor Cyan
        & $sysPy.Source -m venv $venvDir
    }
    Write-Host "[5/6] Installing dependencies (CPU-optimized PyTorch)..." -ForegroundColor Cyan
    & $venvPy -m pip install --upgrade pip --quiet
    & $venvPy -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    & $venvPy -m pip install -e . --quiet
}

# 6. Fetch Model Assets
Write-Host "[6/6] Verifying neural models and voice assets..." -ForegroundColor Cyan
& $venvPy scripts\fetch_models.py --target-dir "$installDir\models"

# 7. Create signora.bat Launcher
$batContent = @"
@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%\.venv\Scripts\signora.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\signora.exe" %*
) else if exist "%SCRIPT_DIR%\.venv\Scripts\python.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\python.exe" src\signora\cli.py %*
) else (
    echo [ERROR] Signora virtual environment not found. Please run install.ps1 first.
    pause
    exit /b 1
)
"@
Set-Content -Path "$installDir\signora.bat" -Value $batContent -Encoding ASCII
Write-Host "      Created launcher: $installDir\signora.bat" -ForegroundColor Green

# 8. Create Desktop Shortcut if Desktop exists
$desktopPath = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
if ($desktopPath -and (Test-Path $desktopPath)) {
    $shortcutPath = "$desktopPath\Signora.lnk"
    try {
        $WshShell = New-Object -ComObject WScript.Shell
        $Shortcut = $WshShell.CreateShortcut($shortcutPath)
        $Shortcut.TargetPath = "$installDir\signora.bat"
        $Shortcut.WorkingDirectory = $installDir
        $Shortcut.Description = "Signora - Real-Time Offline Sign Language Recognition"
        $Shortcut.Save()
        Write-Host "      Created Desktop shortcut: $shortcutPath" -ForegroundColor Green
    } catch {}
}

# 9. Run Diagnostics
Write-Host "`nRunning system diagnostic check..." -ForegroundColor Cyan
& $venvPy src\signora\cli.py doctor

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "  Signora installation completed successfully!                  " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "To launch the Signora interactive dashboard:" -ForegroundColor White
Write-Host "  Double-click 'Signora' on your Desktop, or run:" -ForegroundColor Gray
Write-Host "  $installDir\signora.bat" -ForegroundColor Cyan
Write-Host ""
Write-Host "Quick commands (run in PowerShell or Command Prompt):" -ForegroundColor White
Write-Host "  .\signora.bat live      ASL Fingerspelling" -ForegroundColor Cyan
Write-Host "  .\signora.bat isl       Indian Sign Language (ISL)" -ForegroundColor Cyan
Write-Host "  .\signora.bat words     ASL Dynamic Words" -ForegroundColor Cyan
Write-Host "  .\signora.bat game      Shadow-Play Dino Runner" -ForegroundColor Cyan
Write-Host "  .\signora.bat learn     Browser Learning Kiosk" -ForegroundColor Cyan
Write-Host "  .\signora.bat doctor    System Diagnostics" -ForegroundColor Cyan
Write-Host ""
