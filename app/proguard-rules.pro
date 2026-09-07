# JNI: die nativen Symbole in libwhisperloom.so heissen Java_com_chris_whisperloom_whisper_WhisperLib_*
# -> Klassen- und Methodennamen duerfen von R8 nicht umbenannt werden.
-keep class com.chris.whisperloom.whisper.WhisperLib { *; }
