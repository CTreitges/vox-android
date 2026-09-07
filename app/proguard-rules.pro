# JNI: die nativen Symbole in libvox.so heissen Java_com_chris_vox_whisper_WhisperLib_*
# -> Klassen- und Methodennamen duerfen von R8 nicht umbenannt werden.
-keep class com.chris.vox.whisper.WhisperLib { *; }
