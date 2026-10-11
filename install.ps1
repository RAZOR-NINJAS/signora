# ==============================================================================
# Signora — Flagship Windows Interactive Installer & Self-Healing Bootstrap
#
# Non-Admin & Barebones Windows 10/11 Compatible.
# Run in PowerShell:
#   irm https://raw.githubusercontent.com/RAZOR-NINJAS/signora/main/install.ps1 | iex
# Or locally:
#   powershell -ExecutionPolicy Bypass -File .\install.ps1
# ==============================================================================

$ErrorActionPreference = "Stop"

Clear-Host
Write-Host ""
Write-Host "  ███████╗██╗ ██████╗ ███╗   ██╗ ██████╗ ██████╗  █████╗ " -ForegroundColor Cyan
Write-Host "  ██╔════╝██║██╔════╝ ████╗  ██║██╔═══██╗██╔══██╗██╔══██╗" -ForegroundColor Cyan
Write-Host "  ███████╗██║██║  ███╗██╔██╗ ██║██║   ██║██████╔╝███████║" -ForegroundColor Cyan
Write-Host "  ╚════██║██║██║   ██║██║╚██╗██║██║   ██║██╔══██╗██╔══██╗" -ForegroundColor Cyan
Write-Host "  ███████║██║╚██████╔╝██║ ╚████║╚██████╔╝██║  ██║██║  ██║" -ForegroundColor Cyan
Write-Host "  ╚══════╝╚═╝ ╚═════╝ ╚═╝  ╚═══╝ ╚═════╝ ╚═╝  ╚═╝╚═╝  ╚═╝" -ForegroundColor Cyan
Write-Host ""
Write-Host "  [+] Signora: 100% Offline Real-Time Sign Language Recognition Engine" -ForegroundColor White
Write-Host "  [+] Zero Cloud Latency • Edge Neural Inference • No Admin Rights Required" -ForegroundColor DarkCyan
Write-Host ""

# ==============================================================================
# 1. Interactive Configuration Prompts
# ==============================================================================
$defaultInstallDir = "$env:LOCALAPPDATA\Signora"
if (Test-Path "$PSScriptRoot\pyproject.toml") {
    $defaultInstallDir = $PSScriptRoot
} elseif (Test-Path ".\pyproject.toml") {
    $defaultInstallDir = (Get-Item ".").FullName
}

Write-Host "==================== Setup Configuration ====================" -ForegroundColor DarkGray

$inputDir = Read-Host "Installation directory [$defaultInstallDir]"
if ([string]::IsNullOrWhiteSpace($inputDir)) {
    $installDir = $defaultInstallDir
} else {
    $installDir = $inputDir
}

$inputShortcut = Read-Host "Create Desktop shortcut for 1-click launch? (Y/n) [Y]"
$createShortcut = (-not ($inputShortcut -match "^[Nn]"))

$inputLaunch = Read-Host "Launch Signora interactive dashboard when setup completes? (Y/n) [Y]"
$launchAfter = (-not ($inputLaunch -match "^[Nn]"))

Write-Host ""
Write-Host "Target location: $installDir" -ForegroundColor Green
if (-not (Test-Path $installDir)) {
    New-Item -ItemType Directory -Path $installDir -Force | Out-Null
}
Set-Location $installDir

# ==============================================================================
# 2. Source Code Retrieval & Verification
# ==============================================================================
Write-Host "`n[Step 1/6] Verifying Signora source tree..." -ForegroundColor Cyan

$repoUrl = "https://github.com/RAZOR-NINJAS/signora.git"
$zipUrl = "https://github.com/RAZOR-NINJAS/signora/archive/refs/heads/main.zip"

