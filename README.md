# WhisperBar — lokale Diktier-Tastatur für Android

Eine kostenlose, **komplett offline** laufende Alternative zu [Wispr Flow](https://wisprflow.ai/)
und der Mac-App [Whisper Bar](https://whisperbar.app/) (Kevin Chromik) — als Android-**Tastatur (IME)**.
Halte den Mikro-Knopf, sprich, und der per [whisper.cpp](https://github.com/ggerganov/whisper.cpp)
**auf dem Gerät** erkannte Text landet direkt im aktiven Textfeld jeder App (WhatsApp, Gmail, Browser …).

Keine Cloud, kein Account, keine API-Keys. Das Whisper-Modell liegt lokal im APK.

## Features

- 🎤 **Push-to-talk-Diktat** als System-Tastatur — funktioniert in jeder App
- 🔒 **100 % offline & lokal** — Audio verlässt das Gerät nie (whisper.cpp, ggml-tiny multilingual)
- ✨ **On-device-Textveredelung** — Füllwörter (ähm/äh/um…) entfernen, Sätze groß schreiben,
  Leerzeichen vor Satzzeichen fixen (abschaltbar)
- 🌍 **Mehrsprachig** — Auto-Erkennung oder feste Sprache (de/en/es/fr/it)
- ⌨️ Basis-Tasten (Leer, Komma, Punkt, Enter, Backspace) + Tastatur-Wechsel + Einstellungen
- 🆓 MIT-lizenziert

## Architektur

| Schicht | Umsetzung |
|---|---|
| Spracherkennung | `whisper.cpp` (C/C++), als Git-Submodul, per NDK zu `libwhisperbar.so` gebaut |
| JNI-Brücke | `app/src/main/cpp/whisper_jni.cpp` ↔ `WhisperLib.kt` |
| Whisper-Wrapper | `WhisperContext.kt` — thread-sicher (ein Worker-Thread), Modell aus Asset gestreamt |
| Audio | `AudioRecorder.kt` — 16 kHz Mono PCM16 → Float |
| Textveredelung | `TextPolisher.kt` — reines Kotlin, JVM-unit-getestet |
| Tastatur | `ime/WhisperBarInputMethodService.kt` — `InputMethodService` |
| Onboarding/Settings | `SetupActivity.kt`, `SettingsActivity.kt` (reines Framework, kein AppCompat) |

Bewusst **kein AndroidX/Compose** im App-Code → schlank, wenige Build-Risiken.
Das Modell (`ggml-tiny.bin`, ~77 MB) wird zur Build-Zeit geladen (nicht im Git), unkomprimiert
im APK abgelegt (`noCompress "bin"`) und vom nativen Asset-Loader direkt gestreamt.

## Bauen

### Per GitHub Actions (empfohlen)
`.github/workflows/build.yml` baut bei jedem Push:
1. **Build APK + Unit-Tests + Lint** → lädt das signierte Debug-APK als Artefakt `whisperbar-debug-apk` hoch
2. **Emulator-Instrumented-Tests** (API 30, x86_64, KVM) → transkribiert `jfk.wav` echt auf einem Emulator

APK-Download: Actions-Run öffnen → Artefakt `whisperbar-debug-apk` → `app-debug.apk`.

### Lokal (Android Studio)
```bash
git clone --recurse-submodules <repo-url>
cd whisperbar-android
# Modell einmalig laden:
curl -L --fail -o app/src/main/assets/models/ggml-tiny.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
./gradlew assembleDebug
```
Voraussetzungen: JDK 17, Android SDK 35, NDK `27.2.12479018`, CMake `3.22.1`.

## Installieren & nutzen (Telefon)
1. `app-debug.apk` aufs Telefon kopieren, installieren (Installation aus unbekannten Quellen erlauben).
2. App **WhisperBar** öffnen → 3 Schritte: Mikrofon erlauben → Tastatur aktivieren → als Eingabemethode wählen.
3. In beliebiger App das Textfeld antippen → zur WhisperBar-Tastatur wechseln → **Mikro halten & sprechen**.

Erstes Diktat lädt kurz das Modell (danach im Speicher). Auf sehr alten Geräten ist `tiny` am schnellsten.

## Tests
- **Unit** (`app/src/test/…/TextPolisherTest.kt`): Füllwörter, Groß-Schreibung, Whitespace/Satzzeichen — läuft ohne Gerät.
- **Instrumented** (`app/src/androidTest/…/WhisperTranscriptionTest.kt`): lädt Modell, transkribiert `jfk.wav`,
  prüft echten Text — beweist JNI + native lib + Modell auf einem Emulator.

## Lizenz / Credits
MIT (siehe `LICENSE`). Nutzt whisper.cpp (MIT) und das ggml-Whisper-Modell (MIT).
Inspiriert von Wispr Flow und Whisper Bar (Kevin Chromik) — eigenständige, unabhängige Implementierung.
