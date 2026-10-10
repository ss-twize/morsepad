#!/usr/bin/env bash
# Builds MorsePad: runs the unit tests, then assembles debug and release APKs into dist/.
# Usage: scripts/build-apk.sh [--skip-tests]
set -euo pipefail

cd "$(dirname "$0")/.."

# Gradle 8.9 / AGP 8.5 need JDK 17–21. Prefer an explicit JAVA_HOME, then Android Studio's JBR.
if [[ -z "${JAVA_HOME:-}" ]] || ! "$JAVA_HOME/bin/java" -version 2>&1 | grep -qE 'version "(17|21)'; then
  for candidate in \
    "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
    "/opt/android-studio/jbr" \
    "$HOME/android-studio/jbr"; do
    if [[ -x "$candidate/bin/java" ]]; then
      export JAVA_HOME="$candidate"
      break
    fi
  done
fi
if [[ -z "${JAVA_HOME:-}" ]]; then
  echo "error: JDK 17 or 21 not found. Install Android Studio or set JAVA_HOME." >&2
  exit 1
fi
echo "Using JAVA_HOME=$JAVA_HOME"

# Android SDK location: local.properties, ANDROID_HOME, or the default macOS/Linux path.
if [[ ! -f local.properties ]]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  [[ -z "$sdk" && -d "$HOME/Library/Android/sdk" ]] && sdk="$HOME/Library/Android/sdk"
  [[ -z "$sdk" && -d "$HOME/Android/Sdk" ]] && sdk="$HOME/Android/Sdk"
  if [[ -z "$sdk" ]]; then
    echo "error: Android SDK not found. Set ANDROID_HOME or create local.properties with sdk.dir=..." >&2
    exit 1
  fi
  echo "sdk.dir=$sdk" > local.properties
fi

tasks=(:app:assembleDebug :app:assembleRelease)
if [[ "${1:-}" != "--skip-tests" ]]; then
  tasks=(:core:test "${tasks[@]}")
fi
./gradlew "${tasks[@]}"

version=$(grep -m1 'versionName' app/build.gradle.kts | sed -E 's/.*"(.*)".*/\1/')
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk "dist/MorsePad-$version-release.apk"
cp app/build/outputs/apk/debug/app-debug.apk "dist/MorsePad-$version-debug.apk"
echo
echo "APKs:"
ls -lh dist/*.apk
