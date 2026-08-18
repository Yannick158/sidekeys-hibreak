# Privacy Policy — SideKeys

_Last updated: 18 August 2026_

SideKeys is an open-source app that remaps the hardware keys of your Android
device. This policy explains exactly what the app does and does not do with your
data.

## Short version

**SideKeys collects nothing, stores everything locally, and has no internet
access.** The app does not declare the `INTERNET` permission, so it is
technically incapable of transmitting any data anywhere.

## What the app stores

All of the following is stored **only on your device**, in the app's private
storage, and is deleted when you uninstall the app:

- Your key mappings (key codes and the actions you assigned)
- The package names of apps you chose for "launch app" actions or per-app profiles
- Your settings (timings, debounce, charge alarm level)

None of this is uploaded, shared, or backed up to us. There is no account, no
analytics, no advertising, and no third-party SDK that collects data.

## Use of the Accessibility API

SideKeys uses Android's AccessibilityService API because it is the **only**
supported way for an app to receive hardware key presses system-wide and to
perform actions such as Home, Back, or scrolling.

The service is used strictly for this narrow purpose:

- **Key events** — to detect presses of the keys you configured. Keys you have
  not mapped are passed through unchanged.
- **Foreground app package name** — only when you use the "per-app profiles"
  feature, so the app knows which profile applies. Only the package name is
  evaluated.
- **Performing actions** — global actions (Home, Back, Recents, Screenshot,
  ...) and scroll gestures you assigned to a key.

The service does **not** read, log, store or transmit screen content, text you
type, passwords, or any other information from other apps.

## Optional: WRITE_SECURE_SETTINGS

Two optional convenience features (toggling Battery Saver and enabling the
accessibility service in one tap) require the `WRITE_SECURE_SETTINGS`
permission, which you grant yourself once from a computer via adb. It is used
solely to write two system settings on your own device (`low_power` and the
accessibility service list). No data leaves the device, and no third-party
helper app is involved. Without it, every other feature still works.

## Permissions

| Permission | Why |
|---|---|
| Accessibility service | Receive hardware key presses, perform assigned actions |
| `VIBRATE` | Haptic feedback on key press, charge alarm |
| `POST_NOTIFICATIONS` | Show the charge alarm notification |
| `ACCESS_NOTIFICATION_POLICY` | Toggle Do Not Disturb, if you assign that action |

## Children

SideKeys is a utility app and is not directed at children. It collects no
personal data from anyone, including children.

## Changes

If this policy changes, the updated version will be published in this
repository, with the date above updated.

## Contact

Questions or concerns: please open an issue at
<https://github.com/Yannick158/sidekeys-hibreak/issues>.
