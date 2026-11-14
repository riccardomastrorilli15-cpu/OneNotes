package dev.lumenotes.data

import dev.lumenotes.database.NoteDao
import dev.lumenotes.database.toEntity
import dev.lumenotes.database.toModel
import dev.lumenotes.model.Note
import dev.lumenotes.model.NoteCollection
import dev.lumenotes.model.NoteDraft
import dev.lumenotes.model.NoteSummary
import dev.lumenotes.search.FtsQuery
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class NotesRepository(private val dao: NoteDao) {
    fun observeCollection(collection: NoteCollection, search: String, folder: String?, limit: Int): Flow<List<NoteSummary>> {
        val flow = if (search.isBlank()) {
            dao.observeCollection(collection.databaseKey, folder, limit)
        } else {
            val query = FtsQuery.fromUserInput(search) ?: return flowOf(emptyList())
            dao.search(collection.databaseKey, folder, query, limit)
        }
        return flow
    }

    fun observeNote(id: Long): Flow<Note?> = dao.observe(id).map { it?.toModel() }
    fun observeFolders(): Flow<List<String>> = dao.observeFolders()
    suspend fun find(id: Long): Note? = dao.find(id)?.toModel()

    suspend fun create(title: String = "", body: String = ""): Long {
        val now = System.currentTimeMillis()
        return dao.insert(
            Note(uuid = UUID.randomUUID().toString(), title = title, body = body, createdAt = now, updatedAt = now).toEntity()
        )
    }

    suspend fun save(draft: NoteDraft): Boolean = dao.updateContent(
        id = draft.id,
        title = draft.title,
        body = draft.body,
        folder = draft.folder.trim(),
        tags = draft.tags.trim(),
        now = System.currentTimeMillis()
    ) == 1

    suspend fun saveDrawing(id: Long, drawing: String): Boolean = dao.updateDrawing(id, drawing, System.currentTimeMillis()) == 1
    suspend fun pin(id: Long, value: Boolean) = dao.setPinned(id, value, System.currentTimeMillis())
    suspend fun favorite(id: Long, value: Boolean) = dao.setFavorite(id, value, System.currentTimeMillis())
    suspend fun archive(id: Long, value: Boolean) = dao.setArchived(id, value, System.currentTimeMillis())
    suspend fun trash(id: Long) = dao.trash(id, System.currentTimeMillis())
    suspend fun restore(id: Long) = dao.restore(id, System.currentTimeMillis())
    suspend fun deletePermanently(id: Long) = dao.deletePermanently(id)

    suspend fun backlinks(note: Note): List<Note> {
        if (note.title.isBlank()) return emptyList()
        return dao.backlinks(note.id, "[[${note.title}]]").map { it.toModel() }
    }
}
