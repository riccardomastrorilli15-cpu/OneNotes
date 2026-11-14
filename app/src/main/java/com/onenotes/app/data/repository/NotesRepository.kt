package com.onenotes.app.data.repository

import android.content.Context
import com.onenotes.app.data.db.AppDatabase
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.model.NoteFilter
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.data.model.NoteBody
import com.onenotes.app.data.model.NoteSection
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.data.security.CryptoManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Notes repository: the only class allowed to touch [AppDatabase] on behalf of the UI.
 *
 * It also owns two cross-cutting rules:
 *  - the full text index is always updated together with the note (never out of sync);
 *  - the body of a locked note is encrypted on disk, and its flattened text is never indexed,
 *    so that a locked note cannot be previewed or searched from outside the editor.
 */
class NotesRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val storage: FileStorage,
    private val onChange: suspend () -> Unit = {}
) {
    private val noteDao = db.noteDao()
    private val tagDao = db.tagDao()

    // ------------------------------------------------------------------ read

    fun observeNotes(filter: NoteFilter): Flow<List<NoteListItem>> {
        val source: Flow<List<NoteListItem>> = when {
            filter.section == NoteSection.TRASH -> noteDao.observeDeletedNotes()
            filter.query.isNotBlank() -> noteDao.searchNotes(buildMatch(filter.query), filter.archived)
            filter.tagId != null -> noteDao.observeNotesByTag(filter.tagId, filter.archived)
            filter.noFolder -> noteDao.observeNotesWithoutFolder(
                archived = filter.archived,
                from = filter.from,
                to = filter.to,
                sort = filter.sort.code
            )
            else -> noteDao.observeNotes(
                archived = filter.archived,
                folderId = filter.folderId,
                from = filter.from,
                to = filter.to,
                sort = filter.sort.code
            )
        }
        if (!filter.pinnedOnly && !filter.reminderOnly) return source
        return source.map { list ->
            list.filter { item ->
                (!filter.pinnedOnly || item.note.pinned) &&
                    (!filter.reminderOnly || (item.note.reminderAt != null && !item.note.reminderDone))
            }
        }
    }

    fun observeNote(id: Long): Flow<NoteEntity?> =
        noteDao.observeById(id).map { it?.decrypted() }

    suspend fun getNote(id: Long): NoteEntity? = noteDao.getById(id)?.decrypted()

    /** Parses the body of a note into its blocks. */
    fun bodyOf(note: NoteEntity): MutableList<Block> = NoteBody.fromJson(note.content)

    suspend fun create(
        title: String = "",
        blocks: List<Block> = listOf(TextBlock()),
        folderId: Long? = null,
        color: Int = 0
    ): Long {
        val content = NoteBody.toJson(blocks)
        val plain = NoteBody.toPlainText(blocks)
        val now = System.currentTimeMillis()
        val id = noteDao.upsert(
            NoteEntity(
                title = title,
                content = content,
                plainText = plain,
                folderId = folderId,
                color = color,
                createdAt = now,
                updatedAt = now
            )
        )
        onChange()
        return id
    }

    /** Saves the title and the body of a note. */
    suspend fun save(id: Long, title: String, blocks: List<Block>) {
        val current = noteDao.getById(id) ?: return
        val json = NoteBody.toJson(blocks)
        val plain = NoteBody.toPlainText(blocks)
        val updated = current.copy(
            title = title,
            content = if (current.locked) CryptoManager.encrypt(json) else json,
            plainText = if (current.locked) "" else plain,
            updatedAt = System.currentTimeMillis()
        )
        noteDao.upsert(updated)
        onChange()
    }

    // ------------------------------------------------------------- mutations

    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) = updateMany(ids) {
        it.copy(pinned = pinned)
    }

    suspend fun setColor(ids: Collection<Long>, color: Int) = updateMany(ids) {
        it.copy(color = color)
    }

    suspend fun setFolder(ids: Collection<Long>, folderId: Long?) = updateMany(ids) {
        it.copy(folderId = folderId)
    }

    suspend fun setArchived(ids: Collection<Long>, archived: Boolean) = updateMany(ids) {
        it.copy(archived = archived, updatedAt = System.currentTimeMillis())
    }

    suspend fun moveToTrash(ids: Collection<Long>) = updateMany(ids) {
        it.copy(deleted = true, deletedAt = System.currentTimeMillis(), reminderAt = null)
    }

    suspend fun restore(ids: Collection<Long>) = updateMany(ids) {
        it.copy(deleted = false, deletedAt = null)
    }

    suspend fun deleteForever(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            noteDao.getById(id)?.let { note ->
                storage.delete(NoteBody.attachmentFiles(bodyOf(note)))
                noteDao.deletePermanently(note)
            }
        }
        tagDao.clearTags(ids.toList())
        onChange()
    }

    suspend fun emptyTrash() {
        val deleted = noteDao.getAll().filter { it.deleted }
        deleteForever(deleted.map { it.id })
    }

    suspend fun setLocked(id: Long, locked: Boolean) {
        val current = noteDao.getById(id) ?: return
        if (current.locked == locked) return
        val clearContent = if (current.locked) CryptoManager.decrypt(current.content) else current.content
        val storedContent = if (locked) CryptoManager.encrypt(clearContent) else clearContent
        val plainText = if (locked) "" else NoteBody.toPlainText(NoteBody.fromJson(clearContent))
        noteDao.upsert(current.copy(locked = locked, content = storedContent, plainText = plainText))
        onChange()
    }

    suspend fun setReminder(id: Long, atMillis: Long?) {
        val current = noteDao.getById(id) ?: return
        noteDao.upsert(current.copy(reminderAt = atMillis, reminderDone = false))
        onChange()
    }

    suspend fun markReminderDone(id: Long) {
        val current = noteDao.getById(id) ?: return
        noteDao.upsert(current.copy(reminderDone = true))
        onChange()
    }

    suspend fun duplicate(id: Long): Long {
        val source = noteDao.getById(id) ?: return 0L
        val now = System.currentTimeMillis()
        val copy = source.copy(
            id = 0L,
            pinned = false,
            createdAt = now,
            updatedAt = now,
            reminderAt = null,
            reminderDone = false
        )
        val newId = noteDao.upsert(copy)
        tagDao.setTagsForNote(newId, tagDao.tagsForNote(id).map { it.id })
        onChange()
        return newId
    }

    suspend fun setTags(id: Long, names: List<String>) {
        val ids = names.mapNotNull { name ->
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val existing = tagDao.getByName(trimmed)
            existing?.id ?: tagDao.insert(com.onenotes.app.data.model.TagEntity(name = trimmed))
        }.distinct()
        tagDao.setTagsForNote(id, ids)
        onChange()
    }

    /** Notes that are still in the trash after 30 days are removed for good. */
    suspend fun purgeExpiredTrash() {
        val limit = System.currentTimeMillis() - TRASH_RETENTION_MS
        val expired = noteDao.getExpiredDeleted(limit)
        if (expired.isNotEmpty()) deleteForever(expired.map { it.id })
    }

    suspend fun getNotesWithReminders(): List<NoteEntity> = noteDao.getNotesWithReminders()

    // ------------------------------------------------------------------ counts

    fun observeAllCount(): Flow<Int> = noteDao.observeAllCount()
    fun observePinnedCount(): Flow<Int> = noteDao.observePinnedCount()
    fun observeArchiveCount(): Flow<Int> = noteDao.observeArchiveCount()
    fun observeTrashCount(): Flow<Int> = noteDao.observeTrashCount()
    fun observeReminderCount(): Flow<Int> = noteDao.observeReminderCount()

    /** Most recent notes, used by the home screen widget. */
    suspend fun recent(limit: Int): List<NoteListItem> = noteDao.getRecent(limit)

    // ---------------------------------------------------------- import/export

    /** Every note, folder and tag as they should be written in a backup. */
    suspend fun exportSnapshot(): List<NoteEntity> =
        noteDao.getAll().filter { !it.deleted }.map { it.decrypted() }

    suspend fun insertImported(note: NoteEntity, tags: List<String>): Long {
        val now = System.currentTimeMillis()
        val id = noteDao.upsert(
            note.copy(
                id = 0L,
                createdAt = note.createdAt.takeIf { it > 0 } ?: now,
                updatedAt = note.updatedAt.takeIf { it > 0 } ?: now
            )
        )
        if (tags.isNotEmpty()) setTags(id, tags)
        return id
    }

    suspend fun usedAttachmentNames(): Set<String> {
        val names = HashSet<String>()
        noteDao.getAll().forEach { note ->
            names.addAll(NoteBody.attachmentFiles(bodyOf(note)))
        }
        return names
    }

    suspend fun cleanupAttachments() {
        storage.deleteUnreferenced(usedAttachmentNames())
    }

    // --------------------------------------------------------------- helpers

    /** Applies [transform] to each note and writes it back, keeping the index in sync. */
    private suspend fun updateMany(ids: Collection<Long>, transform: (NoteEntity) -> NoteEntity) {
        if (ids.isEmpty()) return
        ids.forEach { id ->
            val current = noteDao.getById(id) ?: return@forEach
            noteDao.upsert(transform(current))
        }
        onChange()
    }

    private fun NoteEntity.decrypted(): NoteEntity =
        if (locked) copy(content = CryptoManager.decrypt(content)) else this

    /**
     * Turns the text typed by the user into a FTS4 MATCH expression: every word becomes a
     * prefix query, so "note imp" finds "note importanti".
     */
    fun buildMatch(query: String): String {
        val tokens = query.trim()
            .split(Regex("\\s+"))
            .map { it.replace("\"", "") }
            .filter { it.isNotBlank() }
            .take(12)
        if (tokens.isEmpty()) return "\"\""
        return tokens.joinToString(" AND ") { "\"$it\"*" }
    }

    companion object {
        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000
    }
}
