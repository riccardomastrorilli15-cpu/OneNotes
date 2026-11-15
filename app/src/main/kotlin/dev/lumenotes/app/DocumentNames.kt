package dev.lumenotes.app

/** Preserve names without an extension and hidden filenames when importing text. */
internal fun importTitle(displayName: String): String {
    val dot = displayName.lastIndexOf('.')
    return (if (dot > 0) displayName.substring(0, dot) else displayName).take(120)
}
