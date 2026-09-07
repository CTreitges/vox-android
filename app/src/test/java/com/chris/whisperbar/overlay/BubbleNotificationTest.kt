package com.chris.whisperbar.overlay

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chris.whisperbar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Akzeptanz N1: Silhouette als Small-Icon, Farbe je Zustand, Beenden-Aktion, Tipp -> Home. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BubbleNotificationTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Test fun smallIconIstDieSilhouette() {
        BubbleNotification.ensureChannel(ctx)
        val n = BubbleNotification.build(ctx, BubbleState.IDLE)
        assertEquals(R.drawable.ic_stat_whisperbar, n.smallIcon.resId)
    }

    @Test fun contentIntentUndBeendenAktionSindGesetzt() {
        val n = BubbleNotification.build(ctx, BubbleState.IDLE)
        assertNotNull("contentIntent (Tipp -> Home) fehlt", n.contentIntent)
        assertEquals(1, n.actions.size)
        assertEquals(ctx.getString(R.string.float_stop), n.actions[0].title.toString())
        assertEquals(R.drawable.ic_stop, n.actions[0].getIcon().resId)
        assertNotNull(n.actions[0].actionIntent)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(BubbleNotification.CHANNEL_ID, n.channelId)
        assertEquals(ctx.getString(R.string.float_running), n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test fun farbeFolgtDerAufnahme() {
        assertEquals(ctx.getColor(R.color.wb_primary), BubbleNotification.build(ctx, BubbleState.IDLE).color)
        assertEquals(ctx.getColor(R.color.wb_primary), BubbleNotification.build(ctx, BubbleState.SENDING).color)
        assertEquals(ctx.getColor(R.color.wb_recording), BubbleNotification.build(ctx, BubbleState.RECORDING).color)
    }
}
