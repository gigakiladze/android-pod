# android-pod

Turns a Pixel 3 into an iPod. Not an app you open — the phone boots into it, and
there is nothing else on the device.

A Kotlin/Compose home launcher running in device-owner lock task mode: full-screen
iPod classic UI with a working touch click wheel, a filesystem scan for MP3s, and
no system UI of any kind.

---

## What it does

| Feature | How |
| --- | --- |
| Boots straight into the player | Registered as `CATEGORY_HOME` + `BOOT_COMPLETED` receiver |
| No status or nav bar | Device-owner `setLockTaskFeatures(LOCK_TASK_FEATURE_NONE)` |
| No power menu, no recents | Same lock-task feature mask |
| Wake straight to the player | `setKeyguardDisabled(true)` + `setShowWhenLocked` |
| Music keeps playing when asleep | ExoPlayer in a `MediaSessionService` foreground service |
| Finds MP3s | Recursive walk of shared storage, ID3 tags via `MediaMetadataRetriever` |
| Play / pause / next / prev | Click wheel zones and the centre button |
| Brightness | Wheel-adjusted, applied per-window (no system permission needed) |
| Light / dark theme | Settings › Theme |
| Volume keys, no Android panel | Keys consumed in `onKeyDown`; `adjustStreamVolume(..., flags = 0)` |
| Screen never sleeps | `FLAG_KEEP_SCREEN_ON` + `ScreenDimmer` fades to minimum after 45s idle |
| Idle screen | Near-black OLED view with slowly drifting text (power + burn-in) |

## The click wheel

```
        MENU
         ···
   ◀◀  ( OK )  ▶▶
         ···
         ▶❙❙
```

- **Drag around the ring** — scrolls the list. Clockwise goes down. Haptic tick every
  18°, so about 20 ticks per revolution.
- **Tap a cardinal zone** — MENU (back), ◀◀ (previous), ▶▶ (next), ▶❙❙ (play/pause).
- **Tap the centre** — select.
- On **Now Playing**, the ring scrubs ±5s per tick and the centre toggles playback.
- On **Brightness**, the ring adjusts the screen.

## Setup

You need the Pixel 3 plugged in with USB debugging on, and the phone **factory reset
with no accounts added** — device owner cannot be granted otherwise.

```bash
# 1. Remove the Google account first (Settings > Passwords & accounts > Remove),
#    so Factory Reset Protection cannot lock you out.
# 2. Settings > System > Reset options > Erase all data
# 3. On first boot skip EVERY sign-in. One account is enough to block provisioning.
# 4. Settings > About phone > tap "Build number" 7 times
# 5. Settings > System > Developer options > USB debugging: ON
# 6. Plug in, accept the debugging prompt, then:

./scripts/provision-kiosk.sh
```

That installs, grants storage, sets device owner, makes the app home, switches the
phone to gesture navigation, and reboots into the kiosk.

## Getting music on and off

macOS has no native MTP support and Android File Transfer is discontinued, so the
USB "File Transfer" route is a dead end — and in kiosk mode there is no status bar
to change the USB mode from anyway. Use adb over the same cable:

```bash
./scripts/push-music.sh ~/Music/my-albums   # copy a folder to /sdcard/Music
./scripts/clear-music.sh                    # delete the sine-tone test set
./scripts/clear-music.sh --all              # wipe /sdcard/Music entirely
```

Then on the device: **Music › Rescan Library**. The row shows the current track
count, so it is obvious whether the scan picked anything up.

### Day-to-day

```bash
./scripts/build-install.sh    # rebuild and reinstall after a code change
./scripts/exit-kiosk.sh       # recovery: drop device owner, restore normal launcher
```

## Battery

The whole of Android is still running underneath — the player is one process among
~230 packages. Two things dominate drain, and neither is the app:

| Component | Rough draw |
| --- | --- |
| OLED at 60%, full interface | ~700–900 mW |
| OLED at minimum, full interface | ~120–200 mW |
| OLED showing the near-black idle screen | ~40–80 mW |
| MP3 decode + wired output | ~80–150 mW |
| Stock background (Play Services, radios) | ~60–160 mW |
| Background with bloat disabled + radios off | ~20–50 mW |

The pack is 2954 mAh at 3.85 V ≈ 11.4 Wh, so with music playing and the screen left
idle that is roughly **30 h as shipped**, and roughly **50 h** with the idle screen
plus `optimise-battery.sh`. These are estimates from component figures, not measured
on this device.

Because the panel is OLED, two things follow:

- **Dark theme costs materially less than light theme.** A black pixel is an unlit
  pixel. The light theme lights the whole panel.
- **The idle screen matters more than dimming.** Dimming the menus still lights every
  pixel; showing near-black switches most of them off.

