---
title: "Command: adb commands"
tags:
  - cmd/android
aliases:
  - adb commands
  - adb
---

# `adb commands`

> [!INFO] Summary
> **Android Debug Bridge (ADB) Operational Playbook**. Commands for installing APKs, launching activities, monitoring live logcat telemetry, transferring datasets, and managing wireless USB connections.

---

## 1. Device Connection & Verification

```bash
# List all connected USB and Wi-Fi Android devices
adb devices -l

# Restart ADB server daemon if device is unresponsive
adb kill-server && adb start-server
```

---

## 2. APK Installation & Deployment

```bash
# Install or update Signora debug APK (-r preserves app data and permissions)
adb install -r android/app/build/outputs/apk/debug/app-debug.apk

# Re-install with grant all runtime permissions automatically
adb install -r -g android/app/build/outputs/apk/debug/app-debug.apk

# Uninstall app cleanly
adb uninstall ai.signora
```

---

## 3. Application Lifecycle Management

```bash
# Launch Signora main activity on phone
adb shell am start -n ai.signora/.MainActivity

# Force close / stop Signora app
adb shell am force-stop ai.signora

# Clear all application data and cache
adb shell pm clear ai.signora
```

---

## 4. Live Telemetry & Logcat Monitoring

```bash
# Stream filtered logs (Signora telemetry + runtime exceptions only)
adb logcat -s Signora:* AndroidRuntime:*

# Real-time profiling and inference timing logs
adb logcat -s TrackingProfiler:* OneEuroFilter:*

# Clear logcat buffer before a fresh test run
adb logcat -c
```

---

## 5. Dataset Transfer

```bash
# Pull exported ISL training dataset from phone to PC
adb pull /sdcard/Download/signora_isl_dataset.json .

# Push dataset file to phone storage
adb push signora_isl_dataset.json /sdcard/Download/
```

---

## 6. Wireless Debugging via Wi-Fi

```bash
# 1. Connect phone via USB cable first, then enable TCP/IP mode:
adb tcpip 5555

# 2. Disconnect USB cable and connect over local Wi-Fi:
adb connect 192.168.1.105:5555

# 3. Confirm connection:
adb devices -l
```

---

## 7. Related Commands
- [[android gradlew]] — Compile and build debug APK.
- [[signora phone]] — Zero-latency phone webcam streaming via scrcpy.
- [[signora dataset]] — Ingest pulled mobile datasets.
