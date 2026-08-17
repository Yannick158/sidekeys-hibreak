# SideKeys — button mapper for the Bigme HiBreak Pro

SideKeys lets you freely remap the two extra hardware keys on the side of the
Bigme HiBreak Pro — and any other hardware key, **including the volume keys** —
separately for **single press**, **double press** and **long press** per key.

The HiBreak Pro runs Android 14. Its stock settings ("Custom key") only offer a
fixed action list (Home, Back, Screenshot, App switcher, Clear cache, Full
refresh, E Ink Center, Previous/Next page, Light) and in particular **cannot
launch apps** — exactly the gap SideKeys fills.

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
- **Key debounce**: the HiBreak Pro side keys bounce in hardware (a known issue,
  e.g. page-skipping in reader apps) — SideKeys filters ghost presses; the
  interval is adjustable
- E-ink optimized UI: pure black & white, no animations

### Battery extras

- **Charge alarm**: get a sound + vibration + notification when the battery
  reaches a level you pick while plugged in, so you can unplug to protect it.
  Works on any device — no root, no Shizuku.
- **Battery Saver toggle** — as a key action and as a **Quick Settings tile**
  (the tile the HiBreak Pro's stock quick settings is missing). Needs
  [Shizuku](https://shizuku.rikka.app/) once; grant WRITE_SECURE_SETTINGS in the
  app and it then works natively, even without Shizuku running.
- **One-tap accessibility enable** (with Shizuku / the granted permission) so you
  don't have to redo the "Allow restricted settings" dance after every update.

> Note: a true hardware **charge limit** (stop charging at X%) is **not possible
> on the Bigme HiBreak Pro** — its kernel exposes no writable charging-control
> node, so no app (even with root) can stop charging. The charge alarm is the
> honest, working alternative.

## Installation

1. Copy `SideKeys-release.apk` to the phone (or use `adb install`).
2. Allow installation from unknown sources, tap the APK and install.
3. Open the app → tap **"Enable accessibility service"** → in settings enable
   **"SideKeys Button Mapper"**.
   (Without this step Android cannot deliver hardware keys to the app.)
4. **Android 13/14, "Restricted setting":** for sideloaded apps Android blocks
   the accessibility toggle at first. Fix: open SideKeys app info →
   menu (⋮) top right → **"Allow restricted settings"** → then enable the
   accessibility service. (With Shizuku set up, the app's **"Enable in one tap"**
   button skips this on future updates. Installing updates via `adb install -r`
   or Shizuku avoids the prompt entirely.)

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
- Deleting a mapping restores the key's original (Bigme) function — the app
  passes unmapped keys straight through, unchanged.
- Heads-up for **volume keys**: once you map a volume key, that key runs your
  action instead of changing the volume. Delete the mapping to get the volume
  function back.
- If the Bigme firmware already grabs a key before it reaches apps, set that key
  to "None" in the Bigme settings so SideKeys receives it.

## Notes on the Bigme firmware

- **Important — set the Bigme key assignment to "None":** the Bigme firmware runs
  its own key handling in parallel with SideKeys. Symptom: after capturing a key
  the mapping screen only shows briefly and disappears, because the Bigme action
  (e.g. page turn / E Ink Center) interferes — long press works because usually
  no Bigme action sits there. Fix: in the Bigme settings under "Custom key" set
  **both Single Tap and Long Press to "None"** for both keys.
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