```bash
./scripts/optimise-battery.sh               # disable apps a music player never uses
./scripts/optimise-battery.sh --radios-off  # also wifi / bluetooth / mobile data
./scripts/optimise-battery.sh --aggressive  # also Play Services
./scripts/optimise-battery.sh --restore     # undo all of it
```

Nothing is uninstalled — `pm disable-user` is reversible.

## Getting back out

There is **no Exit Kiosk menu on the device** — it was removed deliberately, because
the appliance should not offer a way out to whoever is holding it.

That makes the recovery path below the *only* one, so it is worth knowing:

> `adb shell dpm remove-active-admin` fails on a production device owner with
> `SecurityException: Attempt to remove non-test admin`. Only the owning app can
> relinquish the role, which it does via an intent.

```bash
./scripts/exit-kiosk.sh          # leave kiosk until restart, stay device owner
./scripts/exit-kiosk.sh --full   # relinquish ownership, restore launcher, uninstall
```

Which is just:

```bash
adb shell am start -n com.gigakiladze.pod/.MainActivity \
    -a com.gigakiladze.pod.EXIT_KIOSK --ez clear_owner true
```

**The caveat:** this needs the app to start in order to receive the intent. If a
future build ever crashes on launch, there is no way back except a factory reset
(boot to recovery → erase). Keep USB debugging enabled, and test changes on the
emulator (`pod_pixel3` AVD) before installing them here.

## Building from a fresh clone

`local.properties` is deliberately not committed, so point Gradle at your SDK with
either an `ANDROID_HOME` environment variable or your own `local.properties`:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"   # or wherever yours lives
./gradlew :app:assembleDebug
```

Needs JDK 17. `scripts/env.sh` defaults `JAVA_HOME` to the Homebrew path on macOS;
override it if yours differs.

> **The release build is signed with the debug key**, so `assembleRelease` produces
> something installable without a keystore. That is fine for a personal appliance
> and wrong for anything distributed — swap in a real signing config first.

## Layout

```
app/src/main/java/com/gigakiladze/pod/
├── MainActivity.kt          the only Activity; also the home launcher
├── PodApplication.kt        process-scoped library + player
├── PodSettings.kt           theme and brightness, persisted
├── kiosk/
│   ├── KioskController.kt   lock task, keyguard, immersive, escape hatch
│   ├── PodDeviceAdminReceiver.kt
│   └── BootReceiver.kt
├── media/
│   ├── MusicScanner.kt      recursive .mp3 walk
│   ├── PodPlaybackService.kt  ExoPlayer + MediaSession
│   ├── PlayerConnection.kt  MediaController bridge to Compose
│   ├── MusicLibrary.kt      scan state, artist/album grouping
│   ├── AlbumArt.kt          lazy cover decoding with an LRU cache
│   └── Track.kt
└── ui/
    ├── ClickWheel.kt        the wheel: gestures + Canvas drawing
    ├── PodRoot.kt           navigation, wheel event routing, layout
    ├── PodComponents.kt     header, menus, Now Playing, brightness
    ├── PodDestination.kt    menu hierarchy and back stack
    └── PodTheme.kt          light/dark palettes
```

## Verified on an emulator

Everything below was exercised on a Pixel 3 AVD (Android 12, API 31) before any real
hardware was touched:

- 7 tagged MP3s found by the filesystem walk, with correct titles, artists, durations
  and embedded cover art
- playback, auto-advance to the next track, and the Now Playing screen
- rotational wheel scrolling, MENU/select zone taps
- light/dark theme toggle, persisted across reinstall
- `dpm set-device-owner` → `mLockTaskModeState=LOCKED`, no status or nav bar
- both escape hatches, including relinquishing device ownership

## Platform limits worth knowing

- **A short power-button press cannot be intercepted.** No Android app sees it; the
  system consumes it to sleep the screen. What this app does instead is keep audio
  running in the service and wake straight back into the player with no lock screen.
  The long-press power *menu* is suppressed, because that one is a lock-task feature.
- **Without device owner**, the app still works as a launcher in immersive mode, but a
  swipe can transiently reveal the bars. Useful while developing on an un-wiped phone.
- **Brightness is per-window**, not a global system setting. In kiosk mode nothing else
  is ever on screen, so the effect is the same and it needs no special permission.
- **`MANAGE_EXTERNAL_STORAGE` only applies to processes started after it is granted.**
  Grant it to a running app and that process keeps its restricted view of storage and
  the scanner silently finds nothing. This is why `provision-kiosk.sh` grants storage
  *before* `set-device-owner` (which starts the app via the admin broadcast) and
  reboots at the end.
- **Lock task never hides the Back affordance.** `LOCK_TASK_FEATURE_NONE` removes home,
  recents, notifications and the power menu, but a back button remains in 2- and
  3-button navigation. Switching the phone to gesture navigation is what actually
  clears the last strip of system UI.
- **`adb shell force-stop` does not work on the locked app**, so use a reboot (or the
  EXIT_KIOSK intent) when you need a clean restart of the process.