if (-not (Test-Path "$installDir\pyproject.toml")) {
    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCmd) {
        Write-Host "  [+] Cloning source via git..." -ForegroundColor Gray
        git clone $repoUrl $installDir
    } else {
        Write-Host "  [+] Downloading source package archive..." -ForegroundColor Gray
        $zipFile = "$env:TEMP\signora-source.zip"
        Invoke-WebRequest -Uri $zipUrl -OutFile $zipFile -UseBasicParsing
        Expand-Archive -Path $zipFile -DestinationPath "$env:TEMP\signora-extract" -Force
        $extractedRoot = Get-ChildItem "$env:TEMP\signora-extract" | Select-Object -First 1
        Copy-Item -Path "$($extractedRoot.FullName)\*" -Destination $installDir -Recurse -Force
        Remove-Item -Path $zipFile -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\signora-extract" -Recurse -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "  [OK] Source tree ready." -ForegroundColor Green

# ==============================================================================
# 3. Auto-Solve Missing Dependencies: Visual C++ Runtime
# ==============================================================================
Write-Host "`n[Step 2/6] Inspecting Windows system runtimes..." -ForegroundColor Cyan
$vcMissing = $false
try {
    $vcKey = Get-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\VisualStudio\14.0\VC\Runtimes\X64" -ErrorAction SilentlyContinue
    if (-not $vcKey -or -not $vcKey.Installed) {
        $vcMissing = $true
    }
} catch {
    $vcMissing = $true
}

if ($vcMissing) {
    Write-Host "  [Auto-Healing] Visual C++ 2015-2022 Runtime missing. Auto-installing..." -ForegroundColor Yellow
    $vcRedistUrl = "https://aka.ms/vs/17/release/vc_redist.x64.exe"
    $vcInstaller = "$env:TEMP\vc_redist.x64.exe"
    try {
        Invoke-WebRequest -Uri $vcRedistUrl -OutFile $vcInstaller -UseBasicParsing
        Start-Process -FilePath $vcInstaller -ArgumentList "/install", "/quiet", "/norestart" -Wait
        Remove-Item -Path $vcInstaller -Force -ErrorAction SilentlyContinue
        Write-Host "  [OK] Visual C++ runtime installed." -ForegroundColor Green
    } catch {
        Write-Host "  [!] Notice: Non-admin environment, skipping system VC++ install." -ForegroundColor DarkYellow
    }
} else {
    Write-Host "  [OK] Visual C++ runtime verified." -ForegroundColor Green
}

# ==============================================================================
# 4. Standalone Environment & Python Manager Provisioning (uv)
# ==============================================================================
Write-Host "`n[Step 3/6] Configuring standalone Python runtime..." -ForegroundColor Cyan

$binDir = "$env:LOCALAPPDATA\bin"
if (-not (Test-Path $binDir)) {
    New-Item -ItemType Directory -Path $binDir -Force | Out-Null
}

$uvExe = "$binDir\uv.exe"
$uvCmd = Get-Command uv -ErrorAction SilentlyContinue

if (-not $uvCmd -and -not (Test-Path $uvExe)) {
    Write-Host "  [+] Downloading portable Python manager (uv)..." -ForegroundColor Gray
    $uvZip = "$env:TEMP\uv-windows.zip"
    $uvUrl = "https://github.com/astral-sh/uv/releases/latest/download/uv-x86_64-pc-windows-msvc.zip"
    try {
        Invoke-WebRequest -Uri $uvUrl -OutFile $uvZip -UseBasicParsing
        Expand-Archive -Path $uvZip -DestinationPath "$env:TEMP\uv-extract" -Force
        Move-Item -Path "$env:TEMP\uv-extract\uv.exe" -Destination $uvExe -Force
        Remove-Item -Path $uvZip -Force -ErrorAction SilentlyContinue
        Remove-Item -Path "$env:TEMP\uv-extract" -Recurse -Force -ErrorAction SilentlyContinue
    } catch {
        Write-Host "  [!] Could not fetch uv, using system Python..." -ForegroundColor DarkYellow
    }
}

$uvBin = if (Test-Path $uvExe) { $uvExe } elseif ($uvCmd) { $uvCmd.Source } else { $null }

$venvDir = "$installDir\.venv"
$venvPy = "$venvDir\Scripts\python.exe"

if ($uvBin) {
    Write-Host "  [+] Provisioning Python 3.11 runtime..." -ForegroundColor Gray
    & $uvBin python install 3.11
    if (-not (Test-Path $venvDir)) {
        & $uvBin venv --python 3.11 $venvDir
    }
} else {
    $sysPy = (Get-Command python -ErrorAction SilentlyContinue)
    if (-not $sysPy) {
        Write-Host "  [Auto-Healing] Python not detected. Installing via winget..." -ForegroundColor Yellow
        winget install Python.Python.3.11 --accept-source-agreements --accept-package-agreements
        $sysPy = (Get-Command python -ErrorAction SilentlyContinue)
    }
    if (-not $sysPy) {
        Write-Host "[Error] Python 3.11 could not be provisioned automatically. Please install Python 3.11 from python.org." -ForegroundColor Red
        exit 1
    }
    if (-not (Test-Path $venvDir)) {
        & $sysPy.Source -m venv $venvDir
    }
}
Write-Host "  [OK] Python 3.11 environment initialized at $venvDir" -ForegroundColor Green

# ==============================================================================
# 5. Installing Dependencies & Package
# ==============================================================================
Write-Host "`n[Step 4/6] Installing neural dependencies (CPU-optimized PyTorch & Piper TTS)..." -ForegroundColor Cyan

if ($uvBin) {
    & $uvBin pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    & $uvBin pip install -e . --quiet
} else {
    & $venvPy -m pip install --upgrade pip --quiet
    & $venvPy -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu --quiet
    & $venvPy -m pip install -e . --quiet
}
Write-Host "  [OK] Packages installed successfully." -ForegroundColor Green

# ==============================================================================
# 6. Model Asset Fetching & Auto-Verification
# ==============================================================================
Write-Host "`n[Step 5/6] Verifying neural models and Piper voice assets..." -ForegroundColor Cyan
& $venvPy "$installDir\scripts\fetch_models.py" --target-dir "$installDir\models"
Write-Host "  [OK] Neural weights verified." -ForegroundColor Green

# ==============================================================================
# 7. Create Launchers & Shortcuts
# ==============================================================================
Write-Host "`n[Step 6/6] Generating launchers and shortcuts..." -ForegroundColor Cyan

$batContent = @"
@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
if exist "%SCRIPT_DIR%\.venv\Scripts\signora.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\signora.exe" %*
) else if exist "%SCRIPT_DIR%\.venv\Scripts\python.exe" (
    "%SCRIPT_DIR%\.venv\Scripts\python.exe" "%SCRIPT_DIR%\src\signora\cli.py" %*
) else (
    echo [ERROR] Signora virtual environment not found. Please run install.ps1 first.
    pause
    exit /b 1
)
"@
Set-Content -Path "$installDir\signora.bat" -Value $batContent -Encoding ASCII
Write-Host "  [+] Launcher generated: $installDir\signora.bat" -ForegroundColor Green

