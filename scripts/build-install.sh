#!/usr/bin/env bash
# Builds the APK and installs it on the connected device.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
require_device

echo "==> Building"
./gradlew :app:assembleDebug

echo "==> Installing"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk

echo "==> Granting storage access"
# Device owner can auto-grant the runtime permission, but all-files access is an
# appop and has to come from here.
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

echo "==> Launching"
adb shell am start -n "$HOME_ACTIVITY" >/dev/null
echo "Done."
