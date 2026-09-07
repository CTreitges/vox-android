package com.chris.whisperbar.whisper

/**
 * Ein herunterladbares whisper.cpp-Modell (GGML, quantisiert) von huggingface.co/ggerganov/whisper.cpp.
 *
 * Bytes und SHA-256 stammen aus der Hugging-Face-API (research/whisper-cpp.md §5, live geprueft
 * 2026-09-06); das Katalog-Repo ist seit 2024-10-29 unveraendert. Aendert sich upstream etwas,
 * schlaegt die Integritaetspruefung des Downloads sauber fehl, statt ein korruptes Modell zu laden.
 */
data class WhisperModel(
    /** Stabile ID (Prefs.offlineModel): "tiny", "base", "small", "large-v3-turbo". */
    val id: String,
    /** Anzeigename ("Small", "Large v3 Turbo"). */
    val label: String,
    val fileName: String,
    /** Exakte Dateigroesse — ein Download gilt nur bei Gleichheit als vollstaendig. */
    val bytes: Long,
    /** SHA-256 der Datei, hex, klein geschrieben. */
    val sha256: String,
    /** Grober RAM-Bedarf beim Erkennen (Modell + KV-Cache + Compute-Puffer). */
    val approxRamBytes: Long,
    /** Mindest-Geraete-RAM (0 = keine Einschraenkung); [OfflineSupport.fitsDevice] rechnet 10 % Toleranz ein. */
    val minDeviceRamBytes: Long,
    val recommended: Boolean = false,
) {
    val url: String get() = ModelCatalog.BASE_URL + fileName
}

/** Die vier angebotenen Modelle in Anzeige-Reihenfolge (UX-Spec E4). Kein Modell liegt im APK. */
object ModelCatalog {

    /** 302 -> CDN; HttpURLConnection folgt (gleiches Schema https). Range-Requests werden unterstuetzt. */
    const val BASE_URL = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/"

    private const val MB = 1_000_000L
    private const val GIB = 1024L * 1024L * 1024L

    val TINY = WhisperModel(
        id = "tiny", label = "Tiny", fileName = "ggml-tiny-q5_1.bin",
        bytes = 32_152_673L,
        sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7",
        approxRamBytes = 250 * MB, minDeviceRamBytes = 0,
    )
    val BASE = WhisperModel(
        id = "base", label = "Base", fileName = "ggml-base-q5_1.bin",
        bytes = 59_707_625L,
        sha256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898",
        approxRamBytes = 355 * MB, minDeviceRamBytes = 0,
    )
    val SMALL = WhisperModel(
        id = "small", label = "Small", fileName = "ggml-small-q5_1.bin",
        bytes = 190_085_487L,
        sha256 = "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb",
        approxRamBytes = 430 * MB, minDeviceRamBytes = 0,
        recommended = true,
    )
    val LARGE_V3_TURBO = WhisperModel(
        id = "large-v3-turbo", label = "Large v3 Turbo", fileName = "ggml-large-v3-turbo-q5_0.bin",
        bytes = 574_041_195L,
        sha256 = "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2",
        approxRamBytes = 1_000 * MB, minDeviceRamBytes = 6 * GIB,
    )

    val models: List<WhisperModel> = listOf(TINY, BASE, SMALL, LARGE_V3_TURBO)

    /** Standard-Download und Fallback fuer unbekannte IDs (= Prefs.DEFAULT_OFFLINE_MODEL). */
    val DEFAULT: WhisperModel = SMALL

    fun find(id: String?): WhisperModel? = models.firstOrNull { it.id == id }

    fun byId(id: String?): WhisperModel = find(id) ?: DEFAULT
}
