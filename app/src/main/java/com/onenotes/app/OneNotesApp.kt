package com.onenotes.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.onenotes.app.data.repository.SettingsRepository
import com.onenotes.app.di.AppContainer
import com.onenotes.app.ui.reminder.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Application entry point. It only builds the (tiny) dependency container, creates the
 * notification channels and applies the theme chosen by the user, everything else is lazy so
 * that the cold start stays fast.
 */
class OneNotesApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        NotificationHelper.createChannels(this)

        appScope.launch {
            container.settings.themeMode
                .distinctUntilChanged()
                .collect { mode ->
                    AppCompatDelegate.setDefaultNightMode(
                        when (mode) {
                            SettingsRepository.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                            SettingsRepository.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                        }
                    )
                }
        }

        // Housekeeping: it runs off the critical path of the startup.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                container.notes.purgeExpiredTrash()
                container.notes.cleanupAttachments()
                container.reminders.rescheduleAll()
            }
        }
    }

    companion object {
        fun containerOf(context: android.content.Context): AppContainer =
            (context.applicationContext as OneNotesApp).container
    }
}
