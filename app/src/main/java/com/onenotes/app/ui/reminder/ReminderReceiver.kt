package com.onenotes.app.ui.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onenotes.app.OneNotesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fired by the AlarmManager when the reminder of a note is due. */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(NotificationHelper.EXTRA_NOTE_ID, -1L)
        if (noteId <= 0L) return

        val pending = goAsync()
        val container = OneNotesApp.containerOf(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_MARK_DONE) {
                    container.notes.markReminderDone(noteId)
                    NotificationHelper.cancelReminder(context, noteId)
                    return@launch
                }
                val note = container.notes.getNote(noteId)
                val reminderAt = note?.reminderAt
                if (note == null || note.deleted || reminderAt == null || note.reminderDone) {
                    NotificationHelper.cancelReminder(context, noteId)
                    return@launch
                }
                NotificationHelper.showReminder(
                    context = context,
                    noteId = noteId,
                    title = note.title.ifBlank { context.getString(R.string.notification_reminder_title) },
                    body = note.plainText
                )
                container.reminders.cancel(noteId)
                container.notes.markReminderDone(noteId)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMINDER = "com.onenotes.app.action.REMINDER"
        const val ACTION_MARK_DONE = "com.onenotes.app.action.MARK_DONE"
    }
}
