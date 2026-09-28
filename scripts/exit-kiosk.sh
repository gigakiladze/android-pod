#!/usr/bin/env bash
# Recovery hatch, driven from the Mac.
#
# Note: `adb shell dpm remove-active-admin` does NOT work on a production device
# owner (SecurityException: non-test admin). Only the owning app can relinquish
# the role, so this asks the app to do it via its EXIT_KIOSK intent.
#
#   ./scripts/exit-kiosk.sh            # leave kiosk, stay device owner
#   ./scripts/exit-kiosk.sh --full     # also relinquish device owner, then uninstall
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
require_device

FULL=false
[[ "${1:-}" == "--full" ]] && FULL=true

echo "==> Asking the app to leave kiosk mode (clear_owner=$FULL)"
adb shell am start -n "$HOME_ACTIVITY" \
  -a com.gigakiladze.pod.EXIT_KIOSK --ez clear_owner "$FULL" >/dev/null

sleep 3
STATE="$(adb shell dumpsys activity | grep -o 'mLockTaskModeState=[A-Z]*' | head -1 || true)"
echo "==> Lock task state: ${STATE:-unknown}"

if [[ "$FULL" != "true" ]]; then
  echo
  echo "Kiosk released until the app restarts. It stays device owner, so a reboot"
  echo "brings the iPod back. Re-run with --full to relinquish ownership for good."
  exit 0
fi

echo "==> Owners remaining"
adb shell dpm list-owners || true

echo "==> Restoring the stock launcher"
adb shell cmd package set-home-activity \
  com.google.android.apps.nexuslauncher/.NexusLauncherActivity || true

echo "==> Uninstalling"
if adb uninstall "$PKG"; then
  echo "Done. The phone is a normal Android device again."
else
  cat <<'EOF'

Uninstall failed, which means device ownership was not released. Use the
on-device path: wheel to Settings > Exit Kiosk > Remove Device Owner.
If the screen is unresponsive, boot to recovery and factory reset.
EOF
fi
