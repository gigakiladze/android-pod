#!/usr/bin/env bash
# Removes music from the device. Defaults to the sine-tone test set only.
#   ./scripts/clear-music.sh           # delete just /sdcard/Music/test
#   ./scripts/clear-music.sh --all     # delete everything under /sdcard/Music
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh
require_device

if [[ "${1:-}" == "--all" ]]; then
  TARGET="/sdcard/Music"
  echo "About to delete EVERYTHING under $TARGET:"
else
  TARGET="/sdcard/Music/test"
  echo "About to delete the test tracks in $TARGET:"
fi

adb shell "ls -1 '$TARGET' 2>/dev/null" || { echo "(nothing there)"; exit 0; }

read -r -p "Type 'yes' to confirm: " reply
[[ "$reply" == "yes" ]] || { echo "Cancelled."; exit 1; }

if [[ "${1:-}" == "--all" ]]; then
  adb shell "rm -rf '$TARGET'/*"
else
  adb shell "rm -rf '$TARGET'"
fi

echo "Deleted. Remaining mp3s on device:"
adb shell "find /storage/emulated/0/Music -iname '*.mp3' 2>/dev/null | wc -l"
echo "On the device: Music > Rescan Library to refresh the list."
