package com.chris.whisperbar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream

/**
 * End-to-End-Test der lokalen Whisper-Pipeline auf einem echten (Emulator-)Gerät:
 * laedt das gebuendelte ggml-tiny-Modell, transkribiert das kanonische jfk.wav
 * und prueft, dass echter Text herauskommt. Beweist JNI + native lib + Modell.
 *
 * jfk.wav (16 kHz mono) liegt als androidTest-Asset bei; gesprochen:
 * "And so my fellow Americans, ask not what your country can do for you ..."
 */
@RunWith(AndroidJUnit4::class)
class WhisperTranscriptionTest {

    @Test
    fun nativeLibraryLoadsAndReportsInfo() {
        val info = WhisperContext.systemInfo()
        assertTrue("Erwarte nicht-leere System-Info von whisper.cpp, war: '$info'", info.isNotBlank())
    }

    @Test
    fun transcribesJfkSample() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val appContext = instrumentation.targetContext
        val testContext = instrumentation.context

        val samples = testContext.assets.open("jfk.wav").use { readWavPcm16Mono(it) }
        assertTrue(
            "jfk.wav sollte mehrere Sekunden @16kHz sein, war ${samples.size} Samples",
            samples.size > 16_000 * 5,
        )

        val whisper = WhisperContext.createFromAsset(appContext)
        val transcript = try {
            whisper.transcribe(samples, "en")
        } finally {
            whisper.release()
        }

        val normalized = transcript.lowercase().trim()
        // "country" kommt im Satz zweimal vor -> auch das kleine tiny-Modell trifft es zuverlaessig.
        assertTrue(
            "Transkript enthaelt nicht das erwartete Schluesselwort. Voller Text: '$transcript'",
            normalized.contains("country"),
        )
        assertTrue(
            "Transkript ist zu kurz/leer: '$transcript'",
            normalized.length > 20,
        )
    }

    /** Minimaler WAV-Parser: sucht den data-Chunk und liest PCM16-LE mono -> Float [-1,1]. */
    private fun readWavPcm16Mono(input: InputStream): FloatArray {
        val bytes = input.readBytes()
        require(bytes.size > 44) { "WAV zu klein" }
        require(bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte()) { "Kein RIFF-Header" }

        // Chunks ab Offset 12 durchgehen, bis "data".
        var offset = 12
        var dataOffset = -1
        var dataSize = 0
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = le32(bytes, offset + 4)
            if (id == "data") {
                dataOffset = offset + 8
                dataSize = size
                break
            }
            offset += 8 + size + (size and 1) // Chunks sind wort-ausgerichtet
        }
        require(dataOffset >= 0) { "Kein data-Chunk gefunden" }

        val end = minOf(dataOffset + dataSize, bytes.size)
        val sampleCount = (end - dataOffset) / 2
        val out = FloatArray(sampleCount)
        var p = dataOffset
        for (i in 0 until sampleCount) {
            val lo = bytes[p].toInt() and 0xFF
            val hi = bytes[p + 1].toInt() // Vorzeichen erhalten
            out[i] = ((hi shl 8) or lo) / 32768f
            p += 2
        }
        return out
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
