package com.chris.vox.ui.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fortschrittsrechnung (fileIndex + chunk/chunks) / files — ohne Android. */
class ShareProgressTest {

    @Test fun anteilUeberDateienUndStuecke() {
        assertEquals(0f, ShareProgress.fraction(0, 1, 0, 1), 0f)
        assertEquals(0.25f, ShareProgress.fraction(0, 2, 1, 2), 0f)
        assertEquals(0.5f, ShareProgress.fraction(1, 2, 0, 1), 0f)
        assertEquals(1f, ShareProgress.fraction(1, 2, 2, 2), 0f)
        assertEquals(1f / 3f, ShareProgress.fraction(0, 1, 1, 3), 1e-6f)
    }

    @Test fun grenzfaelleBleibenImBereich() {
        assertEquals(0f, ShareProgress.fraction(0, 0, 0, 0), 0f)
        assertEquals(0f, ShareProgress.fraction(0, 1, 0, 0), 0f)
        assertEquals(1f, ShareProgress.fraction(0, 1, 5, 2), 0f)
        assertEquals(1f, ShareProgress.fraction(7, 2, 0, 1), 0f)
        assertEquals(0f, ShareProgress.fraction(-1, 2, 0, 1), 0f)
    }

    @Test fun anzeigeStueckNieGroesserAlsGesamt() {
        assertEquals(1, ShareProgress(0, 1, 0, 3, "x").displayChunk)
        assertEquals(3, ShareProgress(0, 1, 2, 3, "x").displayChunk)
        assertEquals(3, ShareProgress(0, 1, 3, 3, "x").displayChunk) // Abschluss-Aufruf step == total
        assertEquals(1, ShareProgress(0, 1, 0, 0, "x").displayChunk)
    }

    @Test fun nurStatusBeiEinerDateiUndEinemStueck() {
        assertTrue(ShareProgress(0, 1, 0, 1, "x").isSingle)
        assertFalse(ShareProgress(0, 2, 0, 1, "x").isSingle)
        assertFalse(ShareProgress(0, 1, 0, 2, "x").isSingle)
        assertEquals(0.5f, ShareProgress(0, 1, 1, 2, "x").fraction, 0f)
    }
}
