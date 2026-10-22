---
title: "Command: android gradlew"
tags:
  - cmd/android
aliases:
  - android gradlew
  - gradlew
  - assembleDebug
---

# `android gradlew`

> [!INFO] Summary
> **Android Build Automation Tooling**. Compiles the native Kotlin Signora Android application, processes MediaPipe Tasks vision assets, compiles Jetpack Compose UI, and packages the debug or release APK.

---

## 1. Syntax

Execute within the `android/` directory:
```bash
cd android && ./gradlew [TASK] [OPTIONS] && cd ..
```

---

## 2. Common Tasks

### Standard Debug APK Build
```bash
cd android && ./gradlew assembleDebug && cd ..
```
*Generated output APK:*
`android/app/build/outputs/apk/debug/app-debug.apk` (approx. 72 MB).

### Clean & Rebuild
```bash
# Removes all cached build artifacts and performs full recompilation
cd android && ./gradlew clean assembleDebug && cd ..
```

### Build & Deploy Directly to Connected Phone
```bash
# Compiles APK and automatically installs it to the primary ADB device
cd android && ./gradlew installDebug && cd ..
```

### Build Release APK
```bash
cd android && ./gradlew assembleRelease && cd ..
```

---

## 3. Build Optimizations & Environment
- **Java Runtime**: OpenJDK 17.
- **Gradle Version**: 8.7 with Android Gradle Plugin 8.4.
- **Compose Compiler**: Kotlin 2.0.21 with Compose Compiler Plugin.
- **De-sugaring**: Enabled for `java.time` and Java 8+ APIs on older Android devices.

---

## 4. Related Commands
- [[adb commands]] — Install, run, and debug APK on physical Android phones.
- [[signora phone]] — Stream phone camera to desktop PC.
