package com.onenotes.app.data.model

/** Which "bucket" the list is currently showing. */
enum class NoteSection { NOTES, ARCHIVE, TRASH }

/** Sort order of the notes list. The code is what the DAO passes to the ORDER BY clause. */
enum class SortMode(val code: Int) {
    UPDATED(0),
    CREATED(1),
    TITLE(2),
    REMINDER(3);

    companion object {
        fun fromCode(code: Int): SortMode = entries.firstOrNull { it.code == code } ?: UPDATED
    }
}

/** Layout of the notes list. */
enum class ListLayout { LIST, GRID }

/**
 * Everything the notes list needs to know to build its query.
 */
data class NoteFilter(
    val section: NoteSection = NoteSection.NOTES,
    val folderId: Long? = null,
    val noFolder: Boolean = false,
    val tagId: Long? = null,
    val pinnedOnly: Boolean = false,
    val reminderOnly: Boolean = false,
    val query: String = "",
    val sort: SortMode = SortMode.UPDATED,
    val from: Long? = null,
    val to: Long? = null
) {
    val archived: Boolean get() = section == NoteSection.ARCHIVE

    /** True when the drawer selection is the "all notes" entry. */
    val isPlain: Boolean
        get() = section == NoteSection.NOTES && folderId == null && !noFolder && tagId == null &&
            !pinnedOnly && !reminderOnly && from == null && to == null
}
