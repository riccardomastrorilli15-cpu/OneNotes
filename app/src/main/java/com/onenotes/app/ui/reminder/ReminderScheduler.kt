package com.onenotes.app.ui.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.repository.NotesRepository

/**
 * Schedules the reminders with [AlarmManager], which is the only way to wake the app up even
 * when the device is in doze mode. On Android 12+ the manifest declares
 * SCHEDULE_EXACT_ALARM, so exact alarms are used whenever the user granted it.
 */
class ReminderScheduler(
    private val context: Context,
    private val notes: NotesRepository
) {

    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll() {
        val upcoming = notes.getNotesWithReminders()
            .filter { (it.reminderAt ?: 0L) > System.currentTimeMillis() }
        upcoming.forEach { schedule(it) }
    }

    fun schedule(note: NoteEntity) {
        val at = note.reminderAt ?: return
        if (at <= System.currentTimeMillis()) return
        val manager = alarmManager ?: return
        val pi = pendingIntent(note.id)
        try {
            if (canScheduleExact()) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to schedule the reminder", t)
        }
    }

    fun cancel(noteId: Long) {
        val manager = alarmManager ?: return
        runCatching { manager.cancel(pendingIntent(noteId)) }
        NotificationHelper.cancelReminder(context, noteId)
    }

    fun canScheduleExact(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = alarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }

    private fun pendingIntent(noteId: Long): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_REMINDER
            putExtra(NotificationHelper.EXTRA_NOTE_ID, noteId)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, noteId.toInt(), intent, flags)
    }

    companion object {
        private const val TAG = "ReminderScheduler"
    }
}
