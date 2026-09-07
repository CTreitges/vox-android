# Drittkomponenten und Lizenzen

Vox steht unter der MIT-Lizenz (siehe LICENSE). Enthaltene bzw. genutzte Drittkomponenten:

Abhängigkeiten und Drittkomponenten:
- whisper.cpp (MIT) — https://github.com/ggml-org/whisper.cpp — als Git-Submodul, statisch in libvox.so gelinkt
- Whisper-Modelle im ggml-Format (MIT, OpenAI/ggml) — werden NICHT mitgeliefert, sondern auf Wunsch des Nutzers
  zur Laufzeit von https://huggingface.co/ggerganov/whisper.cpp geladen
- Jetpack Compose / AndroidX (Apache License 2.0) — https://developer.android.com/jetpack
- Material Symbols (Apache License 2.0) — https://fonts.google.com/icons
