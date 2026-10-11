#!/usr/bin/env bash
# ==============================================================================
#  Signora Native Android - 1-Click Dependency Installer & APK Compiler
# ==============================================================================
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================================"
echo "  SIGNORA -- Native Android Automated APK Build Pipeline   "
echo "============================================================"
echo

# 1. Setup Java 17 Runtime Environment
echo "⚡ [1/5] Configuring Java 17 Development Kit..."
if [ -d "$HOME/.local/share/mise/installs/java/17.0.2" ]; then
    export JAVA_HOME="$HOME/.local/share/mise/installs/java/17.0.2"
    export PATH="$JAVA_HOME/bin:$PATH"
elif [ -d "/usr/lib/jvm/java-17-openjdk" ]; then
    export JAVA_HOME="/usr/lib/jvm/java-17-openjdk"
    export PATH="$JAVA_HOME/bin:$PATH"
fi

if ! command -v java >/dev/null 2>&1; then
    echo "Java not found in PATH. Attempting automatic installation via mise..."
    if command -v mise >/dev/null 2>&1; then
        mise use -g java@17
        export JAVA_HOME="$HOME/.local/share/mise/installs/java/17.0.2"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
fi

if ! command -v java >/dev/null 2>&1; then
    echo "❌ Error: Java 17 is required. Please install OpenJDK 17 and re-run."
    exit 1
fi
echo "✓ Active Java: $(java -version 2>&1 | head -n 1)"

# 2. Export & Bundle Offline Neural Weights into Android Assets
echo
echo "⚡ [2/5] Exporting neural models and MediaPipe assets..."
if [ -f "${ROOT_DIR}/.venv/bin/python" ]; then
    "${ROOT_DIR}/.venv/bin/python" "${ROOT_DIR}/scripts/export_mobile_models.py" || true
elif command -v python3 >/dev/null 2>&1; then
    python3 "${ROOT_DIR}/scripts/export_mobile_models.py" || true
else
    echo "Using existing pre-bundled assets in android/app/src/main/assets/models/"
fi

# 3. Ensure Gradle Wrapper Runtime
echo
echo "⚡ [3/5] Verifying Gradle wrapper..."
WRAPPER_JAR="${SCRIPT_DIR}/gradle/wrapper/gradle-wrapper.jar"
if [ ! -f "${WRAPPER_JAR}" ] || [ ! -s "${WRAPPER_JAR}" ]; then
    echo "Downloading Gradle wrapper bootstrap jar..."
    mkdir -p "${SCRIPT_DIR}/gradle/wrapper"
    curl -sLo "${WRAPPER_JAR}" https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar
fi
chmod +x "${SCRIPT_DIR}/gradlew"

# 4. Detect or Auto-Install Android SDK Commandline Tools
echo
echo "⚡ [4/5] Checking Android SDK environment..."
if [ -z "${ANDROID_HOME}" ] && [ -z "${ANDROID_SDK_ROOT}" ]; then
    if [ -d "$HOME/Android/Sdk" ]; then
        export ANDROID_HOME="$HOME/Android/Sdk"
    elif [ -d "$HOME/.android-sdk" ]; then
        export ANDROID_HOME="$HOME/.android-sdk"
    else
        export ANDROID_HOME="$HOME/.android-sdk"
    fi
fi

