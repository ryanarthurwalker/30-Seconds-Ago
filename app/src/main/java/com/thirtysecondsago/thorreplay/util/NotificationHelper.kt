package com.thirtysecondsago.thorreplay.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.thirtysecondsago.thorreplay.MainActivity
import com.thirtysecondsago.thorreplay.R
import com.thirtysecondsago.thorreplay.capture.ReplayBufferService

object NotificationHelper {
    const val CAPTURE_CHANNEL_ID = "thor_replay_capture"
    const val EVENTS_CHANNEL_ID = "thor_replay_events"
    const val CAPTURE_NOTIFICATION_ID = 1001
    const val SAVE_RESULT_NOTIFICATION_ID = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CAPTURE_CHANNEL_ID,
                "Replay buffer",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while 30 Seconds Ago is buffering gameplay."
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                EVENTS_CHANNEL_ID,
                "Replay events",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Save confirmations and replay errors."
            }
        )
    }

    fun captureNotification(context: Context, active: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val saveIntent = PendingIntent.getService(
            context,
            2,
            ReplayBufferService.commandIntent(context, ReplayBufferService.ACTION_SAVE_REPLAY),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            3,
            ReplayBufferService.commandIntent(context, ReplayBufferService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CAPTURE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(if (active) "Replay buffer active" else "Replay buffer idle")
            .setContentText("Save Replay writes the most recent buffered gameplay.")
            .setOngoing(active)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_save, "Save Replay", saveIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop Buffer", stopIntent)
            .build()
    }

    fun notifySaveResult(context: Context, title: String, message: String) {
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, EVENTS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(context.getColor(R.color.notification_accent))
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(SAVE_RESULT_NOTIFICATION_ID, notification)
    }
}
