// JNI-Bruecke zwischen Kotlin (com.chris.whisperbar.WhisperLib) und whisper.cpp.
// Adaptiert vom offiziellen whisper.cpp Android-Beispiel (examples/whisper.android),
// aber mit eigenem Symbol-Namespace und einem Sprach-Parameter fuer fullTranscribe.
#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <string.h>
#include <vector>
#include "whisper.h"

#define TAG "WhisperBarJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

// --- Asset-Streaming-Loader: laedt das GGML-Modell direkt aus dem APK-Asset,
//     ohne es vorher nach filesDir kopieren zu muessen (Asset muss unkomprimiert sein).
static size_t asset_read(void *ctx, void *output, size_t read_size) {
    return AAsset_read((AAsset *) ctx, output, read_size);
}
static bool asset_is_eof(void *ctx) {
    return AAsset_getRemainingLength64((AAsset *) ctx) <= 0;
}
static void asset_close(void *ctx) {
    AAsset_close((AAsset *) ctx);
}

static struct whisper_context *whisper_init_from_asset(
        JNIEnv *env, jobject assetManager, const char *asset_path) {
    LOGI("Lade Modell aus Asset '%s'", asset_path);
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);
    AAsset *asset = AAssetManager_open(mgr, asset_path, AASSET_MODE_STREAMING);
    if (!asset) {
        LOGW("Asset '%s' konnte nicht geoeffnet werden", asset_path);
        return nullptr;
    }
    whisper_model_loader loader = {
            .context = asset,
            .read = &asset_read,
            .eof = &asset_is_eof,
            .close = &asset_close,
    };
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false; // Android: reine CPU-Inferenz
    return whisper_init_with_params(&loader, cparams);
}

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_chris_whisperbar_WhisperLib_initContextFromAsset(
        JNIEnv *env, jobject thiz, jobject assetManager, jstring asset_path_str) {
    (void) thiz;
    const char *path = env->GetStringUTFChars(asset_path_str, nullptr);
    struct whisper_context *ctx = whisper_init_from_asset(env, assetManager, path);
    env->ReleaseStringUTFChars(asset_path_str, path);
    return (jlong) ctx;
}

JNIEXPORT jlong JNICALL
Java_com_chris_whisperbar_WhisperLib_initContext(
        JNIEnv *env, jobject thiz, jstring model_path_str) {
    (void) thiz;
    const char *path = env->GetStringUTFChars(model_path_str, nullptr);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    struct whisper_context *ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(model_path_str, path);
    return (jlong) ctx;
}

JNIEXPORT void JNICALL
Java_com_chris_whisperbar_WhisperLib_freeContext(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env; (void) thiz;
    whisper_free((struct whisper_context *) context_ptr);
}

JNIEXPORT void JNICALL
Java_com_chris_whisperbar_WhisperLib_fullTranscribe(
        JNIEnv *env, jobject thiz, jlong context_ptr,
        jint num_threads, jstring language_str, jint audio_ctx,
        jfloatArray audio_data, jint audio_offset, jint audio_length) {
    (void) thiz;
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;

    // Nur den tatsaechlich benoetigten Ausschnitt kopieren. Der Aufnahme-Puffer ist
    // gewachsen und meist deutlich groesser als das getrimmte Audio — GetFloatArrayElements
    // wuerde ihn komplett kopieren, GetFloatArrayRegion kopiert exakt den Bereich.
    const jsize total = env->GetArrayLength(audio_data);
    if (audio_offset < 0 || audio_length <= 0 || audio_offset + audio_length > total) {
        LOGW("Ungueltiger Audio-Bereich: offset=%d len=%d total=%d",
             (int) audio_offset, (int) audio_length, (int) total);
        return;
    }
    std::vector<float> audio((size_t) audio_length);
    env->GetFloatArrayRegion(audio_data, audio_offset, audio_length, audio.data());

    const char *language = env->GetStringUTFChars(language_str, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime   = false;
    params.print_progress   = false;
    params.print_timestamps = false;
    params.print_special    = false;
    params.translate        = false;
    params.no_timestamps    = true;
    params.single_segment   = false;
    params.no_context       = true;
    params.suppress_blank   = true;
    params.n_threads        = num_threads;
    params.language         = language; // "auto" => Auto-Erkennung

    // --- Die drei Parameter, die das Tempo bestimmen ---------------------------
    //
    // 1. audio_ctx: Whisper padded JEDES Audio auf 30 s und laesst den Encoder ueber
    //    alle 1500 Positionen laufen. Bei einem 3-Sekunden-Diktat ist der allergroesste
    //    Teil davon Stille. Der Deckel spart genau diese Arbeit.
    if (audio_ctx > 0) {
        params.audio_ctx = audio_ctx;
    }
    // 2. Kein Temperatur-Fallback: schlaegt die Dekodierung fehl, wiederholt whisper
    //    denselben Abschnitt sonst bis zu fuenfmal mit steigender Temperatur — im
    //    schlechtesten Fall die fuenffache Wartezeit. Fuer kurze Diktate nicht die
    //    Qualitaet wert.
    params.temperature      = 0.0f;
    params.temperature_inc  = 0.0f;
    // 3. Greedy ohne Mehrfachziehung — bei temperature 0 waeren die Durchlaeufe identisch.
    params.greedy.best_of   = 1;

    whisper_reset_timings(ctx);
    if (whisper_full(ctx, params, audio.data(), (int) audio.size()) != 0) {
        LOGW("whisper_full ist fehlgeschlagen");
    }

    env->ReleaseStringUTFChars(language_str, language);
}

JNIEXPORT jint JNICALL
Java_com_chris_whisperbar_WhisperLib_getTextSegmentCount(
        JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void) env; (void) thiz;
    return whisper_full_n_segments((struct whisper_context *) context_ptr);
}

JNIEXPORT jstring JNICALL
Java_com_chris_whisperbar_WhisperLib_getTextSegment(
        JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    (void) thiz;
    const char *text = whisper_full_get_segment_text((struct whisper_context *) context_ptr, index);
    return env->NewStringUTF(text);
}

JNIEXPORT jstring JNICALL
Java_com_chris_whisperbar_WhisperLib_getSystemInfo(JNIEnv *env, jobject thiz) {
    (void) thiz;
    return env->NewStringUTF(whisper_print_system_info());
}

} // extern "C"
