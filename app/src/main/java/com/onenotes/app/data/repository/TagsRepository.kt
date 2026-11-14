package com.onenotes.app.data.repository

import com.onenotes.app.data.db.AppDatabase
import com.onenotes.app.data.model.TagEntity
import com.onenotes.app.data.model.TagWithCount
import kotlinx.coroutines.flow.Flow

class TagsRepository(private val db: AppDatabase) {

    private val tagDao = db.tagDao()

    fun observeAll(): Flow<List<TagEntity>> = tagDao.observeAll()

    fun observeWithCount(): Flow<List<TagWithCount>> = tagDao.observeWithCount()

    fun observeForNote(noteId: Long): Flow<List<TagEntity>> = tagDao.observeTagsForNote(noteId)

    suspend fun tagsForNote(noteId: Long): List<TagEntity> = tagDao.tagsForNote(noteId)

    suspend fun create(name: String, color: Int = 0): Long =
        tagDao.getByName(name.trim())?.id
            ?: tagDao.insert(TagEntity(name = name.trim(), color = color))

    suspend fun rename(id: Long, name: String) {
        val tag = tagDao.getById(id) ?: return
        tagDao.update(tag.copy(name = name.trim()))
    }

    suspend fun delete(id: Long) = tagDao.deleteTag(id)

    suspend fun getAll(): List<TagEntity> = tagDao.getAll()
}
