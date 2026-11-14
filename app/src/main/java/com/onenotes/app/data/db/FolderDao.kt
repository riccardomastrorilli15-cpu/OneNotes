package com.onenotes.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.onenotes.app.data.model.FolderEntity
import com.onenotes.app.data.model.FolderWithCount
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(folder: FolderEntity): Long

    @Update
    suspend fun update(folder: FolderEntity)

    @Delete
    suspend fun delete(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE id = :id")
    suspend fun getById(id: Long): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY position ASC, name COLLATE NOCASE ASC")
    suspend fun getAll(): List<FolderEntity>

    @Query("SELECT * FROM folders ORDER BY position ASC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query(
        """
        SELECT folders.*,
            (SELECT COUNT(*) FROM notes
              WHERE notes.folder_id = folders.id
                AND notes.deleted = 0
                AND notes.archived = 0) AS note_count
          FROM folders
         ORDER BY folders.position ASC, folders.name COLLATE NOCASE ASC
    """
    )
    fun observeWithCount(): Flow<List<FolderWithCount>>

    @Query("SELECT MAX(position) FROM folders")
    suspend fun getMaxPosition(): Int?

    @Query("SELECT COUNT(*) FROM notes WHERE folder_id = :folderId AND deleted = 0")
    suspend fun countNotes(folderId: Long): Int
}
