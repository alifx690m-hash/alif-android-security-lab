#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$(dirname "$0")"

echo "== ALIF Android Security Lab APK Builder =="

if ! command -v java >/dev/null 2>&1; then
  echo "[!] Java missing."
  echo "    pkg install openjdk-17"
  exit 1
fi

if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  echo "[!] Android SDK environment is not configured."
  echo "    Set ANDROID_HOME or ANDROID_SDK_ROOT to a valid Android SDK."
  echo "    On a phone-only Termux setup, GitHub Actions is usually easier."
  exit 1
fi

SDK="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
if [ ! -d "$SDK" ]; then
  echo "[!] Android SDK directory not found: $SDK"
  exit 1
fi

./gradlew --version
./gradlew :app:assembleDebug

echo
echo "[+] APK build complete:"
echo "    app/build/outputs/apk/debug/app-debug.apk"
