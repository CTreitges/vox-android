package com.chris.whisperbar

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Deep-Link-Intents aus Overlay/IME/Notification (UX-Spec §1.2): route + step + NEW_TASK. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    private fun newTask(i: Intent) = i.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0

    @Test fun homeZieltVorerstAufSetupActivityMitRouteHome() {
        val i = AppNav.home(ctx)
        assertEquals(SetupActivity::class.java.name, i.component?.className)
        assertEquals("home", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse(i.hasExtra(AppNav.EXTRA_STEP))
        assertTrue(newTask(i))
    }

    @Test fun setupOhneSchrittHatKeinStepExtra() {
        val i = AppNav.setup(ctx)
        assertEquals(SetupActivity::class.java.name, i.component?.className)
        assertEquals("setup", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertFalse(i.hasExtra(AppNav.EXTRA_STEP))
        assertTrue(newTask(i))
    }

    @Test fun setupMitSchrittTraegtStep() {
        val i = AppNav.setup(ctx, 3)
        assertEquals("setup", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertEquals(3, i.getIntExtra(AppNav.EXTRA_STEP, -1))
    }

    @Test fun settingsZieltVorerstAufSettingsActivity() {
        val i = AppNav.settings(ctx)
        assertEquals(SettingsActivity::class.java.name, i.component?.className)
        assertEquals("settings", i.getStringExtra(AppNav.EXTRA_ROUTE))
        assertTrue(newTask(i))
    }

    @Test fun extraNamenSindStabil() {
        // Das Compose-Paket liest genau diese Keys.
        assertEquals("route", AppNav.EXTRA_ROUTE)
        assertEquals("step", AppNav.EXTRA_STEP)
    }
}
