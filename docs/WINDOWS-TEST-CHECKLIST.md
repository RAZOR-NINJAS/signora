# Signora — Windows Verification & QA Checklist

This document provides a rigorous test matrix for validating Signora on a barebones Windows 10 or Windows 11 laptop without pre-existing developer tooling (no Python, no Git, no Visual Studio build tools, and strictly standard non-administrator user permissions).

---

## 1. Test Environment Specification

| Property | Requirement / Expected Target |
| :--- | :--- |
| **Operating System** | Windows 10 (Build 19041+) or Windows 11 (64-bit) |
| **User Privileges** | Standard User (**No Administrator / No UAC Elevation**) |
| **Prerequisites** | None (Stock Windows installation with PowerShell 5.1+) |
| **Network** | Internet connection needed only for initial install (~250 MB total) |
| **Hardware** | Dual-core CPU, ≥4 GB RAM, integrated webcam or USB camera |

---

## 2. Installation Verification

### Step 2.1: Non-Admin PowerShell Execution
1. Open standard PowerShell by pressing `Win + X` and clicking **Windows PowerShell** (do **not** select "Run as Administrator").
2. Execute the bootstrap command:
   ```powershell
   irm https://raw.githubusercontent.com/RAZOR-NINJAS/signora/main/install.ps1 | iex
   ```
3. **Verify:**
   - [ ] No UAC (User Account Control) credential prompt appears.
   - [ ] Standalone `uv.exe` is downloaded into `$env:LOCALAPPDATA\bin\uv.exe`.
   - [ ] Standalone CPython 3.11 is provisioned without triggering an MSI/EXE installer wizard.
   - [ ] Virtual environment is created at `%LOCALAPPDATA%\Signora\.venv`.
   - [ ] PyTorch is installed using the lightweight CPU index (`https://download.pytorch.org/whl/cpu`, ~180 MB) rather than the bloated 2.5 GB NVIDIA CUDA wheel.
   - [ ] All 6 neural models download and verify with SHA-256 green checkmarks.
   - [ ] A double-clickable shortcut named `Signora` appears on your Windows Desktop.

---

## 3. Post-Installation Diagnostic Check

Open Command Prompt or PowerShell in the installation directory:
```cmd
cd %LOCALAPPDATA%\Signora
.\signora.bat doctor
```
**Verify:**
- [ ] Python Runtime reports `3.11.x` with `PASS`.
- [ ] Operating System reports `Windows (win32, x86_64) - DirectShow backend` with `PASS`.
- [ ] Webcam Capture reports `Found 1 camera(s) via DirectShow` with `PASS`.
- [ ] MediaPipe Landmarker reports `PASS`.
- [ ] ASL Letters, ISL Mode, and ASL Words report `PASS`.
- [ ] Text-to-Speech (Piper) reports `PASS` and identifies DirectSound/WASAPI audio output devices.
- [ ] Browser Kiosk detects `msedge.exe` or `chrome.exe` with `PASS`.
- [ ] Localhost Loopback binds port with `PASS`.
- [ ] Overall status: `All core systems are operational and ready for use!` (Exit code 0).

---

## 4. Feature Verification Matrix

### 4.1 Interactive Terminal Dashboard (TUI)
- [ ] Double-click the `Signora` desktop shortcut.
- [ ] Verify the Rich terminal opens with ASCII banner, system info table, and mode selection menu.
- [ ] Verify pressing `8` (Probe Webcams) lists camera index 0 with resolution and `Ready` status.
- [ ] Verify pressing `0` exits cleanly without error.

### 4.2 ASL Fingerspelling Mode
- [ ] Run `.\signora.bat live`.
- [ ] Verify OpenCV GUI window opens titled `Signora - ASL Fingerspelling`.
- [ ] Hold single-hand sign `A` steady for 800 ms. Verify letter `A` confirms into transcript buffer.
- [ ] Test in-air thumb-to-palm gesture (or space sign). Verify a space character appends after 800 ms dwell.
- [ ] Press `SPACE` on keyboard. Verify Piper neural voice speaks the transcript aloud.
- [ ] Press `BACKSPACE` on keyboard. Verify last character is removed.
- [ ] Press `C` on keyboard. Verify transcript clears.
- [ ] Press `ESC` or `Q`. Verify clean shutdown.

### 4.3 Indian Sign Language (ISL) Two-Handed Mode
- [ ] Run `.\signora.bat isl`.
- [ ] Verify HUD displays `ISL Two-Handed Mode`.
- [ ] Hold sign `NAMASTE` (both open palms together at chest level). Verify detection and confirmation.
- [ ] Press `ESC` or `Q`. Verify clean shutdown.

### 4.4 ASL Dynamic Words (Holistic Motion DTW-kNN)
- [ ] Run `.\signora.bat words`.
- [ ] Verify console displays vocabulary list and classifier readiness.
- [ ] Perform a greeting motion (e.g., `HELLO` wave from forehead). Verify word recognition.
- [ ] Press `Q` to exit.

### 4.5 Shadow-Play Dino Game
- [ ] Run `.\signora.bat game --kiosk`.
- [ ] Verify Microsoft Edge or Google Chrome launches in dedicated app/kiosk window.
- [ ] Verify corner preview shows live camera feed with skeleton tracking overlay.
- [ ] Click **Start Run**. Form target handshape before answer window expires to clear obstacle.
- [ ] Close browser window and press `Ctrl+C` in console to stop server.

### 4.6 Browser Learning Kiosk
- [ ] Run `.\signora.bat learn --kiosk`.
- [ ] Verify Learning Kiosk opens on `http://127.0.0.1:8000/`.
- [ ] Select letters `A` through `Z` on left navigation panel.
- [ ] Switch between **Practice Drill** and **Timed Quiz** modes.
- [ ] Verify confidence meter reflects hand shape accuracy.
- [ ] Close browser and press `Ctrl+C` in console to stop server.

---

## 5. Offline Integrity Test

1. Completely disconnect all network adapters (toggle Airplane Mode or unplug Ethernet/Wi-Fi).
2. Run `.\signora.bat doctor`.
3. Launch `.\signora.bat live` and `.\signora.bat game`.
4. **Verify:**
   - [ ] No network timeout errors occur.
   - [ ] No external telemetry requests are made.
   - [ ] Video processing, landmark extraction, neural classification, and Piper TTS speech run 100% locally.

---

## 6. Windows-Specific Troubleshooting

| Symptom | Cause | Solution |
| :--- | :--- | :--- |
| `Webcam Capture: No physical webcams currently accessible` | Windows Camera Privacy setting is disabled | Go to **Settings** > **Privacy & security** > **Camera**, turn **Camera access** ON, and enable **Let desktop apps access your camera**. |
| `Cannot bind to 127.0.0.1` | Windows Defender Firewall blocking loopback socket | Allow Python/.venv in Windows Firewall or check VPN software. |
| `Script execution disabled on this system` | Default PowerShell restricted policy | Run installer with bypass flag: `powershell -ExecutionPolicy Bypass -File .\install.ps1`. |
