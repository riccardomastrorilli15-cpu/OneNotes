package com.onenotes.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single note.
 *
 * The body of the note is stored as a JSON document (see [com.onenotes.app.data.model.NoteBody]):
 * it keeps the list of blocks (text, headings, lists, checklists, images, audio, drawings,
 * attachments) together with the inline formatting of every block.
 *
 * [plainText] is a flattened version of the same content, used for the full text index and for
 * the previews shown in the list, so that the UI never has to parse the JSON.
 */
@Entity(
    tableName = "notes",
    indices = [
        Index(value = ["folder_id"]),
        Index(value = ["updated_at"]),
        Index(value = ["deleted"]),
        Index(value = ["archived"]),
        Index(value = ["reminder_at"])
    ]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "title")
    val title: String = "",

    @ColumnInfo(name = "content")
    val content: String = "",

    @ColumnInfo(name = "plain_text")
    val plainText: String = "",

    @ColumnInfo(name = "folder_id")
    val folderId: Long? = null,

    @ColumnInfo(name = "color")
    val color: Int = 0,

    @ColumnInfo(name = "pinned")
    val pinned: Boolean = false,

    @ColumnInfo(name = "archived")
    val archived: Boolean = false,

    @ColumnInfo(name = "locked")
    val locked: Boolean = false,

    @ColumnInfo(name = "deleted")
    val deleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,

    @ColumnInfo(name = "reminder_at")
    val reminderAt: Long? = null,

    @ColumnInfo(name = "reminder_done")
    val reminderDone: Boolean = false
)

/**
 * Full text index (FTS4) of the notes. The rowid is kept in sync with [NoteEntity.id] by
 * [com.onenotes.app.data.db.NoteDao], which is the only writer of this table.
 */
@Entity(tableName = "notes_fts")
@Fts4
data class NoteFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowId: Long,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "body")
    val body: String
)

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0,

    @ColumnInfo(name = "position")
    val position: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "color")
    val color: Int = 0
)

@Entity(
    tableName = "note_tags",
    primaryKeys = ["note_id", "tag_id"],
    indices = [Index(value = ["tag_id"])]
)
data class NoteTagCrossRef(
    @ColumnInfo(name = "note_id")
    val noteId: Long,

    @ColumnInfo(name = "tag_id")
    val tagId: Long
)

/**
 * Row returned by the list queries: the note plus the comma separated list of its tags.
 */
data class NoteListItem(
    @androidx.room.Embedded
    val note: NoteEntity,

    @ColumnInfo(name = "tag_names")
    val tagNames: String? = null
)

/**
 * A folder with the number of notes it contains (used by the navigation drawer).
 */
data class FolderWithCount(
    @androidx.room.Embedded
    val folder: FolderEntity,

    @ColumnInfo(name = "note_count")
    val noteCount: Int = 0
)

/**
 * A tag with the number of notes using it.
 */
data class TagWithCount(
    @androidx.room.Embedded
    val tag: TagEntity,

    @ColumnInfo(name = "note_count")
    val noteCount: Int = 0
)
