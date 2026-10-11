# Signora Troubleshooting & Diagnostic Guide

This guide helps diagnose and resolve issues with video capture, neural model inference, audio playback, browser kiosks, and system performance.

---

## 1. Quick Diagnosis: `signora doctor`

Before applying manual fixes, run the built-in diagnostic doctor:
```bash
signora doctor
```
Or on Windows:
```cmd
.\signora.bat doctor
```
The doctor tests all 10 hardware and software components (Python runtime, OS capture backend, webcam accessibility, MediaPipe Tasks API, ASL weights, ISL weights, Words model, Piper TTS audio, browser kiosk, and loopback socket) and provides targeted recommendations.

---

## 2. Webcam & Video Capture Issues

### Problem: `No physical webcams currently accessible`

#### On Linux:
1. **Check Video Device Permissions:**
   Ensure your user account belongs to the `video` group:
   ```bash
   sudo usermod -a -G video $USER
   ```
   Log out and log back in for changes to take effect.
2. **Verify Device Existence:**
   ```bash
   ls -la /dev/video*
   v4l2-ctl --list-devices
   ```
3. **Select Camera Index:**
   If your built-in webcam is on index 1 or 2, pass `--source`:
   ```bash
   signora live --source 1
   ```

#### On Windows:
1. **Check Windows Camera Privacy Settings:**
   - Open **Settings** (`Win + I`).
   - Navigate to **Privacy & security** > **Camera**.
   - Ensure **Camera access** is toggled **ON**.
   - Scroll down to **Let desktop apps access your camera** and toggle it **ON**.
2. **Check Device Manager:**
   - Press `Win + X` > **Device Manager**.
   - Expand **Cameras** or **Imaging devices**.
   - Confirm your camera is enabled and not in use by another app (e.g., Zoom, Teams, Skype).

---

## 3. Performance Tuning for Low-End Hardware

If running on a low-power laptop (e.g., dual-core CPU with integrated graphics):

### 1. Enable Frame Skipping (`--detect-every`)
MediaPipe spatial landmark estimation is the most compute-intensive stage. Process landmarks every 2nd or 3rd frame while keeping 30fps video display:
```bash
signora live --detect-every 2
```
This reduces CPU consumption by approximately 45–50% with negligible dwell responsiveness difference.

### 2. Run in Headless Mode
When integrating with assistive screen readers or testing, bypass OpenCV GUI rendering:
```bash
signora live --headless
```

### 3. Lower Words Detection Frequency
Set the dynamic words detector cadence:
```bash
export SIGNORA_WORDS_DETECT_EVERY=2
signora words
```

---

## 4. Audio & Text-to-Speech (Piper)

### Problem: `Audio device unavailable` or Muted Sound
- Signora detects available audio output devices via `sounddevice`.
- If no physical speakers or headphones are connected, speech output is automatically suppressed while visual HUD recognition remains fully functional.
- To explicitly run in silent mode:
  ```bash
  signora live --no-tts
  ```
- **Linux Audio Server:** Ensure PipeWire or PulseAudio daemon is running:
  ```bash
  systemctl --user status pipewire
  ```

---

## 5. Model Asset Checksum Failures

### Problem: `SHA-256 mismatch` or Corrupt Model File
If a download was interrupted or corrupted:
1. Re-run the standalone downloader to fetch and re-verify all models:
   ```bash
   python scripts/fetch_models.py
   ```
2. Or verify installed models:
   ```bash
   signora models verify
   ```

---

## 6. Browser Kiosk (Game & Learning Modes)

### Problem: Port 8000 Already in Use
If another local service is using port 8000:
```bash
signora game --port 8080
signora learn --port 8080
```

### Problem: Dedicated Kiosk Mode Doesn't Launch Fullscreen
- By default, `--kiosk` searches for `chromium`, `google-chrome`, or `msedge.exe`.
- If no Chromium-based browser is installed, Signora automatically falls back to your operating system's default browser.
