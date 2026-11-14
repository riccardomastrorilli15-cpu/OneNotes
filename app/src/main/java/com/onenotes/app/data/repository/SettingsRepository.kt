package com.onenotes.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Small preference wrapper. The settings screen writes the very same file with the standard
 * PreferenceFragmentCompat, so both sides always see the same values.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val themeMode: Flow<String> = observeString(KEY_THEME, THEME_SYSTEM)
    val listLayout: Flow<String> = observeString(KEY_LIST_LAYOUT, LAYOUT_LIST)
    val sortMode: Flow<Int> = observeInt(KEY_SORT_MODE, 0)
    val fontScale: Flow<Float> = observeFloat(KEY_FONT_SCALE, 1f)
    val penOnly: Flow<Boolean> = observeBoolean(KEY_PEN_ONLY, false)
    val appLock: Flow<Boolean> = observeBoolean(KEY_APP_LOCK, false)
    val driveAccount: Flow<String> = observeString(KEY_DRIVE_ACCOUNT, "")
    val lastBackupAt: Flow<Long> = observeLong(KEY_LAST_BACKUP, 0L)

    fun setTheme(mode: String) = put(KEY_THEME, mode)
    fun setListLayout(layout: String) = put(KEY_LIST_LAYOUT, layout)
    fun setSortMode(code: Int) = put(KEY_SORT_MODE, code)
    fun setFontScale(scale: Float) = put(KEY_FONT_SCALE, scale)
    fun setPenOnly(enabled: Boolean) = put(KEY_PEN_ONLY, enabled)
    fun setAppLock(enabled: Boolean) = put(KEY_APP_LOCK, enabled)
    fun setDriveAccount(account: String) = put(KEY_DRIVE_ACCOUNT, account)
    fun setLastBackupAt(value: Long) = put(KEY_LAST_BACKUP, value)

    /** Synchronous read of the font scale: needed while the base context of an activity is built. */
    fun fontScaleNow(): Float =
        when (val value = prefs.all[KEY_FONT_SCALE]) {
            is Float -> value
            is Int -> value.toFloat()
            is Long -> value.toFloat()
            is String -> value.toFloatOrNull() ?: 1f
            else -> 1f
        }

    // --------------------------------------------------------------- plumbing

    private fun put(key: String, value: Any?) {
        val editor = prefs.edit()
        when (value) {
            null -> editor.remove(key)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            else -> Unit
        }
        editor.apply()
    }

    private fun prefsFlow(): Flow<SharedPreferences> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(prefs)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(prefs)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private fun observeString(key: String, default: String): Flow<String> =
        prefsFlow().map { it.getString(key, default) ?: default }.distinctUntilChanged()

    private fun observeInt(key: String, default: Int): Flow<Int> =
        prefsFlow().map { it.getInt(key, default) }.distinctUntilChanged()

    private fun observeLong(key: String, default: Long): Flow<Long> =
        prefsFlow().map { it.getLong(key, default) }.distinctUntilChanged()

    /**
     * The font size comes from a ListPreference, which stores a string ("1.0"), while the app
     * works with a float: both are accepted so that the value can be written by the settings
     * screen and by the code.
     */
    private fun observeFloat(key: String, default: Float): Flow<Float> =
        prefsFlow().map { prefs ->
            when (val value = prefs.all[key]) {
                is Float -> value
                is Int -> value.toFloat()
                is Long -> value.toFloat()
                is String -> value.toFloatOrNull() ?: default
                else -> default
            }
        }.distinctUntilChanged()

    private fun observeBoolean(key: String, default: Boolean): Flow<Boolean> =
        prefsFlow().map { it.getBoolean(key, default) }.distinctUntilChanged()

    companion object {
        const val PREFS_NAME = "onenotes_settings"

        const val KEY_THEME = "theme_mode"
        const val KEY_LIST_LAYOUT = "list_layout"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_PEN_ONLY = "pen_only"
        const val KEY_APP_LOCK = "app_lock"
        const val KEY_DRIVE_ACCOUNT = "drive_account"
        const val KEY_LAST_BACKUP = "last_backup_at"

        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        const val THEME_SYSTEM = "system"

        const val LAYOUT_LIST = "list"
        const val LAYOUT_GRID = "grid"
    }
}
