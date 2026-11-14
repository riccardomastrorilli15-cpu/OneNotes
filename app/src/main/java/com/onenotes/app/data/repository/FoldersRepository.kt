package com.onenotes.app.data.repository

import com.onenotes.app.data.db.AppDatabase
import com.onenotes.app.data.model.FolderEntity
import com.onenotes.app.data.model.FolderWithCount
import kotlinx.coroutines.flow.Flow

class FoldersRepository(private val db: AppDatabase) {

    private val folderDao = db.folderDao()
    private val noteDao = db.noteDao()

    fun observeAll(): Flow<List<FolderEntity>> = folderDao.observeAll()

    fun observeWithCount(): Flow<List<FolderWithCount>> = folderDao.observeWithCount()

    suspend fun create(name: String, color: Int = 0): Long {
        val position = (folderDao.getMaxPosition() ?: -1) + 1
        return folderDao.insert(FolderEntity(name = name.trim(), color = color, position = position))
    }

    suspend fun rename(id: Long, name: String) {
        val folder = folderDao.getById(id) ?: return
        folderDao.update(folder.copy(name = name.trim()))
    }

    suspend fun setColor(id: Long, color: Int) {
        val folder = folderDao.getById(id) ?: return
        folderDao.update(folder.copy(color = color))
    }

    /** Deletes a folder; its notes are not deleted, they simply end up without a folder. */
    suspend fun delete(id: Long) {
        val folder = folderDao.getById(id) ?: return
        noteDao.getAll().filter { it.folderId == id }.forEach { note ->
            noteDao.upsert(note.copy(folderId = null))
        }
        folderDao.delete(folder)
    }

    suspend fun getAll(): List<FolderEntity> = folderDao.getAll()

    suspend fun getByName(name: String): FolderEntity? =
        folderDao.getAll().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
}
