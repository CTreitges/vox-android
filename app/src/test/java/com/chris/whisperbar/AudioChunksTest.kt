package com.chris.whisperbar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM-Unit-Tests fuer die Stueckelung langer Aufnahmen. Ein Schnitt an der falschen
 * Stelle kostet an jeder Naht Silben — deshalb wird das Finden der leisen Stelle
 * hier genau festgenagelt.
 */
class AudioChunksTest {

    @Test fun kurzeAufnahmeBleibtEinStueck() {
        val c = AudioChunks.plan(totalFrames = 1000, maxFrames = 5000)
        assertEquals(listOf(AudioChunks.Chunk(0, 1000)), c)
    }

    @Test fun genauAufDerGrenzeBleibtEinStueck() {
        assertEquals(listOf(AudioChunks.Chunk(0, 5000)), AudioChunks.plan(5000, 5000))
    }

    @Test fun leereAufnahmeErgibtNichts() {
        assertTrue(AudioChunks.plan(0, 5000).isEmpty())
    }

    @Test fun ohneProfilWirdSturGeschnitten() {
        val c = AudioChunks.plan(totalFrames = 250, maxFrames = 100)
        assertEquals(listOf(AudioChunks.Chunk(0, 100), AudioChunks.Chunk(100, 200), AudioChunks.Chunk(200, 250)), c)
    }

    @Test fun stueckeSindLueckenlosUndUeberlappungsfrei() {
        val c = AudioChunks.plan(totalFrames = 1_234, maxFrames = 100)
        assertEquals(0, c.first().startFrame)
        assertEquals(1_234, c.last().endFrame)
        for (i in 1 until c.size) {
            assertEquals(c[i - 1].endFrame, c[i].startFrame)
        }
        assertEquals(1_234, c.sumOf { it.frameCount })
    }

    @Test fun schneidetAnDerLeisestenStelle() {
        // 10 Frames je Profil-Eintrag; Eintrag 7 (Frames 70..79) ist die Sprechpause.
        val profile = FloatArray(20) { 0.5f }
        profile[7] = 0.01f
        val c = AudioChunks.plan(
            totalFrames = 200, maxFrames = 100, profile = profile,
            framesPerEntry = 10, searchFrames = 50,
        )
        // Mitte des leisesten Eintrags = 7*10 + 5 = 75
        assertEquals(75, c[0].endFrame)
        assertEquals(75, c[1].startFrame)
    }

    @Test fun leiseStelleAusserhalbDesFenstersWirdIgnoriert() {
        // Pause bei Eintrag 1 (Frames 10..19) liegt weit vor dem Suchfenster [80,100].
        val profile = FloatArray(20) { 0.5f }
        profile[1] = 0.01f
        val c = AudioChunks.plan(
            totalFrames = 200, maxFrames = 100, profile = profile,
            framesPerEntry = 10, searchFrames = 20,
        )
        assertEquals(100, c[0].endFrame)
    }

    @Test fun ohneMerklichLeisereStelleWirdDieVolleLaengeGenutzt() {
        // Durchgehendes Reden: es gibt keine gute Naht -> nicht willkuerlich frueh schneiden.
        val profile = FloatArray(40) { 0.5f }
        val c = AudioChunks.plan(
            totalFrames = 250, maxFrames = 100, profile = profile,
            framesPerEntry = 10, searchFrames = 50,
        )
        assertEquals(100, c[0].endFrame)
        assertEquals(200, c[1].endFrame)
    }

    @Test fun fruehePauseErzeugtKeineWinzStuecke() {
        // Regression: ohne Untergrenze schnitt eine Pause am Stueckanfang bei Frame 5,
        // dann bei 6, dann bei 7 ... — hunderte Stuecke, jedes eine eigene API-Anfrage.
        val profile = FloatArray(40) { 0.5f }
        profile[0] = 0.0f
        val c = AudioChunks.plan(
            totalFrames = 300, maxFrames = 100, profile = profile,
            framesPerEntry = 10, searchFrames = 500,
        )
        assertEquals(3, c.size)
        for (chunk in c) {
            assertTrue("Stueck zu kurz: $chunk", chunk.frameCount >= 50)
        }
        assertEquals(300, c.sumOf { it.frameCount })
    }

    @Test fun schnittNiemalsVorDerHalbenHoechstlaenge() {
        val profile = FloatArray(40) { 0.5f }
        profile[1] = 0.0f // Pause bei Frames 10..19, also im ersten Viertel
        val c = AudioChunks.plan(
            totalFrames = 300, maxFrames = 100, profile = profile,
            framesPerEntry = 10, searchFrames = 200,
        )
        assertTrue("Erstes Stueck darf nicht vor Frame 50 enden: ${c[0]}", c[0].endFrame >= 50)
    }
}
