package com.chris.vox.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine JVM-Tests fuer Fortschritts-Rechnung und die prozessweite Zustands-Map. */
class DownloadStateTest {

    @Test fun prozentRechnung() {
        assertEquals(0, percentOf(0, 0))
        assertEquals(0, percentOf(5, 0))
        assertEquals(50, percentOf(50, 100))
        assertEquals(100, percentOf(150, 100))
        assertEquals(33, DownloadState.Running(1, 3, 0).percent)
        assertEquals(99, percentOf(190_085_486L, 190_085_487L))
    }

    @Test fun zustaendeProModell() {
        ModelDownloads.clear("x")
        ModelDownloads.clear("y")
        assertEquals(DownloadState.Idle, ModelDownloads.stateOf("x"))
        assertFalse(ModelDownloads.isAnyRunning)

        ModelDownloads.update("x", DownloadState.Running(1, 2, 3))
        assertTrue(ModelDownloads.isAnyRunning)
        assertEquals(DownloadState.Running(1, 2, 3), ModelDownloads.states.value["x"])

        ModelDownloads.update("y", DownloadState.Failed("kaputt", retryable = true))
        assertEquals(setOf("x", "y"), ModelDownloads.states.value.keys)

        ModelDownloads.update("x", DownloadState.Done)
        assertFalse(ModelDownloads.isAnyRunning)
        assertEquals(DownloadState.Done, ModelDownloads.stateOf("x"))

        ModelDownloads.clear("x")
        ModelDownloads.clear("y")
        assertTrue(ModelDownloads.states.value.isEmpty())
    }
}
