#!/usr/bin/env bash
# Copies a folder of MP3s onto the device.
#   ./scripts/push-music.sh ~/Music/my-albums
set -euo pipefail
cd "$(dirname "$0")/.."
source scripts/env.sh

SRC="${1:-}"
if [[ -z "$SRC" ]]; then
  echo "Usage: $0 /path/to/folder-of-mp3s" >&2
  exit 1
fi
SRC="${SRC%/}"
[[ -d "$SRC" ]] || { echo "Not a directory: $SRC" >&2; exit 1; }

# Guard against pointing this at a home directory or a filesystem root by mistake:
# adb push would happily copy the whole lot and fill the phone.
RESOLVED="$(cd "$SRC" && pwd -P)"
for forbidden in "$HOME" "/" "/Users" "/Volumes" "$HOME/Desktop" "$HOME/Documents" "$HOME/Downloads"; do
  if [[ "$RESOLVED" == "$(cd "$forbidden" 2>/dev/null && pwd -P || echo "__none__")" ]]; then
    echo "REFUSING to push '$RESOLVED' — that is a top-level folder, not a music folder." >&2
    echo "Point this at the album/playlist folder itself, e.g. ~/Music/my-albums" >&2
    exit 1
  fi
done

COUNT="$(find "$SRC" -iname '*.mp3' -type f | wc -l | tr -d ' ')"
SIZE="$(du -sh "$SRC" | cut -f1 | tr -d ' ')"
if [[ "$COUNT" -eq 0 ]]; then
  echo "No .mp3 files found under '$SRC'. Nothing to do." >&2
  exit 1
fi

echo "Source:  $RESOLVED"
echo "Tracks:  $COUNT mp3 files"
echo "Size:    $SIZE (whole folder, including any non-mp3 files)"

# Anything unusually large is worth a second look before it lands on a 64GB phone.
SIZE_MB="$(du -sm "$SRC" | cut -f1)"
if [[ "$SIZE_MB" -gt 8000 ]]; then
  echo
  read -r -p "That is over 8 GB. Type 'yes' to continue: " reply
  [[ "$reply" == "yes" ]] || { echo "Cancelled."; exit 1; }
fi

require_device
DEST="/sdcard/Music/$(basename "$RESOLVED")"
echo "==> Pushing to $DEST"
adb shell mkdir -p /sdcard/Music
adb push "$RESOLVED" /sdcard/Music/

echo "==> Now on device: $(adb shell "find /storage/emulated/0/Music -iname '*.mp3' 2>/dev/null | wc -l" | tr -d '\r') mp3 files"
echo "On the phone: Music > Rescan Library"
