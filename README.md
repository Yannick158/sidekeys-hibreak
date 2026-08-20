# SideKeys — button mapper for E-Ink phones

SideKeys lets you freely remap the hardware keys of your phone — the extra side
keys that E-Ink devices tend to have, the volume keys, or almost any other
hardware key — separately for **single press**, **double press** and
**long press** per key.

E-Ink phones usually ship with one or two extra side keys, but their stock
firmware only offers a fixed action list (page turn, screen refresh, screenshot,
…) and typically **cannot launch apps**. That is the gap SideKeys fills. Its
interface is built for E-Ink: pure black and white, no animations, no ghosting.

Developed and tested on the **Bigme HiBreak Pro** (Android 14). It should work on
any Android 8+ device whose keys reach apps as normal key events — reports from
other devices are welcome.

## Features

- **Launch Google Assistant**
- **Open Google Wallet** (with fallback to the app-link / Play Store)
- **Launch any app** (pick from all installed apps, with search)
- System actions: Home, Back, Recent apps, Notifications, Quick settings,
  Power menu, Lock screen, Screenshot
- Toggle flashlight, media controls (play/pause, next/previous),
  **volume up / down / mute**, toggle Do Not Disturb
- **Custom intent** (expert option): starts arbitrary activities or sends
  broadcasts — so you can wire up even Bigme-specific functions that are
  otherwise only reachable through the Bigme settings
- **Key debounce**: some side keys bounce in hardware and fire twice (a known
  issue on the HiBreak Pro, e.g. page-skipping in reader apps) — SideKeys filters
  ghost presses; the interval is adjustable
- E-ink optimized UI: pure black & white, no animations

### Battery extras

- **Charge alarm**: get a sound + vibration + notification when the battery
  reaches a level you pick while plugged in, so you can unplug to protect it.
  Works on any device — no root, no helper app.
- **Battery Saver toggle** — as a key action (needs a one-off
  firmwares that use the standard panel; see the device note below).
- **One-tap accessibility enable** so you don't have to redo the
  "Allow restricted settings" dance after every update.

The Battery Saver toggle needs one permission that no app can request at
runtime. There are two one-time routes, both local — pick either:

