package com.onenotes.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.onenotes.app.data.model.NoteTagCrossRef
import com.onenotes.app.data.model.TagEntity
import com.onenotes.app.data.model.TagWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(ref: NoteTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<NoteTagCrossRef>)

    @Query("DELETE FROM note_tags WHERE note_id = :noteId")
    suspend fun clearTags(noteId: Long)

    @Query("DELETE FROM note_tags WHERE tag_id = :tagId")
    suspend fun clearTag(tagId: Long)

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<TagEntity>

    @Query(
        """
        SELECT tags.*,
            (SELECT COUNT(*) FROM notes
               INNER JOIN note_tags nt ON nt.note_id = notes.id
              WHERE nt.tag_id = tags.id
                AND notes.deleted = 0
                AND notes.archived = 0) AS note_count
          FROM tags
         ORDER BY tags.name COLLATE NOCASE ASC
    """
    )
    fun observeWithCount(): Flow<List<TagWithCount>>

    @Query(
        """
        SELECT tags.* FROM tags
          INNER JOIN note_tags nt ON nt.tag_id = tags.id
         WHERE nt.note_id = :noteId
         ORDER BY tags.name COLLATE NOCASE ASC
    """
    )
    fun observeTagsForNote(noteId: Long): Flow<List<TagEntity>>

    @Query(
        """
        SELECT tags.* FROM tags
          INNER JOIN note_tags nt ON nt.tag_id = tags.id
         WHERE nt.note_id = :noteId
         ORDER BY tags.name COLLATE NOCASE ASC
    """
    )
    suspend fun tagsForNote(noteId: Long): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun getByName(name: String): TagEntity?

    /** Replaces the tags of a note with the given set of identifiers. */
    @Transaction
    suspend fun setTagsForNote(noteId: Long, tagIds: Collection<Long>) {
        clearTags(noteId)
        insertCrossRefs(tagIds.map { NoteTagCrossRef(noteId = noteId, tagId = it) })
    }

    @Transaction
    suspend fun deleteTag(tagId: Long) {
        clearTag(tagId)
        getById(tagId)?.let { delete(it) }
    }

    @Query("SELECT * FROM tags WHERE id = :tagId")
    suspend fun getById(tagId: Long): TagEntity?
}
