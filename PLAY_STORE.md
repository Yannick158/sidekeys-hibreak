# Publishing SideKeys to Google Play — step by step

Everything that can be prepared is done. What remains needs **your** Google
account, identity and payment details, so it cannot be automated.

Work through the phases in order. Phase 0 is already finished.

---

## Phase 0 — Ready to upload ✅

- Signed **App Bundle** built: `SideKeys-v1.7.0.aab` (version code 21, targets API 36)
- [Privacy policy](PRIVACY.md) written — declares that no data is collected
- Release notes per language in `distribution/whatsnew/`
- Automated upload workflow: [.github/workflows/play-release.yml](.github/workflows/play-release.yml)

## Phase 1 — Developer account ✅ done

Account registered and identity verified.

> **Target API level:** Google requires **API 36 (Android 16)** for new apps from
> 31 August 2026; before that the floor is API 35. SideKeys targets **36**, so it
> satisfies the requirement either side of that date.

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
3. Upload `SideKeys-v1.7.0.aab`
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

**Short description** (max 80 chars), ready to paste:

```
Button mapper for E-Ink phones: give your side keys any function you want.
```

**Full description**, ready to paste:

```
SideKeys lets you decide what your phone's hardware keys do.

E-Ink phones usually have one or two extra side keys, but their firmware only
offers a fixed list of actions — and typically cannot launch apps at all.
SideKeys fills that gap: assign any action you like to each key, separately for
single press, double press and long press.

WHAT A KEY CAN DO
• Launch any installed app
• Start Google Assistant, open Google Wallet
• Home, Back, Recent apps, notifications, quick settings, power menu, lock
• Take a screenshot
• Scroll up and down in any app
• Flashlight, media controls, volume up/down/mute, Do Not Disturb
• Toggle Battery Saver — a key press reaches it even on phones whose quick
  settings panel cannot be edited
• Custom intents for power users

BUILT FOR E-INK
Pure black and white, no animations, no ghosting. Keys are captured at runtime —
just press the button you want to assign. Built-in debounce filters the ghost
presses that bouncy side keys produce.

MORE
• Per-app profiles: different actions while a chosen app is in the foreground
• Charge alarm: a sound, vibration and notification at a level you pick, so you
  can unplug in time

PRIVACY
No internet permission, so the app cannot send anything anywhere. It does not
read screen content. Your key mappings stay on the device and are deleted when
you uninstall. Open source under the MIT licence.

SETUP
SideKeys needs the accessibility permission — that is the only way an Android app
can receive hardware key presses. The app walks you through the steps.
```
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

---

## Anhang — Antworten für die Deklarationen in der Play Console

Alle Angaben unten sind auf den [Haftungsausschluss](DISCLAIMER.md) und die
[Datenschutzerklärung](PRIVACY.md) abgestimmt. Widersprüche zwischen Formular und
veröffentlichten Dokumenten sind das, was bei einer Prüfung auffällt — deshalb
bitte nicht abweichen.

### Policy → App-Inhalte

| Frage | Antwort | Warum |
|---|---|---|
| Datenschutzerklärung | URL zu `PRIVACY.md` | siehe Phase 4a |
| **Werbung:** Enthält die App Werbung? | **Nein** | kein Werbe-SDK enthalten |
| **App-Zugriff:** Sind Teile eingeschränkt? | **Alle Funktionen ohne besonderen Zugriff verfügbar** | kein Login, kein Konto, keine Bezahlschranke |
| **Inhaltseinstufung** | Fragebogen: überall „Nein" | Werkzeug ohne Gewalt, Sexualität, Drogen, Glücksspiel, Nutzerinhalte → USK 0 / PEGI 3 |
| **Zielgruppe** | **18 Jahre und älter** | hält die App aus den „Designed for Families"-Vorgaben und den strengeren Kinderdatenschutz-Regeln heraus |
| Spricht die App Kinder an? | **Nein** | Gestaltung und Zweck richten sich an Erwachsene |
| Nachrichten-App | Nein | |
| Gesundheits-App | Nein | |
| Finanzprodukte | Nein | |
| Behörden-App | Nein | |

### Data safety (Datensicherheit)

| Frage | Antwort |
|---|---|
| Erhebt oder teilt die App Nutzerdaten? | **Nein** |
| Werden Daten bei der Übertragung verschlüsselt? | entfällt (keine Übertragung) |
| Können Nutzer Löschung verlangen? | entfällt — alle Daten liegen lokal und werden mit der Deinstallation gelöscht |

> Begründung, falls nachgefragt wird: Google definiert „erhoben" als *vom Gerät
> übertragen*. SideKeys speichert Tastenbelegungen ausschließlich im privaten
> App-Speicher und besitzt keine `INTERNET`-Berechtigung, kann also technisch
> nichts übertragen.

### Sensible Berechtigungen

Erwähne von dir aus, ohne dass gefragt wird — verschweigen ist das einzige, was
hier schadet:

