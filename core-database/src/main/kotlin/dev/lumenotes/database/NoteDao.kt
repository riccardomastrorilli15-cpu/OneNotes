package dev.lumenotes.database

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import dev.lumenotes.model.NoteSummary
import kotlinx.coroutines.flow.Flow

private const val SUMMARY_COLUMNS = """
    n.id, n.title, substr(n.body, 1, 320) AS preview, n.folder, n.tags,
    CASE WHEN length(n.drawing) > 0 THEN 1 ELSE 0 END AS hasDrawing,
    n.pinned, n.favorite, n.archived, n.trashedAt, n.updatedAt
"""

private const val COLLECTION_FILTER = """
    ((:collection = 'notes' AND n.trashedAt IS NULL AND n.archived = 0)
    OR (:collection = 'favorites' AND n.trashedAt IS NULL AND n.archived = 0 AND n.favorite = 1)
    OR (:collection = 'archive' AND n.trashedAt IS NULL AND n.archived = 1)
    OR (:collection = 'trash' AND n.trashedAt IS NOT NULL))
    AND (:folder IS NULL OR n.folder = :folder)
"""

@Dao
interface NoteDao {
    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun find(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query(
        """
        SELECT $SUMMARY_COLUMNS FROM notes AS n WHERE $COLLECTION_FILTER
        ORDER BY n.pinned DESC, n.updatedAt DESC, n.id DESC LIMIT :limit
    """
    )
    fun observeCollection(collection: String, folder: String?, limit: Int): Flow<List<NoteSummary>>

    @Query(
        """
        SELECT $SUMMARY_COLUMNS FROM notes AS n INNER JOIN notes_fts ON n.id = notes_fts.rowid
        WHERE notes_fts MATCH :match AND $COLLECTION_FILTER
        ORDER BY n.pinned DESC, n.updatedAt DESC, n.id DESC LIMIT :limit
    """
    )
    fun search(collection: String, folder: String?, match: String, limit: Int): Flow<List<NoteSummary>>

    @Query(
        """
        SELECT DISTINCT folder FROM notes
        WHERE folder != '' AND trashedAt IS NULL
        ORDER BY folder COLLATE NOCASE
    """
    )
    fun observeFolders(): Flow<List<String>>

    @Query(
        """
        SELECT * FROM notes WHERE id != :id AND trashedAt IS NULL
        AND instr(body, :needle) > 0 ORDER BY updatedAt DESC LIMIT 100
    """
    )
    suspend fun backlinks(id: Long, needle: String): List<NoteEntity>

    // Partial updates avoid an autosave overwriting a pin, archive, or drawing change.
    @Query(
        """
        UPDATE notes SET title = :title, body = :body, folder = :folder, tags = :tags,
            updatedAt = :now, version = version + 1
        WHERE id = :id AND trashedAt IS NULL
    """
    )
    suspend fun updateContent(id: Long, title: String, body: String, folder: String, tags: String, now: Long): Int

    @Query(
        """
        UPDATE notes SET drawing = :drawing, updatedAt = :now, version = version + 1
        WHERE id = :id AND trashedAt IS NULL
    """
    )
    suspend fun updateDrawing(id: Long, drawing: String, now: Long): Int

    @Query("UPDATE notes SET pinned = :value, updatedAt = :now, version = version + 1 WHERE id = :id AND trashedAt IS NULL")
    suspend fun setPinned(id: Long, value: Boolean, now: Long): Int

    @Query("UPDATE notes SET favorite = :value, updatedAt = :now, version = version + 1 WHERE id = :id AND trashedAt IS NULL")
    suspend fun setFavorite(id: Long, value: Boolean, now: Long): Int

    @Query("UPDATE notes SET archived = :value, updatedAt = :now, version = version + 1 WHERE id = :id AND trashedAt IS NULL")
    suspend fun setArchived(id: Long, value: Boolean, now: Long): Int

    @Query("UPDATE notes SET trashedAt = :now, updatedAt = :now, version = version + 1 WHERE id = :id AND trashedAt IS NULL")
    suspend fun trash(id: Long, now: Long): Int

    @Query(
        "UPDATE notes SET trashedAt = NULL, archived = 0, updatedAt = :now, version = version + 1 WHERE id = :id AND trashedAt IS NOT NULL"
    )
    suspend fun restore(id: Long, now: Long): Int

    @Query("DELETE FROM notes WHERE id = :id AND trashedAt IS NOT NULL")
    suspend fun deletePermanently(id: Long): Int
}
