# Signora CLI & Tooling Command Reference

This document provides a complete reference for all command-line operations across the Signora ecosystem, including real-time recognition, phone camera streaming via scrcpy, data collection, model training, virtual webcam broadcasting, and Android deployment.

---

## 1. Quick Start & Common Commands

Run commands inside the Python virtual environment:
```bash
source .venv/bin/activate
```

| Task | Command |
|---|---|
| Indian Sign Language (ISL) Recognition | `signora isl` |
| ISL with Body Pose & Spatial Anchors | `signora body` *(or `signora isl --body`)* |
| Use Phone Camera as Zero-Latency Webcam | `signora phone isl` *(or `signora isl --phone`)* |
| ASL Single-Hand Alphabet | `signora asl` *(or `signora live`)* |
| Dynamic Motion Gestures (DTW-kNN) | `signora words` |
| Interactive Terminal UI (TUI) | `signora tui` |
| System Health & Camera Diagnostics | `signora doctor` |
| Virtual Webcam for Zoom / Meet / Teams | `signora stream` |

---

## 2. Phone Camera Bridge via scrcpy (`signora phone` & `--phone`)

Connect an Android smartphone via USB cable with **USB Debugging** enabled in Developer Options. Signora automatically manages ADB detection, configures `v4l2loopback` with single-buffer latency optimization, and feeds high-resolution 60 FPS video directly into Signora pipelines.

### One-Command Phone Launchers
```bash
# Launch ISL recognition using connected phone camera
signora phone isl

# Launch Body Pose + Hand Spatial Recognition using phone camera
signora phone body

# Launch ASL Fingerspelling using phone camera
signora phone asl

# Launch Dynamic Words using phone camera
signora phone words
```

### Camera Facing & Audio Options
```bash
# Use front-facing selfie camera instead of back camera
signora phone isl --facing front

# Disable Piper TTS audio speech output
signora phone isl --no-tts

# Override dwell confirmation time (e.g., 200ms ultra-fluid)
signora phone isl --dwell 200
```

### Using `--phone` on Any Recognition Subcommand
The `--phone` flag is available on all standard subcommands:
```bash
signora isl --phone
signora body --phone
signora live --phone
signora stream --phone
```

---

## 3. Indian Sign Language (ISL) Recognition (`signora isl`)

Dual-handed sign recognition supporting static letters (A-Z) and standard glosses.

```bash
# Standard ISL recognition on default camera (index 0)
signora isl

# Specific physical camera index or video source
signora isl -s 1
signora isl -s /dev/video2

# Specific resolution preset (720p, 480p, 360p, 1080p - strictly 16:9)
signora isl --res 720p

# Fluid timing control (default: 250ms with kinetic apex acceleration)
signora isl --dwell 250

# Run with whole-body pose tracking enabled
signora isl --body

# Headless mode (no OpenCV GUI window, prints transcript to stdout)
signora isl --headless

# Testing with synthetic test bench camera
signora isl --fake-camera

# Pass Google Gemini API key for Tier-2 LLM natural sentence synthesis
signora isl --gemini-key "YOUR_API_KEY"
```

---

## 4. Whole-Body Pose & Spatial Placement Anchors (`signora body`)

Tracks upper-body pose landmarks alongside hands to disambiguate signs differentiated by body placement (e.g., Forehead, Temple, Chest, Shoulder, Throat, Chin).

```bash
# Launch Body Spatial recognition on default camera
signora body

# Alias command
signora spatial

# Use phone camera
signora body --phone

# Connect to IP camera or network stream
signora body -s "http://192.168.1.100:8080/video"

# Launch body collection studio to record new spatial signs
signora body --collect

# Train 220D Spatial Hand+Body PyTorch MLP model
signora body --train
```

---

## 5. American Sign Language (ASL) Fingerspelling (`signora asl` / `signora live`)

Single-hand static alphabet recognition (A-Z + SPACE) with real-time cyberpunk glassmorphic HUD.

```bash
# Run ASL fingerspelling on camera 0
signora asl

# Alias command
signora live

# Custom dwell duration (e.g. 180ms for expert rapid fingerspelling)
signora asl --dwell 180

# Run without Piper TTS audio
signora asl --no-tts

# Stream using phone camera
signora asl --phone
```

---

## 6. Dynamic Motion Gestures (`signora words`)

Holistic dynamic sign language motion recognition using Dynamic Time Warping and k-Nearest Neighbors (DTW-kNN).

```bash
# Run dynamic gesture recognition
signora words

# Adjust confidence classification threshold
signora words --threshold 0.25

# Process specific video recording file
signora words -s test_sample.mp4
```

