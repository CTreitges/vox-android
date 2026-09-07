package com.chris.vox.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.chris.vox.AppNav
import com.chris.vox.R

/**
 * Foreground-Notification des schwebenden Knopfs (UX-Spec §5.5, N1): weisse Silhouette als
 * Small-Icon, Akzentfarbe vox_primary (waehrend RECORDING vox_recording), Aktion "Beenden",
 * Tipp oeffnet Home. Als eigenes Objekt, damit der Builder ohne laufenden Service testbar ist.
 */
object BubbleNotification {

    const val ID = 42
    const val CHANNEL_ID = "vox_float"

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, ctx.getString(R.string.float_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun build(ctx: Context, state: BubbleState): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val stop = PendingIntent.getService(
            ctx, 1,
            Intent(ctx, FloatingMicService::class.java).setAction(FloatingMicService.ACTION_STOP),
            flags,
        )
        val open = PendingIntent.getActivity(ctx, 2, AppNav.home(ctx), flags)
        val color = ctx.getColor(if (state == BubbleState.RECORDING) R.color.vox_recording else R.color.vox_primary)
        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(ctx, R.drawable.ic_stop), ctx.getString(R.string.float_stop), stop,
        ).build()
        return Notification.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vox)
            .setColor(color)
            .setContentTitle(ctx.getString(R.string.float_running))
            .setContentText(ctx.getString(R.string.float_running_text))
            .setContentIntent(open)
            .addAction(stopAction)
            .setOngoing(true)
            .build()
    }
}
