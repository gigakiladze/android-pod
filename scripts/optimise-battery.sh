#!/usr/bin/env bash
# Strips the phone back to what a music appliance actually needs.
#
# Everything here is reversible: `pm disable-user` is undone by `pm enable`, and
# --restore below does exactly that. Nothing is uninstalled.
#
#   ./scripts/optimise-battery.sh                 # disable unneeded apps
#   ./scripts/optimise-battery.sh --radios-off    # also turn off wifi/bluetooth/data
#   ./scripts/optimise-battery.sh --aggressive    # also disable Play Services (see note)
#   ./scripts/optimise-battery.sh --restore       # put everything back
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
require_device

# Safe to disable: none of these are reachable in kiosk mode, and the player does
# not depend on any of them. The Pixel launcher is deliberately NOT in this list —
# it is the fallback home app if you ever leave kiosk mode.
SAFE_PACKAGES=(
  com.android.vending                      # Play Store
  com.android.chrome
  com.google.android.youtube
  com.google.android.apps.photos
  com.google.android.gm                    # Gmail
  com.google.android.apps.maps
  com.google.android.apps.messaging
  com.google.android.apps.wellbeing
  com.google.android.googlequicksearchbox  # Google app / Assistant
  com.google.android.as                    # Android System Intelligence
  com.google.android.apps.tachyon          # Duo/Meet
  com.google.android.calendar
  com.google.android.videos
  com.google.android.apps.docs
  com.google.android.keep
)

# Play Services is the single biggest background drain, but more of the system
# leans on it than you would expect. Behind a flag, and easy to put back.
AGGRESSIVE_PACKAGES=(
  com.google.android.gms
  com.google.android.gsf
)

restore() {
  echo "==> Re-enabling everything"
  for pkg in "${SAFE_PACKAGES[@]}" "${AGGRESSIVE_PACKAGES[@]}"; do
    adb shell pm enable "$pkg" >/dev/null 2>&1 && echo "  enabled  $pkg" || true
  done
  echo "==> Radios back on"
  adb shell svc wifi enable || true
  adb shell svc data enable || true
  adb shell svc bluetooth enable || true
  echo "Done. Reboot the phone to be sure everything comes back cleanly."
}

if [[ "${1:-}" == "--restore" ]]; then
  restore
  exit 0
fi

# Takes package names as arguments; macOS ships bash 3.2, which has no namerefs.
disable_list() {
  for pkg in "$@"; do
    if adb shell pm list packages -e 2>/dev/null | grep -q "^package:$pkg\$"; then
      if adb shell pm disable-user --user 0 "$pkg" >/dev/null 2>&1; then
        echo "  disabled  $pkg"
      else
        echo "  SKIPPED   $pkg (system refused)"
      fi
    fi
  done
}

echo "==> Disabling apps a music player does not need"
disable_list "${SAFE_PACKAGES[@]}"

if [[ "${1:-}" == "--aggressive" ]]; then
  echo "==> Disabling Play Services (revert with --restore if anything misbehaves)"
  disable_list "${AGGRESSIVE_PACKAGES[@]}"
fi

if [[ "${1:-}" == "--radios-off" ]]; then
  echo "==> Turning off radios (USB/adb is unaffected)"
  adb shell svc wifi disable || true
  adb shell svc data disable || true
  adb shell svc bluetooth disable || true
fi

echo
echo "Enabled packages now: $(adb shell pm list packages -e 2>/dev/null | wc -l | tr -d ' ')"
echo "Reboot to clear the disabled processes out of memory:  adb reboot"
