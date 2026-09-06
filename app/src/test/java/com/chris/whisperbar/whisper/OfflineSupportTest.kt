package com.chris.whisperbar.whisper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reine JVM-Tests fuer die CPU-/RAM-Heuristiken (kein Geraet noetig). */
class OfflineSupportTest {

    private val cpuinfoA76 = """
        processor	: 0
        BogoMIPS	: 38.40
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
        CPU implementer	: 0x41
        CPU architecture: 8

        processor	: 1
        Features	: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp
    """.trimIndent()

    private val cpuinfoA53 = "processor\t: 0\nFeatures\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 cpuid\n"

    @Test fun parseVereinigtFeaturesAllerKerne() {
        val f = OfflineSupport.parseCpuInfo(cpuinfoA76)
        assertTrue(f.containsAll(setOf("fp", "asimd", "fphp", "asimddp", "lrcpc")))
        assertFalse(f.contains("Features"))
        assertFalse(f.contains(""))
        assertTrue(OfflineSupport.supportsWhisper(f))
    }

    @Test fun armv8_0OhneFp16UndDotprodNichtUnterstuetzt() {
        val f = OfflineSupport.parseCpuInfo(cpuinfoA53)
        assertTrue(f.contains("asimd"))
        assertFalse(OfflineSupport.supportsWhisper(f))
        assertFalse("nur eins von beiden", OfflineSupport.supportsWhisper(setOf("fphp")))
        assertFalse(OfflineSupport.supportsWhisper(emptySet()))
        assertEquals(emptySet<String>(), OfflineSupport.parseCpuInfo(""))
    }

    @Test fun performanceKerneNachMaximaltakt() {
        assertEquals(0, OfflineSupport.performanceCoreCount(emptyList()))
        assertEquals("alle gleich -> alle", 8, OfflineSupport.performanceCoreCount(List(8) { 2_000_000L }))
        // 4x A55 + 3x A76 + 1x A76 prime -> 4 Performance-Kerne
        assertEquals(4, OfflineSupport.performanceCoreCount(listOf(1_800_000L, 1_800_000L, 1_800_000L, 1_800_000L, 2_400_000L, 2_400_000L, 2_400_000L, 2_840_000L)))
        // 6 kleine + 2 grosse -> 2
        assertEquals(2, OfflineSupport.performanceCoreCount(listOf(1_700_000L, 1_700_000L, 1_700_000L, 1_700_000L, 1_700_000L, 1_700_000L, 2_200_000L, 2_200_000L)))
    }

    @Test fun ramToleranzFuerLarge() {
        val large = ModelCatalog.LARGE_V3_TURBO
        val gib = 1024L * 1024 * 1024
        assertTrue(OfflineSupport.fitsDevice(6 * gib, large))
        assertTrue("5,6 GiB gemeldet auf einem 6-GB-Geraet", OfflineSupport.fitsDevice(56 * gib / 10, large))
        assertFalse(OfflineSupport.fitsDevice(5 * gib, large))
        assertFalse(OfflineSupport.fitsDevice(4 * gib, large))
        assertTrue(OfflineSupport.fitsDevice(2 * gib, ModelCatalog.SMALL))
        assertTrue(OfflineSupport.fitsDevice(0, ModelCatalog.TINY))
    }
}
