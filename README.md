# SideKeys — Tastenbelegung für das Bigme HiBreak Pro

SideKeys belegt die beiden zusätzlichen Hardware-Tasten an der Seite des
Bigme HiBreak Pro (und beliebige andere Hardware-Tasten) mit eigenen Funktionen —
pro Taste getrennt für **einfachen Druck**, **Doppeldruck** und **langen Druck**.

Das HiBreak Pro läuft mit Android 14; die Bigme-eigenen Einstellungen
(„Benutzerdefinierte Taste") bieten nur eine feste Aktionsliste (Home, Zurück,
Screenshot, App-Wechsler, Cache leeren, Vollrefresh, E-Ink-Center, Vorherige/
Nächste Seite, Licht) und können insbesondere **keine Apps starten** — genau
diese Lücke schließt SideKeys.

## Funktionen

- **Google Assistant starten**
- **Google Wallet öffnen** (mit Fallback auf Web-Link/Play Store)
- **Beliebige App starten** (Auswahl aus allen installierten Apps)
- Systemaktionen: Startbildschirm, Zurück, Letzte Apps, Benachrichtigungen,
  Schnelleinstellungen, Ausschalt-Menü, Bildschirm sperren, Screenshot
- Taschenlampe umschalten, Medien steuern (Play/Pause, Vor/Zurück),
  „Nicht stören" umschalten
- **Eigener Intent** (Experten-Option): startet beliebige Activities oder sendet
  Broadcasts — damit lassen sich auch Bigme-eigene Spezialfunktionen anbinden,
  die sonst nur über die Bigme-Einstellungen erreichbar sind
- **Tasten-Entprellung**: die HiBreak-Pro-Seitentasten prellen hardwareseitig
  (bekanntes Problem, z. B. Seiten-Überspringen in Lese-Apps) — SideKeys filtert
  Geisterdrücke; das Intervall ist einstellbar
- E-Ink-optimierte Oberfläche: reines Schwarz-Weiß, keine Animationen

## Installation

1. `SideKeys-release.apk` auf das Handy kopieren (oder per `adb install`).
2. Installation aus unbekannten Quellen erlauben, APK antippen und installieren.
3. App öffnen → **„Bedienungshilfe aktivieren"** antippen → in den
   Einstellungen **„SideKeys Tastenbelegung"** einschalten.
   (Ohne diesen Schritt kann Android der App keine Hardware-Tasten liefern.)
4. **Android 13/14, „Eingeschränkte Einstellung":** Bei per APK installierten
   Apps sperrt Android den Bedienungshilfe-Schalter zunächst. Lösung:
   App-Info von SideKeys öffnen → Menü (⋮) oben rechts →
   **„Eingeschränkte Einstellungen zulassen"** → danach die Bedienungshilfe
   aktivieren.

```bash
adb install SideKeys-release.apk
```

## Benutzung

1. **„+ Taste zuordnen"** antippen.
2. Die gewünschte Seitentaste **drücken** — die App erkennt den Keycode automatisch.
3. Für einfachen Druck / Doppeldruck / langen Druck je eine Aktion wählen
   (mit ▶ sofort testbar) und **Speichern**.

Wichtig zu wissen:

- Ist nur der einfache Druck belegt, reagiert die Taste sofort. Sobald ein
  Doppeldruck belegt ist, wartet der einfache Druck das Doppeldruck-Zeitfenster
  ab (einstellbar unter Einstellungen).
- Wird eine Belegung gelöscht, erhält die Taste ihre ursprüngliche
  (Bigme-)Funktion zurück — die App reicht nicht gemappte Tasten unverändert durch.
- Falls die Bigme-Firmware eine Taste bereits selbst abfängt, bevor sie bei
  Apps ankommt, stelle die Taste in den Bigme-Einstellungen auf „keine
  Funktion"/Standard, damit SideKeys sie erhält.

## Hinweise zur Bigme-Firmware

