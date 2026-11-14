package dev.lumenotes.model

/** Stable public UUID plus a local SQLite row id. Neither represents a cloud account. */
data class Note(
    val id: Long = 0,
    val uuid: String,
    val title: String = "",
    val body: String = "",
    val folder: String = "",
    val tags: String = "",
    val drawing: String = "",
    val pinned: Boolean = false,
    val favorite: Boolean = false,
    val archived: Boolean = false,
    val trashedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val version: Long = 1
)

data class NoteDraft(val id: Long, val title: String, val body: String, val folder: String, val tags: String)

enum class NoteCollection(val databaseKey: String) {
    NOTES("notes"),
    FAVORITES("favorites"),
    ARCHIVE("archive"),
    TRASH("trash")
}

/** Small projection used by the library; full bodies and vector data stay in SQLite. */
data class NoteSummary(
    val id: Long,
    val title: String,
    val preview: String,
    val folder: String,
    val tags: String,
    val hasDrawing: Boolean,
    val pinned: Boolean,
    val favorite: Boolean,
    val archived: Boolean,
    val trashedAt: Long?,
    val updatedAt: Long
)
