## SideKeys v1.12.1

A button mapper for **E-Ink phones**: freely remap the extra side keys, the volume
keys or almost any hardware key — separately for single press, double press and
long press. Developed and tested on the Bigme HiBreak Pro.

### New in 1.12.1
- **Fixes a regression from 1.12.0.** On devices where every side key reports
  the same code (KEYCODE_UNKNOWN), 1.12.0 made each button resolve to its own
  scan code — which broke any mapping saved before that change, since it lived
  under the old shared code. Resolving now falls back to the raw key code when
  nothing is mapped to the precise one, so an existing "both buttons do the
  same thing" setup keeps working exactly as before, and assigning a button
  individually still overrides it for that button only.

### New in 1.12.0

All three from user reports, and two of them were the same misunderstanding
seen from opposite sides: "No action" never meant what people expected.

- **"Block the key (do nothing)"** — a new action. Leaving all three press
  types on *No action* means the key is unassigned, so it is passed through
  untouched and the foreground app still reacts to it. Some HiBreak side keys
  report as F1/F2, and browsers open a tab on those. This action swallows the
  key instead, without a haptic buzz, so it behaves like a dead key.
- **"Let the app handle this key"** — the opposite, and the fix for readers
  with their own page-turn keys. In an app profile, *No action* means "inherit
  the global mapping", so there was no way to say "don't intercept here". This
  overrides the global mapping and stops interception for that key. It applies
  to the whole key, not one press type: detecting a double or long press means
  holding the key back, so it cannot be forwarded instantly and gesture-checked
  at the same time.
- **Keys that report as "unknown" can now be told apart.** On a Bigme B7 both
  side keys arrive as key code 0, which made them one and the same key. The
  kernel scan code still differs per button, so it is used as the identity when
  the key code is unknown.

### New in 1.11.0
- **Works on Android 15 and 16.** The UI was drawing underneath the status and
  navigation bars, which on newer devices could push the Save button out of
  reach. Reported on a Viwoods AiPaper (Android 16); it affected every Android
  15+ device.
- **Key capture no longer stays silent.** Back, Home, Recents, Menu and Power
  were dropped without a word, so a reader whose page-turn buttons report those
  codes looked exactly like a device sending nothing at all. Capture now names
  every key it sees, Back / Recents / Menu can be assigned after a confirmation,
  and only Power and Home stay locked. If nothing arrives at all, the screen now
  says so and explains why.
- **Scroll distance per app.** An app profile can override the global scroll
  distance — a reading app usually wants a different step than a browser.
- **The Battery Saver Quick Settings tile is gone.** It never worked on the
  firmwares it was built for: their panel draws a fixed set of tiles and ignores
  the system tile list, so the tile could not become visible no matter what.
  Shipping a button that silently does nothing is worse than not shipping it.
  **Map Battery Saver to a key instead** — that always worked and is faster.

### New in 1.9.x
- **Adjustable scroll distance.** Scrolling used to jump a fixed ~half screen,
  which overshoots at small font sizes. Settings → *Scroll distance* now sets it
  anywhere from 10 % to 90 % of the screen.
- **Scrolling no longer flings.** The swipe now ends with the finger held still
  before it lifts, so the app stops where the gesture stops instead of coasting
  past it. This was the real reason a scroll went further than you asked.
- **Up-front disclosure, with a real choice.** On first launch SideKeys explains
  why it needs the accessibility service (Android offers no other way for an app
  to receive hardware key presses in the background), what it accesses (key
  events, plus the foreground app's package name only if you use per-app
  profiles), and that nothing leaves the device — it has no internet permission.
  You either agree or decline, and declining closes the app without enabling
  anything. Required by Google Play for apps that use the Accessibility API
  without being an accessibility tool.

First version submitted to Google Play. It remains free, open source, ad-free,
and installable as an APK from here.

### New in 1.8.0
- **Setup now follows the order Android actually requires.** For sideloaded apps
  the "Allow restricted settings" entry only appears *after* a blocked attempt,
  so the screen walks three steps — try, allow, switch on — and says that the
  refusal in step 1 is expected.
- **Targets Android 16 (API 36).**

### New in 1.5.x
- Guided setup for the accessibility service, replacing a one-tap button that
  could never work on a fresh install (superseded by the three-step flow in 1.8.0).
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
