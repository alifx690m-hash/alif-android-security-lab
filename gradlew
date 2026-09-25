#!/data/data/com.termux/files/usr/bin/bash
set -e

GRADLE_VERSION="8.2.1"
CACHE_DIR="${HOME}/.cache/alif-gradle"
GRADLE_HOME="${CACHE_DIR}/gradle-${GRADLE_VERSION}"
ZIP_FILE="${CACHE_DIR}/gradle-${GRADLE_VERSION}-bin.zip"

if ! command -v java >/dev/null 2>&1; then
  echo "[!] Java is not installed."
  echo "    Run: pkg install openjdk-17"
  exit 1
fi

mkdir -p "${CACHE_DIR}"

if [ ! -x "${GRADLE_HOME}/bin/gradle" ]; then
  echo "[*] Downloading Gradle ${GRADLE_VERSION}..."
  if command -v curl >/dev/null 2>&1; then
    curl -L --fail --retry 3 -o "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "${ZIP_FILE}" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  else
    echo "[!] curl/wget not found. Run: pkg install curl"
    exit 1
  fi
  rm -rf "${CACHE_DIR}/gradle-${GRADLE_VERSION}" "${CACHE_DIR}/gradle-${GRADLE_VERSION}-tmp"
  mkdir -p "${CACHE_DIR}/gradle-${GRADLE_VERSION}-tmp"
  unzip -q "${ZIP_FILE}" -d "${CACHE_DIR}/gradle-${GRADLE_VERSION}-tmp"
  mv "${CACHE_DIR}/gradle-${GRADLE_VERSION}-tmp/gradle-${GRADLE_VERSION}" "${GRADLE_HOME}"
  rm -rf "${CACHE_DIR}/gradle-${GRADLE_VERSION}-tmp" "${ZIP_FILE}"
fi

exec "${GRADLE_HOME}/bin/gradle" "$@"