- **Wichtig — Bigme-Tastenbelegung auf „Keine" stellen:** Die Bigme-Firmware
  führt ihre eigene Tastenbelegung parallel zu SideKeys aus. Symptom: Nach dem
  Erfassen einer Taste erscheint der Zuordnungs-Screen nur kurz und verschwindet
  wieder, weil die Bigme-Aktion (z. B. Seitenwechsel/E-Ink-Center) dazwischenfunkt —
  mit langem Halten funktioniert es, weil dort meist keine Bigme-Aktion liegt.
  Lösung: In den Bigme-Einstellungen unter „Benutzerdefinierte Taste" für beide
  Tasten **Einzeltipp und langes Drücken auf „Keine"** stellen.
  (Die Tasten senden normale KeyEvents — die App Key Mapper funktioniert auf dem
  Gerät nachweislich, SideKeys nutzt denselben Mechanismus. Wahrscheinliche
  Keycodes sind BILD-AUF/BILD-AB (92/93), der Capture-Screen erkennt aber jeden Code.)
- **DuraSpeed deaktivieren:** Die MediaTek-Funktion „DuraSpeed" beendet
  Hintergrund-Apps aggressiv und kann auch den SideKeys-Dienst treffen.
  Abschalten per ADB:

  ```bash
  adb shell settings put global setting.duraspeed.enabled 0
  ```

  Zusätzlich SideKeys von der Akku-Optimierung ausnehmen
  (Einstellungen → Apps → SideKeys → Akku → „Nicht optimieren").
- Der Capture-Screen zeigt jeden erkannten Keycode an — er funktioniert auch
  für Kopfhörer- oder Bluetooth-Tasten.
- Über **„Eigener Intent"** lassen sich Bigme-Spezialfunktionen auslösen, z. B.
  `Activity`-Start der Bigme-Einstellungen oder (falls von Bigme dokumentiert)
  E-Ink-Refresh-Broadcasts.

## Projekt bauen (für Entwickler)

Voraussetzungen: JDK 17, Android SDK (Platform 34).

```bash
./gradlew assembleRelease
```

Die APK liegt danach in `app/build/outputs/apk/release/`.

**Signierung:** Der Release-Keystore liegt bewusst **außerhalb** des Repos, damit
er nicht versehentlich veröffentlicht werden kann. Setze die Umgebungsvariable
`SIDEKEYS_KEYSTORE_DIR` auf das Verzeichnis mit `sidekeys.jks` und
`keystore.properties` (Vorlage: [keystore.properties.example](keystore.properties.example)):

```bash
export SIDEKEYS_KEYSTORE_DIR=~/.android-keys/sidekeys
./gradlew assembleRelease
```

Ohne diese Variable schlägt der Release-Build **bewusst fehl** (verhindert ein
versehentlich debug-signiertes „Release"). Für ein lokales Testbuild:

```bash
./gradlew assembleRelease -PallowDebugSigning
```

Eigenen Keystore erzeugen:

```bash
keytool -genkeypair -keystore ~/.android-keys/sidekeys/sidekeys.jks \
  -alias sidekeys -keyalg RSA -keysize 4096 -validity 10000
```

**Echtheit prüfen:** Offizielle APKs sind mit dem Maintainer-Schlüssel signiert.
Fingerprint des Signaturzertifikats (SHA-256):

```
CE:1A:7F:AC:78:29:3F:ED:0C:B7:6A:48:7F:7C:09:FB:81:EA:44:89:AD:36:75:29:72:27:9C:CD:8B:34:F9:D3
```

Verifizieren mit: `apksigner verify --print-certs SideKeys-release.apk`

## Architektur

Kotlin · Jetpack Compose (Material 3) · MVVM mit UiState/StateFlow ·
DataStore Preferences · kotlinx-serialization · AccessibilityService mit
`FLAG_REQUEST_FILTER_KEY_EVENTS` (`canRequestFilterKeyEvents`).

```
app/src/main/java/com/sidekeys/hibreak/
├── MainActivity.kt              # Single-Activity, Capture-Fallback
├── ui/SideKeysApp.kt            # Navigation (ohne Animationen)
├── core/
│   ├── model/                   # KeyMapping, KeyAction, Settings
│   ├── data/                    # DataStore-Repository
│   ├── common/                  # Keycode-Namen, Labels, Service-Status
│   └── designsystem/            # E-Ink-Theme + Komponenten
├── service/
│   ├── KeyInterceptorService.kt # AccessibilityService (Key-Filter)
│   ├── KeyPressHandler.kt       # Einfach/Doppel/Lang-Zustandsautomat
│   └── ActionExecutor.kt        # Führt alle Aktionen aus
└── feature/                     # home, capture, mapping, apppicker, settings
```

Die App liest keine Bildschirminhalte, hat keine Internet-Berechtigung und
sammelt keine Daten.
