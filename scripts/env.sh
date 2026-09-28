#!/usr/bin/env bash
# Shared environment for the helper scripts. Sourced, not run directly.

export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17}"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$PATH"

PKG="com.gigakiladze.pod"
ADMIN="$PKG/$PKG.kiosk.PodDeviceAdminReceiver"
HOME_ACTIVITY="$PKG/$PKG.MainActivity"

# Waits rather than failing outright: right after a factory reset the phone
# appears, disappears and reappears while it finishes booting.
require_device() {
  local waited=0 limit="${ADB_WAIT_SECONDS:-120}"
  while [[ "$(adb devices | grep -cw "device" || true)" -eq 0 ]]; do
    if [[ "$waited" -eq 0 ]]; then
      echo "Waiting for the phone (up to ${limit}s)..."
      echo "  - plugged in over USB?"
      echo "  - USB debugging on, and the 'Allow USB debugging?' prompt accepted?"
    fi
    if [[ "$waited" -ge "$limit" ]]; then
      echo "No device after ${limit}s. Check 'adb devices' and try again." >&2
      exit 1
    fi
    sleep 3
    waited=$((waited + 3))
  done

  if adb devices | grep -qw "unauthorized"; then
    echo "Device is UNAUTHORIZED. Accept the 'Allow USB debugging?' prompt on the phone." >&2
    exit 1
  fi

  # Wait out the tail of boot so pm/dpm commands do not race the package manager.
  until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
}
