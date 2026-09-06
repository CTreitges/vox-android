# JNI: die nativen Symbole in libwhisperbar.so heissen Java_com_chris_whisperbar_whisper_WhisperLib_*
# -> Klassen- und Methodennamen duerfen von R8 nicht umbenannt werden.
-keep class com.chris.whisperbar.whisper.WhisperLib { *; }
