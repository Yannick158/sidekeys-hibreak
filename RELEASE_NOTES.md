## SideKeys v1.5.2

Freely remap the two side keys of the **Bigme HiBreak Pro** — and the volume keys,
or any hardware key — separately for single press, double press and long press.

### New in 1.5.x
- **Enabling the service is now two clear steps** — "Allow restricted settings"
  (opens the app info screen) and "Enable accessibility service", each with a
  note saying what to tap there. The old one-tap button is gone: it could never
  work on a fresh install.
- **"Restart service"** for when Bigme's task manager force-stops the app. Only
  shown when the app actually holds the rights to do it, and it verifies the
  result instead of claiming success.
- **Battery Saver toggle: two optional setup routes** — via Shizuku from the
  phone, or `adb pm grant` from a computer. Android offers no API for this to
  normal apps, and the alternative (clicking the toggle through the
  accessibility tree) would require the ability to read screen content, which
  this app deliberately does not have.

### New in 1.4.0
- **Scroll up / down in any app** as key actions
- **Launch a specific app screen (activity)** — pick from a list or type the
  component by hand
- **Per-app key profiles**: different actions while a chosen app is in the
  foreground; empty slots fall back to the global mapping
- **E-ink full refresh** (experimental)
- **`adb pm grant` as a setup route** for the features that need elevated
  rights, shown ready to copy inside the app (see 1.5.x for the Shizuku route).

### Earlier
- Keys survive Bigme's task manager: SideKeys hides itself from recent apps by
  default, and "Enable in one tap" restarts a killed service
- **Volume Up / Down / Mute** and **Battery Saver** as actions
- **Battery Saver Quick Settings tile** (the one the stock HiBreak Pro is missing)
- **Charge alarm**: sound + vibration + notification at your chosen level so you
  can unplug — works on any device, no root
- **One-tap accessibility enable** (skips the "Allow restricted settings" dance
  after updates)

### Features
- Launch Google Assistant, open Google Wallet, launch any app
- System actions: Home, Back, Recent apps, Notifications, Quick settings, Power menu, Lock screen, Screenshot
- Flashlight, media controls, volume up/down/mute, Do Not Disturb, custom intent (expert)
- E-ink optimized (black & white, no animations), hardware key debounce, runtime key capture
- UI in English (default) and German

### Not possible on the HiBreak Pro
A true hardware charge limit (stop charging at X%) can't be done on this device —
its kernel exposes no writable charging-control node, so no app (even with root)
can stop charging. The charge alarm is the working alternative.

### Verify authenticity
Certificate fingerprint (SHA-256):
`CE:1A:7F:AC:78:29:3F:ED:0C:B7:6A:48:7F:7C:09:FB:81:EA:44:89:AD:36:75:29:72:27:9C:CD:8B:34:F9:D3`

Minimum Android: 8.0 (API 26) · Tested for Android 14 (HiBreak Pro)