---

## 7. Virtual Camera Streaming for Zoom / Meet / Teams (`signora stream`)

Broadcasts Signora's real-time transcript, lower-third captions, and hand landmarks into a virtual camera device that can be selected in Zoom, Google Meet, Microsoft Teams, or OBS.

```bash
# Display v4l2loopback installation guide
signora stream --setup-v4l2

# Stream ISL recognition to virtual webcam (/dev/video10 default)
signora stream -m isl

# Stream ASL recognition
signora stream -m asl

# Broadcast clean stream (lower-third captions only, without HUD cards)
signora stream --clean

# Hide skeletal hand lines on broadcast stream
signora stream --no-hands

# Stream from connected Android phone
signora stream --phone

# Custom resolution and frame rate
signora stream --width 1920 --height 1080 --fps 60

# Specify custom target loopback device
signora stream --device /dev/video4
```

---

## 8. Sound Detection & Alert (`signora alert` / `signora listen`)

*"Hear Through Your Eyes"*: Detects environmental acoustic events (doorbell, emergency sirens, knocks, baby crying, dog barking, glass breaking) and displays visual overlays and OS notifications.

```bash
# Run interactive visual sound monitor
signora alert

# Start background sound watcher daemon
signora alert --start

# Check background daemon status
signora alert --status

# Stop running background daemon
signora alert --stop

# Adjust microphone sensitivity (low, medium, high)
signora alert --sensitivity high

# Profile room ambient acoustics and calibrate sensitivity
signora alert --calibrate

# Fire an immediate test alert notification
signora alert --test

# Simulate specific acoustic event
signora alert --simulate doorbell
signora alert --simulate siren
signora alert --simulate knock
```

---

## 9. Data Collection Studio & Model Training

Record custom sign samples and train local PyTorch neural networks.

### Sample Collection Studio (`signora collect` / `signora studio`)
```bash
# Open interactive collection studio for letter A
signora collect -s A

# Collect 500 samples with a 3-second countdown
signora collect -s B -n 500 --timer 3.0

# Collect with upper-body 220D spatial features
signora collect -s HELLO --body

# Automatically train neural network immediately after collection
signora collect -s EMERGENCY --train
```

### PyTorch MLP Training (`signora train`)
```bash
# Train standard ISL hand topology model (50 epochs)
signora train --epochs 50

# Train 220D Spatial Hand+Body model
signora train --body --epochs 60 --lr 0.001

# Import mobile-recorded dataset JSON and retrain model
signora train -i signora_isl_dataset.json
```

### Dataset Management (`signora dataset`)
```bash
# Inspect recorded dataset statistics
signora dataset inspect signora_isl_dataset.json

# Import dataset and retrain model automatically
signora dataset import signora_isl_dataset.json
```

---

## 10. Games & Interactive Learning

### Gesture-Controlled Shadow Runner Game (`signora game`)
```bash
# Launch Dino Runner game server on port 8000
signora game

# Launch in kiosk mode with dual-handed ISL gestures
signora game --mode isl --kiosk

# Custom port
signora game -p 9000
```

### Learning Kiosk & Quizzes (`signora learn`)
```bash
# Launch interactive learning kiosk
signora learn

# Open fullscreen browser kiosk window
signora learn --kiosk
```

---

## 11. System Diagnostics, Models & Lip Reading

```bash
# Comprehensive system health, camera, and dependency inspection
signora doctor

# Inspect neural model weights status
signora models list

# Fetch/download missing neural model weights
signora models fetch

# Verify integrity and checksum of local model weights
signora models verify

# Real-time visual lip sync and speech activity detection
signora lipsync

# Replay futuristic introductory startup animation
signora --intro
```

---

## 12. Android App Commands

Android Gradle build, device installation, and debugging commands:

```bash
# Build debug APK
cd android && ./gradlew assembleDebug && cd ..

# Install built APK to connected Android device
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Launch Signora app on phone
adb shell am start -n ai.signora/.MainActivity

# Stream filtered application logcat
adb logcat -s Signora:* AndroidRuntime:*

# Force stop application
adb shell am force-stop ai.signora

# Check connected Android devices
adb devices -l
```

---

## 13. Test Suite Verification

Run the full automated test suite (166 unit and integration tests):
```bash
# Run all tests
./.venv/bin/pytest tests/

# Run debounce timing tests
./.venv/bin/pytest tests/test_core_debounce.py

# Run NLP synthesizer tests
./.venv/bin/pytest tests/test_nlp_synthesizer.py

# Run phone webcam bridge tests
./.venv/bin/pytest tests/test_phone.py
```
