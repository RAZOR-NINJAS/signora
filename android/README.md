# 📱 Signora Native Android

**100% Offline Edge Neural Sign Language Recognition for Android (Kotlin + Jetpack Compose + MediaPipe Tasks Vision)**

---

## ⚡ Highlights

- **Sub-Millisecond Inference**: Custom pure-Kotlin neural forward engine executing ASL & ISL MLP layers in under **0.2ms/frame** on ARM64 processors without heavy C++/NDK bloat.
- **Hardware-Accelerated Hand Tracking**: Integrated with **Google MediaPipe Tasks Vision Android SDK** (`com.google.mediapipe:tasks-vision`), with GPU/NPU acceleration and CPU fallback.
- **Built-in Offline Voice Output**: Leverages native Android `android.speech.tts.TextToSpeech`—zero external downloads, zero cloud network latency, 100% private.
- **Self-Healing Model Dependencies**:
  - Automatically bundles `asl_model.json`, `isl_model.json`, and `hand_landmarker.task` in the APK assets.
  - If any model file is ever missing or corrupted, `AutoAssetManager` automatically downloads and restores it from GitHub releases in the background.
- **Glassmorphic Cyberpunk HUD**: Custom Jetpack Compose UI with real-time skeleton overlay, animated dwell progress ring, live FPS counter, and responsive transcript controls.

---

## 📂 Architecture & Directory Structure

```
android/
├── app/
│   ├── build.gradle.kts           # Compose, CameraX, and MediaPipe dependencies
│   ├── src/main/
│   │   ├── AndroidManifest.xml    # Camera and Internet permissions
│   │   ├── assets/models/         # Bundled offline neural weights & tasks
│   │   │   ├── asl_model.json     # 74D ASL MLP weights & normalization
│   │   │   ├── isl_model.json     # 164D ISL dual-handed MLP weights
│   │   │   └── hand_landmarker.task # MediaPipe Vision Task binary
│   │   ├── java/ai/signora/
│   │   │   ├── MainActivity.kt    # Permission handling & pipeline coordinator
│   │   │   ├── camera/            # CameraX 640x480 zero-copy stream
│   │   │   ├── vision/            # MediaPipe Tasks HandLandmarker wrapper
│   │   │   ├── ml/                # Scale-invariant feature extraction & MLP forward pass
│   │   │   ├── engine/            # Temporal debounce, dwell & transcript retention
│   │   │   ├── tts/               # Android native TextToSpeech manager
│   │   │   ├── recovery/          # AutoAssetManager (self-healing downloader)
│   │   │   └── ui/                # Jetpack Compose cyberpunk HUD & skeleton canvas
│   │   └── res/                   # Themes, strings, icons, and colors
├── gradle/wrapper/                # Auto-downloading Gradle 8.4 wrapper
├── gradlew                        # POSIX wrapper script
├── build.gradle.kts               # Root Gradle project configuration
├── settings.gradle.kts            # Project repositories & modules
└── setup.sh                       # 1-Click automated dependency solver & APK compiler
```

---

## 🚀 Building & Running

### Option A: 1-Click Automated Build (CLI)

Run the self-healing setup script from repository root:

```bash
./android/setup.sh
```

This script automatically:
1. Converts the latest PyTorch weights to mobile assets via `scripts/export_mobile_models.py`.
2. Verifies and installs JDK 17 if not present.
3. Downloads the Gradle wrapper.
4. Compiles the debug APK to `android/app/build/outputs/apk/debug/app-debug.apk`.

### Option B: Android Studio

1. Open **Android Studio** (Hedgehog 2023.1.1 or newer).
2. Click **Open** and select the `/home/vaibhav/Projects/signora/android` directory.
3. Android Studio will automatically sync dependencies from Google Maven and Gradle repositories.
4. Click **Run (Shift + F10)** on a connected Android phone (via USB/Wi-Fi ADB) or an emulator with webcam enabled.

---

## 📲 Installing on Your Phone via ADB

Once built, connect your Android device with USB Debugging enabled:

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

Or copy `app-debug.apk` directly to your phone storage and open it in any Android file manager.
