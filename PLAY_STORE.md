# Publishing SideKeys to Google Play — step by step

Everything that can be prepared is done. What remains needs **your** Google
account, identity and payment details, so it cannot be automated.

Work through the phases in order. Phase 0 is already finished.

---

## Phase 0 — Ready to upload ✅

- Signed **App Bundle** built: `SideKeys-v1.5.2.aab` (version code 15)
- [Privacy policy](PRIVACY.md) written — declares that no data is collected
- Release notes per language in `distribution/whatsnew/`
- Automated upload workflow: [.github/workflows/play-release.yml](.github/workflows/play-release.yml)

## Phase 1 — Developer account (~15 min + waiting)

1. Go to <https://play.google.com/console> and register
2. Pay the **one-time 25 USD** fee
3. Complete identity verification — Google may take a few days to approve

Nothing else can happen until this is done.

## Phase 2 — Create the app

Play Console → **Create app**

| Field | Value |
|---|---|
| App name | SideKeys |
| Default language | English (or German) |
| App or game | App |
| Free or paid | Free |

The package name `com.sidekeys.hibreak` is taken from the bundle on first upload
and can never be changed afterwards.

## Phase 3 — First upload (must be manual)

The GitHub workflow can only **update** an app that already exists, so the first
bundle goes up by hand:

1. **Testing → Internal testing → Create new release**
2. Accept **Play App Signing** when prompted (see the note below)
3. Upload `SideKeys-v1.5.2.aab`
4. Paste the text from `distribution/whatsnew/whatsnew-en-US` as the release notes
5. Save → Review release → **Start rollout to internal testing**

> **Play App Signing:** Google then holds the real signing key and your local
> keystore becomes the *upload* key. Consequence: APKs installed from Play have a
> **different signature** than the GitHub-release APKs, so you cannot update from
> one channel to the other — users have to uninstall first. That is normal and
> unavoidable if you want Play distribution.

## Phase 4 — The forms (this is where care pays off)

Play Console → **Policy → App content**. Four items matter:

### 4a. Privacy policy
Paste the raw URL of [PRIVACY.md](PRIVACY.md), e.g.
`https://github.com/Yannick158/sidekeys-hibreak/blob/main/PRIVACY.md`

### 4b. Data safety
Answer **"No"** to data collection and sharing — it is accurate: the app has no
`INTERNET` permission and therefore cannot transmit anything.

### 4c. Accessibility API declaration ← the important one
You will be asked why the app uses the AccessibilityService. Suggested wording:

> SideKeys receives hardware key presses so users can assign their own actions to
> the physical side keys of their device. The service uses only the key-event
> filter and performs the actions the user configured (e.g. launching an app,
> Home, Back, scrolling). It does not read screen content — the service does not
> declare `canRetrieveWindowContent`. The foreground app's package name is read
> only for the optional per-app profiles feature.

That last sentence is your strongest argument, and it is verifiably true.

### 4d. Content rating
Fill in the questionnaire — a utility with no ads or user content rates as
"Everyone" everywhere.

## Phase 5 — Store listing

**Store presence → Main store listing.** Required:

- Short description (max 80 chars), e.g.
  *"Give your phone's side keys and volume keys any function you want."*
- Full description — reuse the feature list from [README.md](README.md)
- **App icon** 512×512 PNG
- **Feature graphic** 1024×500 PNG
- **At least 2 phone screenshots** — home screen and the mapping screen work well

Screenshots and graphics are the only assets still missing; everything else is
written.

## Phase 6 — Automated releases from here on

Once the app exists in the Console, further versions go out from GitHub.

1. Play Console → **Users and permissions** → invite a service account
2. Google Cloud → IAM → **Create service account**, grant it **no project roles**
3. Create a **JSON key**
4. Back in Play Console, give it access **only to this app**, permission
   *Release manager*

> Do **not** grant "Owner" on the Cloud project — that advice circulates widely
> and is [excessive and unsafe](https://github.com/r0adkll/upload-google-play/issues/224).

Then add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `PLAY_SERVICE_ACCOUNT_JSON` | full contents of the service account JSON |
| `SIDEKEYS_KEYSTORE_BASE64` | `base64 -i ~/.android-keys/sidekeys/sidekeys.jks` |
| `SIDEKEYS_STORE_PASSWORD` | your keystore password |
| `SIDEKEYS_KEY_PASSWORD` | your key password |

Create an **environment** named `play-store` (Settings → Environments) with
yourself as a required reviewer, so no upload happens unattended.

Afterwards: Actions → **Play Store release** → *Run workflow*.

---

## Notes on review risk

SideKeys declares `WRITE_SECURE_SETTINGS`, which users grant themselves via adb
or Shizuku. This is **established practice** for this category — both
[Key Mapper](https://docs.keymapper.club/user-guide/adb-permissions/) and
[Button Mapper](https://setup.buttonmapper.app/) are on Play and do exactly the
same. Do not hide it: describe it plainly in the listing as an optional setup
step for two convenience features.

Accessibility apps get a stricter review than average, so expect the first review
to take longer than the usual few days. Start with the **internal testing** track
(as in phase 3) — it reviews faster and lets you fix findings before going to
production.
