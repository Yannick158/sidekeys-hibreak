## SideKeys v1.2.1

Freely remap the two side keys of the **Bigme HiBreak Pro** — and the volume keys,
or any hardware key — separately for single press, double press and long press.

### New since 1.0
- **Volume Up / Down / Mute** and **Battery Saver** as actions
- **Battery Saver Quick Settings tile** (the one the stock HiBreak Pro is missing)
- **Charge alarm**: sound + vibration + notification at your chosen level so you
  can unplug — works on any device, no root, no Shizuku
- **One-tap accessibility enable** via Shizuku / permission (skips the
  "Allow restricted settings" dance after updates)
- **Battery Saver without Shizuku**: grant WRITE_SECURE_SETTINGS once, then the
  toggle works natively

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