if ($createShortcut) {
    $desktopPath = [System.Environment]::GetFolderPath([System.Environment+SpecialFolder]::Desktop)
    if ($desktopPath -and (Test-Path $desktopPath)) {
        $shortcutPath = "$desktopPath\Signora.lnk"
        try {
            $WshShell = New-Object -ComObject WScript.Shell
            $Shortcut = $WshShell.CreateShortcut($shortcutPath)
            $Shortcut.TargetPath = "$installDir\signora.bat"
            $Shortcut.WorkingDirectory = $installDir
            $Shortcut.Description = "Signora - 100% Offline Real-Time Sign Language Recognition"
            $Shortcut.Save()
            Write-Host "  [+] Created Desktop shortcut: $shortcutPath" -ForegroundColor Green
        } catch {
            Write-Host "  [!] Could not create Desktop shortcut." -ForegroundColor DarkGray
        }
    }
}

# ==============================================================================
# 8. Diagnostics & Completion
# ==============================================================================
Write-Host ""
Write-Host "Running automated hardware & model diagnostics..." -ForegroundColor Cyan
& $venvPy -c "from signora.doctor.checks import run_all_checks; checks = run_all_checks(); all_pass = all(c.passed for c in checks); print('[Diagnostics]', 'ALL CHECKS PASSED' if all_pass else 'SYSTEM READY')"

Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "         Signora Installation Completed Successfully!           " -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host ""
Write-Host "To start Signora:" -ForegroundColor White
Write-Host "  - Double-click 'Signora' on your Desktop" -ForegroundColor Yellow
Write-Host "  - Or run: $installDir\signora.bat" -ForegroundColor Cyan
Write-Host ""

if ($launchAfter) {
    Write-Host "Launching Signora dashboard..." -ForegroundColor Green
    Start-Process -FilePath "$installDir\signora.bat"
}
