# WhisperBar — lokale Diktier-Tastatur für Android

Eine kostenlose, **komplett offline** laufende Alternative zu [Wispr Flow](https://wisprflow.ai/)
und der Mac-App [Whisper Bar](https://whisperbar.app/) (Kevin Chromik).
Sprich — und der per [whisper.cpp](https://github.com/ggerganov/whisper.cpp) **auf dem Gerät**
erkannte Text landet direkt im aktiven Textfeld jeder App (WhatsApp, Gmail, Browser …).

Keine Cloud, kein Account, keine API-Keys. Das Whisper-Modell liegt lokal im APK.

## Bedienung

Ein einziger Knopf, zwei Gesten — kein Moduswechsel, nichts einzustellen:

| Geste | Wirkung |
|---|---|
| **Halten & sprechen** | Beim Loslassen wird transkribiert (klassisches Push-to-talk) |
| **Kurz tippen** | Aufnahme läuft freihändig weiter und endet **nach einer Sprechpause von selbst** |

Der schwebende Knopf kann zusätzlich:

| Geste | Wirkung |
|---|---|
| Ziehen | Verschieben — rastet am nächsten Bildschirmrand ein, Position bleibt gespeichert |
| Auf ✕ ziehen | Laufende Aufnahme verwerfen bzw. den Knopf ausblenden |
| Lang drücken | **Letztes Diktat zurücknehmen** |

In der Tastatur gibt es dafür eine eigene Taste (↺) sowie eine Löschtaste mit
Wiederholung beim Halten — für kleine Korrekturen muss man die Tastatur nicht wechseln.

## Features

- 🎤 **Zwei Diktat-Wege**: (a) **schwebender Mikro-Knopf** (Overlay + Bedienungshilfe), der
  Text ins Fokus-Feld schreibt — **ohne** Gboard zu verlassen; (b) eigene **Tastatur (IME)**.
- ⚡ **Turbo-Erkennung** — der Whisper-Encoder rechnet nur die tatsächlich gesprochene
  Länge statt immer 30 s (siehe unten). Bei typischen Diktaten von 2–6 s ist das der
  größte Einzelgewinn.
- ✋ **Freihändig** — tippen, sprechen, fertig. Die Aufnahme endet nach einer einstellbaren
  Sprechpause selbst.
- ↺ **Rückgängig** — genau der zuletzt eingefügte Text verschwindet wieder, in Tastatur
  und Overlay.
- 🔒 **100 % offline & lokal** — Audio verlässt das Gerät nie (whisper.cpp, ggml-**small** q5
  gebündelt). Modell wählbar: **small / base / tiny** (q5, Base/Tiny werden bei Bedarf geladen).
- ☁️ **Optionale Cloud-API** (opt-in) — OpenAI-kompatibel (OpenAI, Groq, self-hosted).
  Sendet dann Audio an den Anbieter (nicht mehr offline).
- ✨ **On-device-Textveredelung** — Füllwörter entfernen, Sätze groß schreiben, Leerzeichen fixen.
- 🌍 **Mehrsprachig** — Default **Deutsch** (fest = schneller/genauer), oder auto / en / es / fr / it.
- 🌗 **Hell & dunkel** — folgt dem Systemthema, auch die Tastatur.
- 🆓 MIT-lizenziert

## Wo die Geschwindigkeit herkommt

| Stellschraube | Umsetzung |
|---|---|
| **`audio_ctx`** | Whisper padded jedes Audio auf 30 s und lässt den Encoder über alle 1500 Positionen laufen. `WhisperTuning.audioCtxFor()` deckelt sie auf die reale Länge (Untergrenze 512, damit die Qualität hält). |
| **Kein Temperatur-Fallback** | `temperature_inc = 0` — sonst wiederholt whisper einen misslungenen Abschnitt bis zu fünfmal. |
| **Threads = schnelle Kerne** | `CpuInfo` liest `cpuinfo_max_freq` aus sysfs und zählt nur den schnellen Cluster. Little-Cores bremsen, weil whisper pro Layer synchronisiert. |
| **Zero-Copy-Audio** | Aufnahme schreibt direkt in einen Float-Puffer; Stille-Trimmung arbeitet auf Indizes ([`AudioSlice`](app/src/main/java/com/chris/whisperbar/AudioUtils.kt)); JNI kopiert per `GetFloatArrayRegion` nur den benötigten Ausschnitt. Vorher: drei volle Durchläufe über die Daten, alle nach dem Loslassen. |
| **Vorwärmen** | Das Modell lädt parallel zur laufenden Aufnahme — bis man ausgesprochen hat, ist es warm. |
| **Sparsames Zeichnen** | Pegel-Callback auf ~30 Hz gedrosselt; der Mikro-Orb rendert im Ruhezustand keinen einzigen Frame. |

## Architektur

| Schicht | Umsetzung |
|---|---|
| Spracherkennung | `whisper.cpp` (C/C++), als Git-Submodul, per NDK zu `libwhisperbar.so` gebaut |
| JNI-Brücke | `app/src/main/cpp/whisper_jni.cpp` ↔ `WhisperLib.kt` |
| Whisper-Wrapper | `WhisperContext.kt` — thread-sicher (ein Worker-Thread), Modell aus Asset gestreamt |
| Tempo-Heuristiken | `WhisperTuning.kt` — rein, JVM-unit-getestet |
| Audio | `AudioRecorder.kt` — 16 kHz Mono, direkt Float, VAD + Pegel |
| Diktat-Ablauf | `dictation/DictationController.kt` — **eine** Pipeline für Tastatur und Overlay |
| Textveredelung | `TextPolisher.kt` — reines Kotlin, JVM-unit-getestet |
| Tastatur | `ime/WhisperBarInputMethodService.kt` — `InputMethodService` |
| Schwebender Knopf | `overlay/FloatingMicService.kt` + `a11y/TextInserterAccessibilityService.kt` |
| Eigene Views | `ui/MicOrbView.kt`, `ui/WaveformView.kt` |
| Oberfläche | `HomeActivity.kt`, `SettingsActivity.kt` (reines Framework, kein AppCompat) |

Bewusst **kein AndroidX/Compose** im App-Code → schlank, wenige Build-Risiken.
Farben liegen als Tokens in `values/colors.xml` + `values-night/colors.xml`; Layouts
referenzieren nur Tokens, nie Rohwerte.

Das Modell (`ggml-small-q5_1.bin`, ~181 MB) wird zur Build-Zeit geladen (nicht im Git),
unkomprimiert im APK abgelegt (`noCompress "bin"`) und vom nativen Asset-Loader direkt gestreamt.

## Bauen

### Per GitHub Actions (empfohlen)
`.github/workflows/build.yml` baut bei jedem Push:
1. **Build APK + Unit-Tests + Lint** → Artefakte `whisperbar-debug-apk` (Debug-Key)
   **und `whisperbar-release-apk` (Release-signiert)**
2. **Emulator-Instrumented-Tests** (API 30, x86_64, KVM) → transkribiert `jfk.wav` echt auf einem Emulator

APK-Download: Actions-Run öffnen → gewünschtes Artefakt → `app-debug.apk` bzw. `app-release.apk`.

### Release veröffentlichen
Version in `app/build.gradle.kts` hochziehen (`versionCode` **und** `versionName`), die neue
Version in `.github/release-version` schreiben (z. B. `v2.0`) und optional Release-Notes unter
`.github/release-notes/v2.0.md` ablegen. Beim nächsten Push legt der `release`-Job daraus ein
GitHub-Release mit dem signierten APK an. Steht die Version schon als Release im Repo, passiert
nichts — weitere Pushes bleiben also ruhig. Ein Tag-Push `v*` oder ein manueller Start mit dem
Eingabefeld `release_tag` funktionieren ebenfalls und überschreiben ein bestehendes Release.

**Release-Signierung:** Ein persistenter PKCS12-Keystore liegt als GitHub-Secrets
`WB_KEYSTORE_B64` + `WB_KEYSTORE_PASSWORD` (nicht im Repo). Die CI dekodiert ihn und
signiert `assembleRelease`. Derselbe Key signiert jeden Release → Updates sind installierbar.
Für lokale Release-Builds: Keystore unter `keystore/whisperbar-release.p12` ablegen und
`WB_KEYSTORE`/`WB_KEYSTORE_PASSWORD`/`WB_KEY_ALIAS` als Env setzen (fehlt er, bleibt release unsigniert).

### Lokal (Android Studio)
```bash
git clone --recurse-submodules <repo-url>
cd whisperbar-android
# Modell einmalig laden:
curl -L --fail -o app/src/main/assets/models/ggml-small-q5_1.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin
./gradlew assembleDebug
```
Voraussetzungen: JDK 17, Android SDK 35, NDK `27.2.12479018`, CMake `3.22.1`.

## Installieren & nutzen (Telefon)
1. `app-debug.apk` aufs Telefon kopieren, installieren (Installation aus unbekannten Quellen erlauben).
2. App **WhisperBar** öffnen. Der Hauptknopf macht immer den nächsten fehlenden Schritt —
   Mikrofon erlauben → Overlay erlauben → Diktat-Knopf starten. Erledigte Schritte
   verschwinden aus der Liste.
3. Für automatisches Einfügen zusätzlich die **Bedienungshilfe „WhisperBar"** aktivieren
   (sonst landet der Text nur in der Zwischenablage).
4. In beliebiger App ins Textfeld tippen → schwebenden Knopf antippen → sprechen.

Das erste Diktat lädt kurz das Modell (danach bleibt es im Speicher). Auf sehr alten
Geräten ist `tiny` am schnellsten.

## Tests
- **Unit** (`app/src/test/…`): Textveredelung, Stille-Trimmung inkl. Index-Variante,
  WAV-Encoding und die Tempo-Heuristiken (`WhisperTuningTest`) — läuft ohne Gerät.
- **Instrumented** (`app/src/androidTest/…/WhisperTranscriptionTest.kt`): lädt Modell,
  transkribiert `jfk.wav`, prüft echten Text — beweist JNI + native lib + Modell auf einem Emulator.

## Lizenz / Credits
MIT (siehe `LICENSE`). Nutzt whisper.cpp (MIT) und das ggml-Whisper-Modell (MIT).
Inspiriert von Wispr Flow und Whisper Bar (Kevin Chromik) — eigenständige, unabhängige Implementierung.
