# Modell-Verzeichnis

Hier wird beim Build das gebündelte Standard-Modell `ggml-small-q5_1.bin`
(multilingual, quantisiert, ~181 MB) abgelegt. Es wird **nicht** eingecheckt
(siehe `.gitignore`); die CI lädt es automatisch.

Für lokale Builds (Android Studio) einmalig laden:

```bash
curl -L --fail -o app/src/main/assets/models/ggml-small-q5_1.bin \
  https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin
```

Base/Tiny (q5) werden zur Laufzeit über die Einstellungen bei Bedarf geladen —
diese müssen hier **nicht** liegen.