- **AccessibilityService** — Deklaration wie in Phase 4c
- **`WRITE_SECURE_SETTINGS`** — wird von der App nie selbst angefordert. Nutzer
  können sie optional per adb oder Shizuku erteilen, um zwei Komfortfunktionen
  freizuschalten (Energiesparmodus-Schalter, Neustart des Dienstes). Ohne sie
  funktioniert die App vollständig; die betroffenen Aktionen öffnen dann nur die
  jeweilige Einstellungsseite. Gleiches Vorgehen wie bei Key Mapper und
  Button Mapper.

### Store-Eintrag

| Feld | Wert |
|---|---|
| Kategorie | **Tools / Extras** |
| Kostenlos oder kostenpflichtig | **Kostenlos** (nachträglich nicht auf „kostenpflichtig" änderbar) |
| In-App-Käufe | **Nein** |
| Kontakt-E-Mail | dieselbe Adresse wie im Impressum |

> Kostenlos und werbefrei zu bleiben ist nicht nur eine Produktentscheidung: Es
> ist die Voraussetzung dafür, dass die Ausnahmen der EU-Produkthaftungs-
> richtlinie und des Cyber Resilience Act für nicht-kommerzielle freie Software
> greifen. Siehe [DISCLAIMER.md](DISCLAIMER.md).

---

## Advance notice to the Play App Review team

Optional but recommended for apps that use the Accessibility API
(support.google.com/googleplay/android-developer/answer/6320428, item 5).
Google asks for "an explanation documenting how the app uses the system
capabilities that the service requests". Paste the following.

### Subject

SideKeys (com.sidekeys.hibreak) — AccessibilityService used to remap hardware
buttons; no screen content is read

### Body

**What the app does.** SideKeys is a free, open-source button mapper for E-Ink
phones. Devices such as the Bigme HiBreak Pro have extra physical side keys that
the stock firmware exposes only for a fixed handful of functions. SideKeys lets
the user assign their own action to a physical key — separately for single
press, double press and long press. Every action is user-configured and runs
only in response to a physical key press.

**Why an AccessibilityService is required.** Android provides no other API for a
normal app to receive hardware key events while it is not in the foreground.
`FLAG_REQUEST_FILTER_KEY_EVENTS` on an AccessibilityService is the only
supported mechanism. There is no alternative permission, intent or system API
that achieves this.

**Capabilities requested, and why.**

| Requested | Purpose |
| --- | --- |
| `canRequestFilterKeyEvents` / `flagRequestFilterKeyEvents` | Receive hardware key events. Keys the user has not assigned are returned to the system unchanged. |
| `canPerformGestures` | Dispatch a swipe for the "scroll up" / "scroll down" actions and the experimental E-Ink refresh. Gestures are only dispatched in direct response to a key press. |

**Capabilities deliberately NOT requested.** `canRetrieveWindowContent` is not
declared. The service therefore cannot read view hierarchies, text fields, or
anything displayed by other apps. `isAccessibilityTool` is also not declared,
because SideKeys is a general-purpose utility and not an accessibility tool.

**The only data the service reads.** From accessibility events the app reads
`getPackageName()` and nothing else, so that a user who has configured a per-app
profile gets the mapping they chose for the app currently in the foreground.
No event text, node tree, or screen content is accessed.

**Where the data goes.** Nowhere. The app does not declare
`android.permission.INTERNET`, so it is technically incapable of transmitting
anything off the device. Key mappings are stored in the app's private storage
and are deleted on uninstall. There is no account, no analytics, no advertising
and no third-party SDK that collects data.

**No autonomous behaviour.** The app does not use the Accessibility API to
autonomously initiate, plan or execute actions. It is a deterministic
key-press-to-action lookup: the user presses a key they configured, and the one
action they assigned to it runs.

**In-app disclosure.** On first launch, before any other screen and without the
user navigating a menu, SideKeys shows a full-screen disclosure stating what the
accessibility service accesses, what it is used for, and that nothing is shared;
the user must accept it explicitly to continue. It is separate from the privacy
policy.

**About `WRITE_SECURE_SETTINGS` in the manifest.** This permission is declared
but is `signature|privileged` and is therefore **never granted by a Play
installation** — the app runs fully without it. It only takes effect if the user
deliberately grants it themselves, either with `adb shell pm grant` from a
computer or via Shizuku. It is declared because Android only lets an app use a
permission it has declared; it is not requested at runtime and cannot be
obtained through installation. Two optional features use it: toggling Battery
Saver (`Settings.Global` `low_power`), and re-enabling the accessibility service
after some OEM task managers force-stop the app. The in-app UI states both times
that this is optional and how the grant works.

**Source code.** https://github.com/Yannick158/sidekeys-hibreak — the
accessibility service is `app/src/main/java/com/sidekeys/hibreak/service/KeyInterceptorService.kt`
and its configuration is `app/src/main/res/xml/accessibility_service_config.xml`.
