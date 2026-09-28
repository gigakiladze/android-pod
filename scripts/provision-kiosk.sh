#!/usr/bin/env bash
# Promotes the app to device owner, which is what unlocks true kiosk mode.
#
# PREREQUISITE: the phone must have been factory reset and NOT had any account
# added during setup. Skip Google sign-in, skip everything you can. If an account
# exists, set-device-owner will refuse.
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
require_device

echo "==> Checking for accounts that would block provisioning"
if adb shell dumpsys account | grep -q "Account {name="; then
  echo
  echo "REFUSING: this device has at least one account on it." >&2
  echo "Device owner can only be set on a device with no accounts." >&2
  echo "Either remove every account (Settings > Passwords & accounts), or factory" >&2
  echo "reset and skip all sign-ins during setup, then run this again." >&2
  exit 1
fi

echo "==> Installing the app"
adb install -r -g app/build/outputs/apk/debug/app-debug.apk

# Must happen BEFORE set-device-owner. Setting the owner starts the app process
# via the admin broadcast, and on Android 11+ a process only picks up
# MANAGE_EXTERNAL_STORAGE at startup — grant it later and the already-running
# process keeps a restricted view of storage and the scanner finds nothing.
echo "==> Granting storage access (before the app process starts)"
adb shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow || true
adb shell pm grant "$PKG" android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

echo "==> Setting device owner"
adb shell dpm set-device-owner "$ADMIN"

echo "==> Making it the home launcher"
adb shell cmd package set-home-activity "$HOME_ACTIVITY" || true

# Lock task hides home and recents but never the Back affordance, so in 2- or
# 3-button navigation a stray chevron stays on screen. Gesture navigation has no
# buttons to leave behind.
echo "==> Switching to gesture navigation (removes the leftover back button)"
adb shell cmd overlay enable com.android.internal.systemui.navbar.gestural || true

echo "==> Suppressing the one-time 'Viewing full screen' system overlay"
adb shell settings put secure immersive_mode_confirmations confirmed || true

# A reboot is the clean way to get a process that has the full storage view, and
# it doubles as a check that the device really does boot into the player.
echo "==> Rebooting into the kiosk"
adb reboot
sleep 8
adb wait-for-device
until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do sleep 3; done

cat <<'EOF'

Provisioned. The phone is now an iPod:
  - status bar, nav bar, recents and the power menu are gone
  - the lock screen is disabled, so waking lands straight on the player
  - it relaunches itself on boot

The factory reset wiped any music too. Load some with:
  ./scripts/push-music.sh /path/to/your/mp3s
then on the device: Music > Rescan Library.

To get out: Settings > Exit Kiosk on the device, or run scripts/exit-kiosk.sh.
EOF
