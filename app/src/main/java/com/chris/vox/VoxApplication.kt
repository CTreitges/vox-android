package com.chris.vox

import android.app.Application
import android.content.ComponentCallbacks2
import com.chris.vox.whisper.WhisperEngine

/**
 * Prozessweiter Einstieg: initialisiert die Offline-Engine (Modellordner) und gibt das geladene
 * whisper-Modell bei Speicherdruck frei — IME-Prozesse sind LMK-Kandidaten, small belegt ~430 MB,
 * large-v3-turbo ~1 GB. Beim naechsten Diktat wird es neu geladen (Sekunden).
 */
class VoxApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        WhisperEngine.init(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // RUNNING_CRITICAL (15), UI_HIDDEN (20) und alle Hintergrund-Stufen darueber.
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) WhisperEngine.release()
    }
}
