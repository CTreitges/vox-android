# Modell-Verzeichnis

Hier wird beim Build `ggml-tiny.bin` (multilingual, ~77 MB) abgelegt.

Die Datei wird **nicht** eingecheckt (siehe `.gitignore`). Die CI lädt sie automatisch.

Für lokale Builds (Android Studio) einmalig laden:

```bash
curl -L --fail -o app/src/main/assets/models/ggml-tiny.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.bin
```