- **From the phone:** with [Shizuku](https://shizuku.rikka.app/) running, tap
  *Grant permanently via Shizuku* under **Battery & charge alarm**.
- **From a computer:** run this once with USB debugging on:

  ```bash
  adb shell pm grant com.sidekeys.hibreak android.permission.WRITE_SECURE_SETTINGS
  ```

Either way it stays granted until you uninstall, and works even when Shizuku is
not running. Without it, the action simply opens the Battery Saver settings page.
Everything else in the app works regardless.

> Note: a true hardware **charge limit** (stop charging at X%) is not possible on
> every device. On the Bigme HiBreak Pro it is confirmed impossible — the kernel
> exposes no writable charging-control node, so no app (even with root) can stop
> charging. The charge alarm is the honest, working alternative.

## Installation

1. Copy `SideKeys-release.apk` to the phone (or use `adb install`).
2. Allow installation from unknown sources, tap the APK and install.
3. Open the app. It shows the two steps Android requires for sideloaded apps:
   - **Step 1: Allow restricted settings** — opens the app info screen; there,
     menu (⋮) → *Allow restricted settings*. Without this Android keeps the
     switch in step 2 greyed out.
   - **Step 2: Enable accessibility service** — opens accessibility settings;
     switch on *SideKeys Button Mapper*.

   (Installing updates via `adb install -r` avoids the restricted-setting prompt
   entirely.)

```bash
adb install SideKeys-release.apk
```

## Usage

1. Tap **"+ Assign key"**.
2. **Press** the key you want to assign — a side key **or a volume key** — the app
   detects the keycode automatically.
3. Pick an action for single / double / long press (test instantly with ▶) and
   tap **Save**.

Good to know:

- If only the single press is assigned, the key reacts immediately. As soon as a
  double press is assigned, the single press waits for the double-press window
  (adjustable in Settings).
- Deleting a mapping restores the key's original firmware function — the app
  passes unmapped keys straight through, unchanged.
- Heads-up for **volume keys**: once you map a volume key, that key runs your
  action instead of changing the volume. Delete the mapping to get the volume
  function back.
- If the firmware grabs a key before it reaches apps, set that key to "None" in
  its own key settings so SideKeys receives it (see device notes below).

## Device notes (Bigme / MediaTek)

- **Keys stop working after you "close" the app?** Bigme's task manager
  (recent apps → swipe away / "close all") does a **force-stop**, which kills the
  accessibility service — and Android deliberately does not restart a
  force-stopped service until it is toggled again. Key Mapper is affected the
  same way. SideKeys therefore **hides itself from the recent-apps list by
  default** (Settings → "Hide from recent apps"), so there is nothing to swipe
  away; open it from the app drawer. If it does get killed and you granted the
  permission above, the start screen offers **"Restart service"**, which brings it
  back without a trip to the settings.
- **Important — set the Bigme key assignment to "None":** the Bigme firmware runs
  its own key handling in parallel with SideKeys. Symptom: after capturing a key
  the mapping screen only shows briefly and disappears, because the Bigme action
  (e.g. page turn / E Ink Center) interferes — long press works because usually
  no Bigme action sits there. Fix: in the Bigme settings under "Custom key" set
  **both Single Tap and Long Press to "None"** for both keys.
- **Looking for a Quick Settings tile?** There isn't one any more. Several e-ink firmwares — Bigme's among them — ship a modified SystemUI whose panel draws a fixed set of tiles and never reads the system tile list, so a tile from an app can never become visible there. Rather than ship a feature that silently does nothing, it was removed in 1.11.0. Map Battery Saver to a side key or a volume key instead — that works regardless of the panel, and takes one press instead of two.


- **Disable DuraSpeed:** the MediaTek "DuraSpeed" feature aggressively kills
  background apps and can hit the SideKeys service too. Turn it off via ADB:

  ```bash
  adb shell settings put global setting.duraspeed.enabled 0
  ```

  Also exempt SideKeys from battery optimization
  (Settings → Apps → SideKeys → Battery → "Don't optimize").
- The capture screen shows every detected keycode — it also works for headset or
  Bluetooth keys.
- Via **"Custom intent"** you can trigger Bigme-specific functions, e.g. an
  `Activity` start of the Bigme settings or (if documented by Bigme) e-ink
  refresh broadcasts.

## Building (for developers)

Requirements: JDK 17, Android SDK (Platform 34).

```bash
./gradlew assembleRelease
```

The APK then lives in `app/build/outputs/apk/release/`.

**Signing:** the release keystore lives **outside** the repo on purpose, so it
can never be published by accident. Set the environment variable
`SIDEKEYS_KEYSTORE_DIR` to the directory holding `sidekeys.jks` and
`keystore.properties` (template: [keystore.properties.example](keystore.properties.example)):

```bash
export SIDEKEYS_KEYSTORE_DIR=~/.android-keys/sidekeys
./gradlew assembleRelease
```

Without that variable the release build **fails on purpose** (prevents an
accidentally debug-signed "release"). For a local test build:

```bash
./gradlew assembleRelease -PallowDebugSigning
```

Create your own keystore:

```bash
keytool -genkeypair -keystore ~/.android-keys/sidekeys/sidekeys.jks \
  -alias sidekeys -keyalg RSA -keysize 4096 -validity 10000
```

**Verify authenticity:** official APKs are signed with the maintainer key.
Signing certificate fingerprint (SHA-256):

```
CE:1A:7F:AC:78:29:3F:ED:0C:B7:6A:48:7F:7C:09:FB:81:EA:44:89:AD:36:75:29:72:27:9C:CD:8B:34:F9:D3
```

Verify with: `apksigner verify --print-certs SideKeys-release.apk`

## Architecture

Kotlin · Jetpack Compose (Material 3) · MVVM with UiState/StateFlow ·
DataStore Preferences · kotlinx-serialization · AccessibilityService with
`FLAG_REQUEST_FILTER_KEY_EVENTS` (`canRequestFilterKeyEvents`).

```
app/src/main/java/com/sidekeys/hibreak/
├── MainActivity.kt              # Single activity, capture fallback
├── ui/SideKeysApp.kt            # Navigation (no animations)
├── core/
│   ├── model/                   # KeyMapping, KeyAction, Settings
│   ├── data/                    # DataStore repository
│   ├── common/                  # Keycode names, labels, service state
│   └── designsystem/            # E-ink theme + components
├── service/
│   ├── KeyInterceptorService.kt # AccessibilityService (key filter)
│   ├── KeyPressHandler.kt       # Single/double/long state machine
│   └── ActionExecutor.kt        # Runs all actions
└── feature/                     # home, capture, mapping, apppicker, settings
```

The app reads no screen content, has no internet permission and collects no data.
The UI ships in English (default) and German (`values-de`).

## License

[MIT](LICENSE)
