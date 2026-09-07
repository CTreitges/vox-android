package com.chris.vox.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Lokal gibt es kein NDK, der Linker prueft die JNI-Symbole also erst in CI. Dieser Test liest
 * WhisperLib.kt und whisper_jni.cpp und gleicht Namen, Praefix (Paket + Objektname) und
 * Parameterzahl in beide Richtungen ab — derselbe Abgleich wie tools/check_jni_symbols.py.
 */
class JniSymbolsTest {

    private fun source(vararg candidates: String): String =
        candidates.map { File(it) }.first { it.exists() }.readText()

    private val kt = source(
        "src/main/java/com/chris/vox/whisper/WhisperLib.kt",
        "app/src/main/java/com/chris/vox/whisper/WhisperLib.kt",
    )
    private val cpp = source("src/main/cpp/whisper_jni.cpp", "app/src/main/cpp/whisper_jni.cpp")

    private fun paramCount(list: String) = list.split(',').count { it.isNotBlank() }

    private val prefix: String
    private val ktFuns: Map<String, Int>
    private val cppFuns: Map<String, Int>

    init {
        val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(kt)!!.groupValues[1]
        val obj = Regex("^\\s*(?:\\w+\\s+)*object\\s+(\\w+)", RegexOption.MULTILINE).find(kt)!!.groupValues[1]
        // JNI-Mangling: "." -> "_", "_" -> "_1"
        prefix = "Java_" + "$pkg.$obj".replace("_", "_1").replace('.', '_') + "_"
        ktFuns = Regex("external\\s+fun\\s+(\\w+)\\s*\\(([^)]*)\\)").findAll(kt)
            .associate { it.groupValues[1] to paramCount(it.groupValues[2]) }
        cppFuns = Regex("JNIEXPORT\\s+\\w+\\s+JNICALL\\s+(Java_\\w+)\\s*\\(([^)]*)\\)").findAll(cpp)
            .associate { it.groupValues[1] to paramCount(it.groupValues[2]) - 2 } // JNIEnv*, jobject
    }

    @Test fun praefixEntsprichtPaketUndObjekt() {
        assertEquals("Java_com_chris_vox_whisper_WhisperLib_", prefix)
        assertTrue(cppFuns.isNotEmpty())
        for (symbol in cppFuns.keys) assertTrue("$symbol ohne Praefix $prefix", symbol.startsWith(prefix))
    }

    @Test fun jedeExternalFunHatGenauEinSymbolMitGleicherParameterzahl() {
        assertTrue(ktFuns.isNotEmpty())
        assertEquals(ktFuns.keys, cppFuns.keys.map { it.removePrefix(prefix) }.toSet())
        for ((name, n) in ktFuns) assertEquals("Parameterzahl von $name", n, cppFuns[prefix + name])
    }

    @Test fun keinAssetLaderMehr() {
        // Modelle kommen nur per Download; ein Asset-Loader wuerde wieder ein Modell im APK nahelegen.
        assertFalse(cpp.contains("asset_manager"))
        assertFalse(ktFuns.containsKey("initContextFromAsset"))
    }
}
