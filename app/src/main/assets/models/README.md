# Modell-Verzeichnis

Hier wird beim Build `ggml-base.bin` (multilingual, ~148 MB) abgelegt.

Die Datei wird **nicht** eingecheckt (siehe `.gitignore`). Die CI lädt sie automatisch.

Für lokale Builds (Android Studio) einmalig laden:

```bash
curl -L --fail -o app/src/main/assets/models/ggml-base.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin
```

> Kleiner/schneller (weniger genau): `ggml-tiny.bin` (~77 MB). Dann auch
> `WhisperContext.MODEL_ASSET` und die CI-`MODEL_URL/MODEL_PATH` anpassen.
