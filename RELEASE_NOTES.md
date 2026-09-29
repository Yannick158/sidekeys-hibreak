## SideKeys v1.20.1

A button mapper for **E-Ink phones**: freely remap the extra side keys, the volume
keys or almost any hardware key — separately for single press, double press and
long press. Developed and tested on the Bigme HiBreak Pro.

### New in 1.20.1

- **A held volume key keeps stepping.** Mapping long press to Volume up or
  Volume down used to change the volume exactly once, however long you held it,
  which reads as a broken key. It now repeats every 120 ms until you let go,
  close to the system's own volume repeat. Reported by a user who expected it to
  behave like holding the built-in key.

  Only volume repeats. A toggle would flip back and forth, an app launch would
  fire over and over, and a repeating scroll would redraw the e-ink panel at
  every step — so everything else still runs once per hold. The haptic buzz also
  fires only on the first step, otherwise a held key would rattle.

  One limit: on devices whose firmware never reports the volume keys as key
  events, SideKeys recovers the press from the volume change itself. That route
  cannot tell a held key from our own repeat, so there a hold still gives a
  single step.

### New in 1.20.0

- **Vibration strength is now yours to pick.** Light, Medium or Strong, in
  Settings under the haptics switch; tapping a level buzzes at that level, so
  you choose by feel rather than by guessing. Asked for by a user whose Do Not
  Disturb shortcut he could barely feel.

  The stronger levels are longer as well as harder, which is the point: a lot of
  e-ink phones cannot vary vibration intensity at all, and on those, duration is
  the only thing that makes a buzz feel stronger. Light is byte-for-byte the old
  pulse and is the default, so an existing install feels exactly as it did.

- **Confirm actions on screen.** A new switch, off by default. With it on, Do Not
  Disturb, the flashlight, the media controls and custom broadcasts each say what
  they just did. It stays deliberately quiet for scrolling, page turns and app
  launches — you can already see those happen, and a message on every page turn
  would refresh the e-ink panel every time.

- **The app is smaller and faster to install.** R8 now shrinks and optimises the
  release build, which Google Play also measures: the download drops from about
  11 MB to under 2 MB.

### New in 1.19.1

- **The charge alarm no longer breaks through Do Not Disturb.** It used to
  vibrate and play its sound directly, and neither is a notification — so Do Not
  Disturb never applied, and the channel's "override Do Not Disturb" switch, the
  one control a user would look for, had no effect at all. Reported by a tester
  whose phone rang in the middle of the night.

  Both now go through the notification channel, where DND applies. That switch
  is the real control: leave it off and DND silences the alarm, turn it on and
  it breaks through by your own choice. The alarm also follows your ringer mode
  now, so it stays quiet on Mute.

  Two consequences worth knowing. The channel had to be recreated (vibration
  cannot be added to an existing one), so custom channel settings return to
  their defaults once — a lowered importance is carried over, so an alarm you
  had quietened stays quiet. And since everything now goes through a
  notification, the alarm needs notification permission on Android 13+; the
  charge screen says so and offers a way in if notifications are switched off.

### New in 1.19.0

- **A last-resort route for volume keys the firmware fully swallows.** Some
  vendors don't even hand the press to the audio system — they just change the
  volume themselves, and both the key filter and the MediaSession route stay
  silent. The new second stage observes exactly that: it detects the volume
  change, silently undoes it, and treats it as the key press it was. Nothing is
  intercepted, so there is nothing for the firmware to block. Feeds the same
  gesture machine, so single, double and long press all work.
- **Deliberately staged, each stage its own opt-in.** The observer only appears
  once the audio route is on, and stays off by default: on a phone whose keys
  arrive normally it would add nothing but side effects (dragging the volume
  slider by hand could read as a key press). The settings text says when each
  stage is needed — and when it is not.
- Guards built in: echoes of SideKeys' own volume writes are ignored, active
  media playback is never hijacked, and the volume is kept one step away from
  its limits so both directions always produce a detectable change.

### New in 1.18.0

- **Volume keys through the audio route** — for devices whose firmware swallows
  the volume keys before any app can see them (Viwoods among them). A new
  opt-in switch in Settings makes SideKeys receive the press through the audio
  system instead, the same mechanism Tasker uses for its volume triggers. And
  not just single presses: the release is reconstructed from tick timing, so
  **single, double and long press all work**. Leave the single press unassigned
  and it keeps changing the volume — one press is volume, a double press is
  whatever you want. Unassigned keys behave completely normally; while another
  app is actively playing media, that app gets the keys. Covered by six new
  unit tests that pin down exactly these semantics.
