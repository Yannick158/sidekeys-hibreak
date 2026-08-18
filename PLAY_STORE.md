# Publishing SideKeys to Google Play — checklist

Everything in this repo is prepared. What remains needs **your** Google account,
identity and payment details, so it cannot be automated.

---

## ⚠️ Read this first: real rejection risks

SideKeys is an **AccessibilityService app**, which Google reviews strictly.
Two things are genuinely risky:

### 1. `WRITE_SECURE_SETTINGS` — recommended to remove for the Play build

The app declares this permission; the user grants it themselves via adb. It is a
`signature|privileged` permission that a normal app can never hold through the
Play install flow. To a Play reviewer this can look like circumventing the
permission model, and it is the single most likely reason for rejection.

**Affected features if removed:** the Battery Saver key action and the quick tile
would only open the Battery Saver settings page, and "Enable in one tap" would
fall back to the manual accessibility settings route. Everything else is
unaffected.

### 2. Accessibility API declaration

Google requires apps that use the Accessibility API without being an
accessibility tool to declare a *narrow, clearly understood purpose* and show a
**prominent disclosure** ([policy](https://support.google.com/googleplay/android-developer/answer/10964491)).
Key Mapper and Button Mapper are on Play, so this is achievable — but you must:

- Fill in the **Accessibility API declaration** in Play Console → Policy → App content
- State the purpose as: *"Receives hardware key presses so the user can assign
  their own actions to physical buttons."*
- Confirm the app does not read screen content (it does not — see [PRIVACY.md](PRIVACY.md))

> If you would rather avoid all of this, GitHub Releases (current setup) or
> F-Droid are perfectly good distribution channels for a device-specific tool
> like this one.

---

## Step 1 — Google Play Developer account (only you)

1. Register at <https://play.google.com/console> — **one-time 25 USD fee**
2. Complete identity verification (can take a few days)

## Step 2 — Create the app and upload the first release manually

The GitHub action can only **update** an app that already exists. The very first
upload must be done by hand:

1. Play Console → **Create app** → package name `com.sidekeys.hibreak`
2. Build the bundle locally:
   ```bash
   export SIDEKEYS_KEYSTORE_DIR=~/.android-keys/sidekeys
   ./gradlew bundleRelease
   ```
3. Upload `app/build/outputs/bundle/release/app-release.aab` to the **Internal
   testing** track
4. Complete: store listing, screenshots, content rating questionnaire,
   **Data safety** form (declare: no data collected), privacy policy URL

**Privacy policy URL** — use the raw link to [PRIVACY.md](PRIVACY.md), e.g.
`https://github.com/Yannick158/sidekeys-hibreak/blob/main/PRIVACY.md`

## Step 3 — Play App Signing

Accept **Play App Signing** when prompted. Google then holds the app signing key
and your local keystore becomes the *upload* key only. Note: APKs from Play will
have a **different signature** than the GitHub-release APKs, so users cannot
update across the two channels.

## Step 4 — Service account for automated uploads

1. Play Console → **Users and permissions** → invite a service account
2. Google Cloud → IAM → **Create service account**, grant it **no project roles**
3. Create a **JSON key**
4. Back in Play Console, grant it access **only to this app**, with the
   *Release manager* permission

> Do **not** grant "Owner" on the Cloud project — that recommendation circulates
> widely and is [excessive and unsafe](https://github.com/r0adkll/upload-google-play/issues/224).

## Step 5 — GitHub secrets

Repository → Settings → Secrets and variables → Actions:

| Secret | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the service account JSON |
| `SIDEKEYS_KEYSTORE_BASE64` | `base64 -i ~/.android-keys/sidekeys/sidekeys.jks` |
| `SIDEKEYS_STORE_PASSWORD` | your keystore password |
| `SIDEKEYS_KEY_PASSWORD` | your key password |

Also create an **environment** named `play-store` (Settings → Environments) and
add yourself as a required reviewer, so no upload happens without approval.

## Step 6 — Automated releases

Afterwards: Actions → **Play Store release** → *Run workflow*, pick the track.
The workflow ([.github/workflows/play-release.yml](.github/workflows/play-release.yml))
runs the unit tests, builds a signed AAB and uploads it.

Security properties of the workflow, deliberately:

- Manual trigger only (`workflow_dispatch`) — **never** on `pull_request`, so a
  fork PR can never reach the secrets
- All third-party actions **pinned to commit SHAs**, not moving tags
- `permissions: contents: read` — minimal token scope
- Keystore is shredded from the runner in an `always()` step

---

## Release notes

Per-language notes for each upload live in `distribution/whatsnew/`
(`whatsnew-en-US`, `whatsnew-de-DE`). Update them before running the workflow.
