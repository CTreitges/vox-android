// JNI-Bruecke zwischen Kotlin (com.chris.whisperbar.WhisperLib) und whisper.cpp.
// Adaptiert vom offiziellen whisper.cpp Android-Beispiel (examples/whisper.android),
// aber mit eigenem Symbol-Namespace und einem Sprach-Parameter fuer fullTranscribe.
#include <jni.h>
#include <android/asset_manager.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <string.h>
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
        jint num_threads, jstring language_str, jfloatArray audio_data) {
    (void) thiz;
    struct whisper_context *ctx = (struct whisper_context *) context_ptr;
    jfloat *audio = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_len = env->GetArrayLength(audio_data);
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

    whisper_reset_timings(ctx);
    if (whisper_full(ctx, params, audio, audio_len) != 0) {
        LOGW("whisper_full ist fehlgeschlagen");
    }

    env->ReleaseStringUTFChars(language_str, language);
    env->ReleaseFloatArrayElements(audio_data, audio, JNI_ABORT);
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
