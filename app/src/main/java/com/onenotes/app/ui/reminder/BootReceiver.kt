package com.onenotes.app.ui.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.onenotes.app.OneNotesApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The AlarmManager forgets everything after a reboot: schedule the reminders again. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val pending = goAsync()
        val container = OneNotesApp.containerOf(context)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.reminders.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