if [ ! -f "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Android SDK tools not found. Setting up Android Command-line Tools into ${ANDROID_HOME}..."
    mkdir -p "${ANDROID_HOME}/cmdline-tools"
    
    CMDLINE_ZIP="/tmp/cmdline-tools.zip"
    if [ ! -f "${CMDLINE_ZIP}" ]; then
        echo "Downloading Google Android Command-line Tools (~140MB)..."
        curl -L --retry 3 -o "${CMDLINE_ZIP}" https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    fi
    
    echo "Extracting Android SDK tools..."
    unzip -q -o "${CMDLINE_ZIP}" -d "${ANDROID_HOME}/cmdline-tools"
    rm -rf "${ANDROID_HOME}/cmdline-tools/latest" 2>/dev/null || true
    mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" 2>/dev/null || true
fi

export PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${PATH}"
echo "sdk.dir=${ANDROID_HOME}" > "${SCRIPT_DIR}/local.properties"

# Ensure required platform-34 and build-tools-34.0.0 exist
if [ ! -d "${ANDROID_HOME}/platforms/android-34" ] || [ ! -d "${ANDROID_HOME}/build-tools/34.0.0" ]; then
    echo "Resolving Android Platform 34 & Build-Tools 34.0.0..."
    python3 -c "
import urllib.request, zipfile, os, shutil

sdk_root = os.environ.get('ANDROID_HOME', os.path.expanduser('~/.android-sdk'))
downloads = [
    ('https://dl.google.com/android/repository/platform-34-ext7_r03.zip', '/tmp/platform-34.zip', 'platforms/android-34'),
    ('https://dl.google.com/android/repository/build-tools_r34-linux.zip', '/tmp/build-tools-34.zip', 'build-tools/34.0.0'),
    ('https://dl.google.com/android/repository/platform-tools_r37.0.1-linux.zip', '/tmp/platform-tools.zip', 'platform-tools')
]

for url, zip_path, dest_rel in downloads:
    dest = os.path.join(sdk_root, dest_rel)
    if os.path.exists(dest):
        continue
    print(f'Downloading {url}...')
    existing = os.path.getsize(zip_path) if os.path.exists(zip_path) else 0
    req = urllib.request.Request(url, headers={'User-Agent': 'curl/7.88.1'})
    if existing > 0:
        req.add_header('Range', f'bytes={existing}-')
    with urllib.request.urlopen(req, timeout=45) as resp, open(zip_path, 'ab' if existing > 0 else 'wb') as f:
        shutil.copyfileobj(resp, f)
    print(f'Extracting {dest_rel}...')
    tmp_dir = zip_path + '_ext'
    os.makedirs(tmp_dir, exist_ok=True)
    with zipfile.ZipFile(zip_path, 'r') as z:
        z.extractall(tmp_dir)
    items = os.listdir(tmp_dir)
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    if len(items) == 1 and os.path.isdir(os.path.join(tmp_dir, items[0])):
        shutil.move(os.path.join(tmp_dir, items[0]), dest)
    else:
        shutil.move(tmp_dir, dest)
    shutil.rmtree(tmp_dir, ignore_errors=True)
    print(f'✓ Ready: {dest}')
" || true
fi

echo "✓ Android SDK environment verified."

# 5. Compile Standalone Android APK
echo
echo "⚡ [5/5] Compiling Signora standalone APK..."
cd "${SCRIPT_DIR}"
./gradlew assembleDebug --no-daemon

OUTPUT_APK="${SCRIPT_DIR}/app/build/outputs/apk/debug/app-debug.apk"
FINAL_APK="${ROOT_DIR}/Signora.apk"

if [ -f "${OUTPUT_APK}" ]; then
    cp -f "${OUTPUT_APK}" "${FINAL_APK}"
    cp -f "${OUTPUT_APK}" "${SCRIPT_DIR}/Signora.apk"
    
    APK_SIZE=$(du -h "${FINAL_APK}" | cut -f1)
    echo
    echo "============================================================"
    echo "🎉 SUCCESS: Single Standalone Android APK Built!"
    echo "============================================================"
    echo "📦 Final Package File:  ${FINAL_APK} (${APK_SIZE})"
    echo
    echo "📲 How to install on your Android phone:"
    echo "   1. Via USB Debugging (ADB):"
    echo "      adb install -r ${FINAL_APK}"
    echo
    echo "   2. Direct Transfer:"
    echo "      Send 'Signora.apk' to your phone via USB cable, Google Drive,"
    echo "      or WhatsApp/Telegram, and tap to install!"
    echo "============================================================"
else
    echo "❌ Build completed but APK output not found at ${OUTPUT_APK}"
    exit 1
fi
