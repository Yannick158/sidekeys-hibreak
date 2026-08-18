# Security Policy

## Reporting a vulnerability

If you find a security problem in SideKeys, please report it privately first:

- Use GitHub's **[Report a vulnerability](https://github.com/Yannick158/sidekeys-hibreak/security/advisories/new)** form, or
- open a regular issue **only** if the problem is not sensitive.

Please include the app version, your Android version and device, and the steps to
reproduce. I aim to acknowledge reports within 14 days. As this is an unpaid
hobby project maintained by a single person in their spare time, please do not
expect commercial response times.

## Scope

SideKeys runs entirely on the device. It has **no `INTERNET` permission**, no
server component, no account system and no analytics, so there is no backend to
attack and no user data in transit.

The parts most worth scrutiny are:

- the accessibility service that receives hardware key events
- the "custom intent" action, which starts activities or sends broadcasts that
  the user configured
- the optional use of `WRITE_SECURE_SETTINGS`, granted by the user via adb or
  Shizuku, used only to write `low_power` and the accessibility service list

## Verifying an official build

APKs published under [Releases](https://github.com/Yannick158/sidekeys-hibreak/releases)
are signed with this certificate:

```
SHA-256: CE:1A:7F:AC:78:29:3F:ED:0C:B7:6A:48:7F:7C:09:FB:81:EA:44:89:AD:36:75:29:72:27:9C:CD:8B:34:F9:D3
```

Check any APK with `apksigner verify --print-certs SideKeys-release.apk`.
A build from Google Play has a different signature, because Play re-signs apps
with its own key (Play App Signing).
