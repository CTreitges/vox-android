package com.chris.whisperbar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Review SEC-1: die Einstellungen enthalten API-Keys im Klartext. Die App verspricht "Dein Key bleibt
 * auf dem Geraet" — also darf das Manifest kein Auto-Backup (Google Drive, D2D-Transfer) erlauben.
 */
class ManifestBackupTest {

    private fun manifest(): File =
        listOf("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml").map { File(it) }.first { it.exists() }

    private fun xml(name: String): File = File(manifest().parentFile, "res/xml/$name.xml")

    @Test fun autoBackupIstAbgeschaltet() {
        val application = manifest().readText().substringAfter("<application").substringBefore(">")
        assertTrue("allowBackup=\"false\" fehlt: $application", application.contains("android:allowBackup=\"false\""))
        assertTrue(application.contains("android:fullBackupContent=\"@xml/backup_rules\""))
        assertTrue(application.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""))
    }

    @Test fun backupRegelnSchliessenDiePrefsDateiAus() {
        // Falls allowBackup kuenftig ignoriert wird: die Prefs-Datei (api_key, llm_key) bleibt ausgeschlossen —
        // fuer Cloud-Backup UND Geraete-Transfer.
        val exclude = "<exclude domain=\"sharedpref\" path=\"whisperbar.xml\" />"
        assertTrue(xml("backup_rules").readText().contains(exclude))
        val rules = xml("data_extraction_rules").readText()
        assertTrue(rules.substringAfter("<cloud-backup").substringBefore("</cloud-backup>").contains(exclude))
        assertTrue(rules.substringAfter("<device-transfer").substringBefore("</device-transfer>").contains(exclude))
    }
}
