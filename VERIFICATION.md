# What is verified before a release, and what is not

Run `tools/preflight.sh` before every upload. It exits non-zero if anything
below fails.

## Checked automatically

| Check | Catches |
| --- | --- |
| Unit tests (19) | press-gesture state machine, per-app profile merge, mapping and app-list serialisation |
| Android Lint | leaked contexts, dead SDK checks, unused and untranslated resources |
| Release build | anything that stops compiling or packaging |
| Version name vs. last git tag | re-releasing a version number |
| Version code vs. `distribution/used-version-codes.txt` | *"Version code N has already been used"* from Play |
| Release notes length | the 500-character limit, counted in characters — an umlaut is two bytes but one character |
| Strings in both languages | a string added in English and forgotten in German, which crashes at runtime |
| Working tree | a keystore, APK, AAB or private file about to be committed |
| Artefact version | uploading a stale build from before the version bump |
| Signing fingerprint | a build users could not install over their existing one |

Record the version code after a **successful** Play upload:

```
echo <versionCode> >> distribution/used-version-codes.txt
```

## Not checked — no device or emulator here

Nothing below is exercised. It has only ever been verified by hand, on the
maintainer's phone or by a user reporting back.

- **Key interception itself.** That a mapped key fires its action, that
  "Block the key" swallows it, that "Let the app handle this key" forwards it.
  The state machine behind it is unit-tested; its connection to real hardware
  is not.
- **Anything screen-sized.** Layout, clipping, whether a button sits under the
  navigation bar. The Android 15 edge-to-edge bug reached users for exactly
  this reason.
- **The accessibility service in a live system** — being enabled, surviving a
  force-stop, restarting.
- **Gesture dispatch:** scrolling, edge taps, the e-ink refresh.
- **Anything needing elevated rights:** Battery Saver, the DuraSpeed guard,
  Shizuku paths.
- **Vendor firmware behaviour.** Which keys a device even delivers differs per
  model and cannot be simulated.

## Closing part of that gap

Installing an emulator image (`sdkmanager "system-images;android-34;google_apis;arm64-v8a"`,
roughly 1.5 GB) would allow instrumented tests that enable the accessibility
service over adb, inject key events with `adb shell input keyevent`, and take
screenshots to check layout. That would cover interception, blocking,
pass-through and clipping — the first two entries above.

It would still not cover vendor firmware, which is where most reported bugs
have come from.
