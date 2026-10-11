#!/usr/bin/env bash
# ==============================================================================
#  Signora Native Android - Automated Dependency Resolver & Build System
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================================"
echo "  SIGNORA -- Native Android Automated Dependency Resolver  "
echo "============================================================"
echo

# 1. Export Neural Models & Assets to Android Assets
echo "⚡ [1/4] Generating mobile neural model assets..."
if [ -f "${ROOT_DIR}/.venv/bin/python" ]; then
    "${ROOT_DIR}/.venv/bin/python" "${ROOT_DIR}/scripts/export_mobile_models.py"
elif command -v python3 >/dev/null 2>&1; then
    python3 "${ROOT_DIR}/scripts/export_mobile_models.py"
else
    echo "⚠️ Python not found, using pre-bundled assets."
fi

# 2. Verify or Install Java (JDK 17)
echo
echo "⚡ [2/4] Checking Java Development Kit (JDK 17+)..."
if ! command -v java >/dev/null 2>&1; then
    echo "Java not found in PATH. Checking mise / system installers..."
    if command -v mise >/dev/null 2>&1; then
        echo "Installing Java 17 via mise..."
        mise use -g java@17
    elif command -v pacman >/dev/null 2>&1; then
        echo "Tip: Run 'sudo pacman -S jdk17-openjdk' to install JDK 17 on Arch Linux."
    elif command -v apt-get >/dev/null 2>&1; then
        echo "Tip: Run 'sudo apt-get install openjdk-17-jdk' to install JDK 17 on Ubuntu/Debian."
    fi
else
    echo "✓ Found Java: $(java -version 2>&1 | head -n 1)"
fi

# 3. Ensure Gradle Wrapper
echo
echo "⚡ [3/4] Ensuring Gradle wrapper runtime..."
WRAPPER_JAR="${SCRIPT_DIR}/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "${WRAPPER_JAR}" ]; then
    mkdir -p "${SCRIPT_DIR}/gradle/wrapper"
    echo "Fetching Gradle wrapper jar..."
    curl -sLo "${WRAPPER_JAR}" https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar || true
fi
chmod +x "${SCRIPT_DIR}/gradlew"

# 4. Check Android SDK
echo
echo "⚡ [4/4] Checking Android SDK environment..."
if [ -z "${ANDROID_HOME}" ] && [ -z "${ANDROID_SDK_ROOT}" ]; then
    # Standard locations
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "$HOME/.android-sdk" ]; then
        export ANDROID_HOME="$HOME/.android-sdk"
    fi
fi

if [ -n "${ANDROID_HOME}" ] && [ -d "${ANDROID_HOME}" ]; then
    echo "✓ ANDROID_HOME configured: ${ANDROID_HOME}"
    echo
    echo "🚀 Building Signora Native Android APK..."
    cd "${SCRIPT_DIR}"
    ./gradlew assembleDebug --no-daemon
    echo
    echo "🎉 SUCCESS: Native Android APK built at:"
    echo "   ${SCRIPT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
else
    echo "ℹ️  ANDROID_HOME not detected in environment."
    echo "   You can open the '${SCRIPT_DIR}' project directly in Android Studio,"
    echo "   or set ANDROID_HOME and run './setup.sh' to compile the APK from CLI."
fi
