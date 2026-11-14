package dev.lumenotes.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import dagger.hilt.android.HiltAndroidApp
import dev.lumenotes.settings.SettingsStore
import dev.lumenotes.settings.ThemeChoice
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class LumeApplication : Application() {
    @Inject lateinit var settings: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            settings.theme.distinctUntilChanged().collect { theme ->
                val mode = when (theme) {
                    ThemeChoice.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                    ThemeChoice.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    ThemeChoice.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                }
                AppCompatDelegate.setDefaultNightMode(mode)
            }
        }
    }
}
