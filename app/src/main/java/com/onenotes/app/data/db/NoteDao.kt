package com.onenotes.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.model.NoteFtsEntity
import com.onenotes.app.data.model.NoteListItem
import kotlinx.coroutines.flow.Flow

private const val SELECT_NOTE_LIST = """
    SELECT notes.*,
        (SELECT group_concat(t.name, ', ')
           FROM tags t
           INNER JOIN note_tags nt ON nt.tag_id = t.id
          WHERE nt.note_id = notes.id) AS tag_names
      FROM notes
"""

/**
 * Notes are never physically removed until the trash is emptied: deleting a note only flips the
 * [NoteEntity.deleted] flag, which keeps the "undo" action instant.
 */
@Dao
interface NoteDao {

    // ------------------------------------------------------------- mutations

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFts(entry: NoteFtsEntity)

    @Query("DELETE FROM notes_fts WHERE rowid = :rowId")
    suspend fun deleteFts(rowId: Long)

    /**
     * Inserts or updates a note and keeps the full text index in sync, all in one transaction.
     */
    @Transaction
    suspend fun upsert(note: NoteEntity): Long {
        val id = if (note.id == 0L) insert(note) else {
            update(note)
            note.id
        }
        insertFts(NoteFtsEntity(rowId = id, title = note.title, body = note.plainText))
        return id
    }

    @Transaction
    suspend fun deletePermanently(note: NoteEntity) {
        deleteFts(note.id)
        delete(note)
    }

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Transaction
    suspend fun deleteByIdsWithIndex(ids: List<Long>) {
        ids.forEach { deleteFts(it) }
        deleteByIds(ids)
    }

    // --------------------------------------------------------------- reading

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deleted = 0")
    suspend fun getAllActive(): List<NoteEntity>

    /** The most recent notes: used by the home screen widget. */
    @Query(
        SELECT_NOTE_LIST + """
        WHERE notes.deleted = 0 AND notes.archived = 0
        ORDER BY notes.pinned DESC, notes.updated_at DESC
        LIMIT :limit
    """
    )
    suspend fun getRecent(limit: Int): List<NoteListItem>

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observeById(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE reminder_at IS NOT NULL AND reminder_done = 0 AND deleted = 0")
    suspend fun getNotesWithReminders(): List<NoteEntity>

    /**
     * The main list query. An optional folder, an optional time window and the sort mode are
     * passed as parameters so that a single statement covers every filter of the UI.
     */
    @Query(
        SELECT_NOTE_LIST + """
        WHERE notes.deleted = 0
          AND notes.archived = :archived
          AND (:folderId IS NULL OR notes.folder_id = :folderId)
          AND (:from IS NULL OR notes.updated_at >= :from)
          AND (:to IS NULL OR notes.updated_at <= :to)
        ORDER BY notes.pinned DESC,
            CASE :sort
                WHEN 0 THEN -notes.updated_at
                WHEN 1 THEN -notes.created_at
                WHEN 3 THEN COALESCE(notes.reminder_at, 253402300799000)
                ELSE 0
            END,
            CASE :sort WHEN 2 THEN notes.title ELSE '' END,
            notes.id DESC
    """
    )
    fun observeNotes(
        archived: Boolean,
        folderId: Long?,
        from: Long?,
        to: Long?,
        sort: Int
    ): Flow<List<NoteListItem>>

    @Query(
        SELECT_NOTE_LIST + """
        WHERE notes.deleted = 0
          AND notes.archived = :archived
          AND notes.folder_id IS NULL
          AND (:from IS NULL OR notes.updated_at >= :from)
          AND (:to IS NULL OR notes.updated_at <= :to)
        ORDER BY notes.pinned DESC,
            CASE :sort
                WHEN 0 THEN -notes.updated_at
                WHEN 1 THEN -notes.created_at
                WHEN 3 THEN COALESCE(notes.reminder_at, 253402300799000)
                ELSE 0
            END,
            CASE :sort WHEN 2 THEN notes.title ELSE '' END,
            notes.id DESC
    """
    )
    fun observeNotesWithoutFolder(
        archived: Boolean,
        from: Long?,
        to: Long?,
        sort: Int
    ): Flow<List<NoteListItem>>

    @Query(
        SELECT_NOTE_LIST + """
        INNER JOIN note_tags nt ON nt.note_id = notes.id
        WHERE notes.deleted = 0
          AND notes.archived = :archived
          AND nt.tag_id = :tagId
        ORDER BY notes.pinned DESC, -notes.updated_at, notes.id DESC
    """
    )
    fun observeNotesByTag(tagId: Long, archived: Boolean): Flow<List<NoteListItem>>

    @Query(
        SELECT_NOTE_LIST + """
        WHERE notes.deleted = 1
        ORDER BY notes.deleted_at DESC, notes.id DESC
    """
    )
    fun observeDeletedNotes(): Flow<List<NoteListItem>>

    /**
     * Full text search. [match] is a FTS4 MATCH expression built by the repository.
     */
    @Query(
        SELECT_NOTE_LIST + """
        INNER JOIN notes_fts ON notes.id = notes_fts.rowid
        WHERE notes_fts MATCH :match
          AND notes.deleted = 0
          AND (:archived IS NULL OR notes.archived = :archived)
        ORDER BY notes.pinned DESC, -notes.updated_at, notes.id DESC
    """
    )
    fun searchNotes(match: String, archived: Boolean?): Flow<List<NoteListItem>>

    // ---------------------------------------------------------------- counts

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND archived = 0 AND folder_id = :folderId")
    fun observeFolderCount(folderId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND archived = 0")
    fun observeAllCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND archived = 0 AND pinned = 1")
    fun observePinnedCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND archived = 1")
    fun observeArchiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 1")
    fun observeTrashCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notes WHERE deleted = 0 AND reminder_at IS NOT NULL AND reminder_done = 0")
    fun observeReminderCount(): Flow<Int>

    /** Notes deleted more than 30 days ago are removed for good. */
    @Query("SELECT * FROM notes WHERE deleted = 1 AND deleted_at IS NOT NULL AND deleted_at < :before")
    suspend fun getExpiredDeleted(before: Long): List<NoteEntity>

    @Query("SELECT MIN(created_at) FROM notes")
    suspend fun getOldestCreation(): Long?
}
