# WhisperBar — Diktier-Tastatur für Android

Diktieren in **jede** App: halte den Mikro-Knopf, sprich, und der erkannte Text landet direkt
im aktiven Textfeld (WhatsApp, Gmail, Browser …). Eine schlanke Alternative zu
[Wispr Flow](https://wisprflow.ai/) und der Mac-App [Whisper Bar](https://whisperbar.app/)
(Kevin Chromik) — als Android-**Tastatur (IME)** *und* als schwebender Knopf über allen Apps.

Die Erkennung läuft über **deinen eigenen API-Zugang** (OpenAI-kompatibel: OpenAI, Groq,
self-hosted). Du brauchst also einmalig einen API-Key; dein Audio geht zur Erkennung an den
Anbieter, den du einträgst.

> **Hinweis zur Version 2.0:** Der frühere On-Device-Betrieb (whisper.cpp im APK, ~154 MB) ist
> raus — er lieferte auf dem Telefon zu schlechte Ergebnisse. Der letzte Stand mit lokalem Modell
> liegt als Tag [`offline-v1`](../../releases/tag/offline-v1) im Repo.

## Features

- 🎤 **Zwei Diktat-Wege**: (a) eigene **Tastatur (IME)** mit Mikro, (b) **schwebender Mikro-Knopf**
  (Overlay + Bedienungshilfe), der Text ins Fokus-Feld schreibt — **ohne** Gboard zu verlassen.
- 🔵 **Sichtbare Zustände** am schwebenden Knopf: bereit · nimmt auf (mit Timer) · sendet · Fehler.
  Ziehen aufs ✕ am unteren Rand verwirft das Diktat, der Knopf merkt sich seine Position.
- ↻ **Kein Diktat geht verloren**: Scheitert die Anfrage (kein Netz, Server-Aussetzer), bleibt das
  Audio gepuffert — ein Tipp sendet erneut.
- ✨ **Textveredelung**: Füllwörter entfernen, Sätze groß schreiben, Whitespace/Satzzeichen fixen
  (lokal, ohne Extra-Kosten) — optional zusätzlich **KI-Glättung** für Zeichensetzung, Grammatik
  und Absätze, wahlweise mit **intelligenter Füllwort-Entfernung** (die KI entscheidet selbst,
  statt fester Wortliste).
- 🏷️ **Kontext-Prompt**: Eigennamen und Fachbegriffe hinterlegen — verbessert die Erkennung, kostet nichts.
- 🌍 **Mehrsprachig** — Default **Deutsch**, oder auto / en / es / fr / it.
- 🆓 MIT-lizenziert · APK ~2 MB · kein AndroidX/Compose

## Architektur

| Schicht | Umsetzung |
|---|---|
| Aufnahme | `AudioRecorder.kt` — 16 kHz Mono PCM16 → Float, `AudioUtils.trimSilence` |
| Upload | `WavEncoder.kt` — WAV im Speicher, `api/Http.kt` — HttpURLConnection, typisierte Fehler |
| Erkennung | `api/ApiTranscriber.kt` — `POST /audio/transcriptions` (multipart, mit `prompt`) |
| KI-Glättung | `api/TextRefiner.kt` — optional, `POST /chat/completions`; Anweisung in `RefinePrompt` |
| Pipeline | `TranscriptionEngine.kt` — Stille schneiden → erkennen → glätten → polieren |
| Textveredelung | `TextPolisher.kt` + `PolishPlan` — reines Kotlin, JVM-unit-getestet |
| Tastatur | `ime/WhisperBarInputMethodService.kt` — `InputMethodService`, Wiederholen-Taste |
| Schwebender Knopf | `overlay/FloatingMicService.kt` + `BubbleState`/`BubbleUi`/`BubblePosition` |
| Text einfügen | `a11y/TextInserterAccessibilityService.kt` + `TextInsertion` (clipboard-frei) |
| Onboarding/Settings | `SetupActivity.kt`, `SettingsActivity.kt` (reines Framework, kein AppCompat) |

Bewusst **kein AndroidX/Compose** im App-Code und keine HTTP-Bibliothek → schlank, wenige Build-Risiken.
Die gesamte Rechen-Logik ohne Android-Abhängigkeit (Einfügen, Position, Formatierung, Polish-Plan,
Prompt-Bau) liegt in reinen Kotlin-Objekten und ist damit ohne Emulator testbar.

## Einrichten

1. APK installieren, App **WhisperBar** öffnen.
2. **API-Key eintragen** (Einstellungen): Base-URL, Key, Modell.
   - OpenAI: `https://api.openai.com/v1`, Modell `gpt-4o-transcribe` (Default) oder `whisper-1`
   - Groq: `https://api.groq.com/openai/v1`, Modell `whisper-large-v3-turbo`
   Der Key wird nur lokal auf dem Gerät gespeichert.
3. Mikrofon erlauben.
4. Entweder Tastatur aktivieren + als Eingabemethode wählen — **oder** (empfohlen) „Über anderen
   Apps anzeigen" + Bedienungshilfe „WhisperBar" aktivieren und den schwebenden Knopf starten.

**Schwebender Knopf:** antippen = aufnehmen, nochmal antippen = senden, ziehen = verschieben,
auf das ✕ ziehen = verwerfen. Nach einem Fehler bedeutet ein Tipp „erneut senden".

## Bauen

### Per GitHub Actions (empfohlen)
`.github/workflows/build.yml` baut bei jedem Push: Unit-Tests + Lint, Debug-APK und
Release-signiertes APK → Artefakte `whisperbar-debug-apk` / `whisperbar-release-apk`.

**Release-Signierung:** Ein persistenter PKCS12-Keystore liegt als GitHub-Secrets
`WB_KEYSTORE_B64` + `WB_KEYSTORE_PASSWORD` (nicht im Repo). Die CI dekodiert ihn und
signiert `assembleRelease`. Derselbe Key signiert jeden Release → Updates sind installierbar.
Für lokale Release-Builds: Keystore unter `keystore/whisperbar-release.p12` ablegen und
`WB_KEYSTORE`/`WB_KEYSTORE_PASSWORD`/`WB_KEY_ALIAS` als Env setzen (fehlt er, bleibt release unsigniert).

### Lokal
```bash
git clone <repo-url>
cd whisperbar-android
./gradlew assembleDebug
```
Voraussetzungen: JDK 17, Android SDK 35. Kein NDK, kein CMake, kein Submodul mehr.

## Tests

Alle Tests laufen ohne Gerät (`./gradlew testDebugUnitTest`):

| Datei | Deckt ab |
|---|---|
| `TextPolisherTest` | Füllwörter, Groß-Schreibung, Whitespace/Satzzeichen |
| `PolishPlanTest` | Regex-Filter weicht der KI-Entscheidung |
| `WavEncoderTest` | WAV-Header und PCM-Konvertierung |
| `AudioUtilsTest` | Stille-Trimmen |
| `a11y/TextInsertionTest` | Einfügen an Cursor/Auswahl, leeres Feld |
| `overlay/BubblePositionTest` | Clamping, Abbrechen-Trefferfläche |
| `overlay/BubbleUiTest` | Timer-Formatierung |
| `api/RefinePromptTest` | Anweisung für die KI-Glättung |
| `api/ApiErrorsTest` | Welche Fehler einen zweiten Versuch verdienen |

## Lizenz / Credits
MIT (siehe `LICENSE`).
Inspiriert von Wispr Flow und Whisper Bar (Kevin Chromik) — eigenständige, unabhängige Implementierung.