- **Phantom key events are filtered during capture.** On a Bigme B7 Pro one
  press of a page-turn key emits two events: the key configured in system
  settings and a raw twin (scan code 143) that is identical for both keys.
  Capture used to grab whichever came first — often the twin, which cannot
  tell the keys apart. It now waits 120 ms for a proper key code and prefers
  it, so the two keys stay separable.
- **Capture shows what it is doing.** A pulsing "waiting for a key" indicator,
  a hint after 1.5 s to hold the key rather than tap it (some devices only
  report a held key), and the raw key/scan codes of every press, with a pause
  so they can actually be read.

### New in 1.16.3

- **Keys that report no proper code are handled more reliably.** The identity of
  a key is now decided when it goes down and kept until it comes up. It used to
  be recomputed for every event, and a device does not have to report the scan
  code on every event of one press — when it did not, the release was filed
  under a different key, so the press never completed and the key silently did
  nothing.
- **Key capture shows the raw numbers** it received, and pauses so they can be
  read. When two keys look identical this is the only thing that says whether
  the device distinguishes them at all, and it can be read off the screen and
  reported without any tooling.
- **"Enabled but not running" is detected and explained.** Reinstalling can
  leave SideKeys listed among the enabled accessibility services while nothing
  is actually running — most often after moving between the Play build and the
  APK here, which forces an uninstall because the two are signed differently.
  The switch in system settings then already looks on, so the old setup steps
  were telling people to enable something that appeared enabled. The main screen
  now says what is wrong: switch it off and on again.
- **DuraSpeed handling rebuilt on much better information**, thanks to a user's
  testing and decompilation of the service
  (https://www.reddit.com/r/Bigme/s/M8tCfoal21):
  - The value is nudged through **2** before 0, not 1 — some firmwares use 1 as
    their own "enabled" value, so 1 → 0 may not register as a change.
  - **Settings.System** is what the service reads at startup, so that is what
    makes the change survive a reboot. Global only takes effect immediately.
  - Status now comes from `dumpsys duraspeed status` rather than the stored
    value, which can read 0 while DuraSpeed is running. Without a shell to ask
    with, the app says it does not know instead of claiming it is off.

### New in 1.15.0

- **Pick several apps at once.** The app picker now has checkboxes and opens
  with your current list already ticked, so the same screen adds and removes.
  Building a list of ten apps no longer means ten round trips through the menu.
  Selection order is kept, since it decides which app ends up in front.
- **Keep DuraSpeed off.** MediaTek's DuraSpeed stops background apps, this
  service among them, which is why keys silently stop working on some devices.
  Settings can now hold it off — and because the value comes back on its own on
  some firmwares, it is watched rather than written once. Shown only on devices
  that actually have DuraSpeed. Disabling the DuraSpeed app itself is a red
  herring: it only hides the notification.
- **"Let the app handle this key" now explains itself** in the mapping screen.
  It applies to the whole key rather than one press type, which is worth saying
  where the choice is made.
- Fixes: the multi-select picker showed package names instead of app names for
  anything scrolled out of view by the search; the DuraSpeed watcher could chase
  its own writes; the watcher was not unregistered when the service stopped.
- The repository now carries a Gradle wrapper, so the release workflow can
  actually run, and a fresh clone builds without the private signing key.

### New in 1.14.0

All three from user requests.

- **Launch several apps with one key press.** Pick "Launch several apps", choose
  an app, then pick the same action again to add the next — the list grows
  instead of being replaced. Launches are staggered by 350 ms on purpose: fired
  at once, Android folds them into a single transition and only the last app
  actually starts. The last app in the list ends up in front, the earlier ones
  stay warm behind it — useful on devices whose task killers keep evicting
  background apps.
- **Tap left/right screen edge** — page turns without anything extra. Most
  readers (Storytel included) flip on an edge tap, and a synthetic tap only
  needs the gesture capability the service already uses for scrolling. No
  Shizuku, no new permission.
- **D-pad left/right** — for apps that only listen to real arrow keys. Android
  offers no public API for injecting key events (the permission is reserved for
  system apps), so these two actions require Shizuku running. Try the edge-tap
  actions first; they cover the common case without it.

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
