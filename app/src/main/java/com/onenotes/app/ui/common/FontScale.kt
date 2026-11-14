package com.onenotes.app.ui.common

import android.content.Context
import android.content.res.Configuration
import com.onenotes.app.OneNotesApp

/**
 * Applies the font size chosen by the user in the settings (0.85x, 1x, 1.15x, 1.3x).
 *
 * Every text of the app is expressed in sp, so changing the scale of the configuration is enough
 * to enlarge or shrink the whole interface without touching a single layout.
 */
object FontScale {

    fun apply(base: Context): Context {
        val scale = runCatching { OneNotesApp.containerOf(base).settings.fontScaleNow() }
            .getOrDefault(1f)
        if (scale <= 0f || scale == 1f) return base
        val configuration = Configuration(base.resources.configuration)
        configuration.fontScale = scale
        return base.createConfigurationContext(configuration)
    }
}
