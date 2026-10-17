#!/usr/bin/env bash
set -e
APK_PATH="/home/vaibhav/Projects/signora/android/Signora.apk"
echo "=================================================="
echo "Unspoken (Signora) - Automated Device Installer"
echo "=================================================="
echo "Waiting for connected Android device via ADB..."
adb wait-for-device
DEVICE_INFO=$(adb devices -l | grep "device " || true)
echo "Found device: $DEVICE_INFO"
echo "Installing $APK_PATH..."
adb install -r -d "$APK_PATH"
echo "Granting Camera permissions..."
adb shell pm grant ai.signora.debug android.permission.CAMERA 2>/dev/null || true
echo "Launching Unspoken..."
adb shell am start -n ai.signora.debug/ai.signora.MainActivity
echo "=================================================="
echo "Installation complete and application running!"
echo "=================================================="
