package com.onenotes.app.ui.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.onenotes.app.R
import com.onenotes.app.ui.editor.EditorActivity

/** Everything related to the reminder notification. */
object NotificationHelper {

    const val CHANNEL_REMINDERS = "onenotes_reminders"
    const val EXTRA_NOTE_ID = "extra_note_id"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            context.getString(R.string.notification_channel_reminders),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notification_channel_reminders_desc)
            enableVibration(true)
            enableLights(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun showReminder(context: Context, noteId: Long, title: String, body: String) {
        val openIntent = EditorActivity.newIntent(context, noteId).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context, noteId.toInt(), openIntent, pendingFlags()
        )
        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_MARK_DONE
            putExtra(EXTRA_NOTE_ID, noteId)
        }
        val donePending = PendingIntent.getBroadcast(
            context, noteId.toInt() + 1_000_000, doneIntent, pendingFlags()
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle(title.ifBlank { context.getString(R.string.notification_reminder_title) })
            .setContentText(body.ifBlank { context.getString(R.string.notification_reminder_title) })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPending)
            .addAction(
                R.drawable.ic_check,
                context.getString(R.string.notification_action_done),
                donePending
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(noteId.toInt(), notification)
        }
    }

    fun cancelReminder(context: Context, noteId: Long) {
        NotificationManagerCompat.from(context).cancel(noteId.toInt())
    }

    private fun pendingFlags(): Int {
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return flags
    }
}
