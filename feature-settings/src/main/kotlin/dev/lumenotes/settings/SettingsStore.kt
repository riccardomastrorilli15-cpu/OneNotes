package dev.lumenotes.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "lume_settings")

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val themeKey = stringPreferencesKey("theme")

    val theme: Flow<ThemeChoice> = store.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { prefs ->
            runCatching { ThemeChoice.valueOf(prefs[themeKey] ?: ThemeChoice.SYSTEM.name) }
                .getOrDefault(ThemeChoice.SYSTEM)
        }

    suspend fun setTheme(choice: ThemeChoice) {
        store.edit { it[themeKey] = choice.name }
    }
}
