package com.chris.whisperloom.api

import com.chris.whisperloom.WavEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** JVM-Unit-Tests fuer den Rueckweg WAV-Upload -> Samples (Offline-Erkennung). */
class WavUploadTest {

    @Test fun diktatLiefertDieOriginalSamples() {
        val samples = floatArrayOf(0f, 0.5f, -0.5f, 1f)
        val upload = WavUpload.fromSamples(samples)
        assertSame(samples, upload.readSamples())
        assertEquals(8, upload.pcmByteCount)
        assertEquals(WavEncoder.HEADER_SIZE + 8, upload.totalBytes)
    }

    @Test fun gestreamtesPcmWirdZurueckgerechnet() {
        val samples = floatArrayOf(0f, 0.25f, -0.25f, 0.999f, -1f)
        val pcm = WavEncoder.pcmBytes(samples)
        val upload = WavUpload(pcmByteCount = pcm.size) { it.write(pcm) }
        val back = upload.readSamples()
        assertEquals(samples.size, back.size)
        for (i in samples.indices) assertEquals(samples[i], back[i], 2f / 32767f)
    }

    @Test fun pcmRoundtripIstVerlustarm() {
        val samples = FloatArray(100) { (it - 50) / 50f }
        val back = WavEncoder.samples(WavEncoder.pcmBytes(samples))
        for (i in samples.indices) assertEquals(samples[i], back[i], 2f / 32767f)
    }
}
