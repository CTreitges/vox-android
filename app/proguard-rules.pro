# JNI-Bindings duerfen nicht umbenannt/entfernt werden — sonst findet der native
# Code die Java-Methoden nicht mehr.
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.chris.whisperbar.WhisperLib { *; }
