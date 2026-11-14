package com.onenotes.app.ui.editor

import android.app.Application
import android.content.Context
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.NoteBody
import com.onenotes.app.data.model.NoteEntity
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.util.copyToClipboard
import com.onenotes.app.util.shareText
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State of the editor.
 *
 * It owns the working copy of the note (title + list of blocks) and it is the only place that
 * writes it to the database. The save is debounced (a note is written at most once every
 * [SAVE_DELAY] milliseconds while the user is typing) so that typing never touches the disk on
 * the main thread, and it is forced when the editor is left.
 */
class EditorViewModel(app: Application) : androidx.lifecycle.AndroidViewModel(app) {

    private val container = OneNotesApp.containerOf(app)
    private val notes = container.notes
    private val tags = container.tags

    private val _note = MutableStateFlow<NoteEntity?>(null)
    val note: StateFlow<NoteEntity?> = _note.asStateFlow()

    /** Bumped every time the *structure* of the note changes, so the adapter can re-submit. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private var working: MutableList<Block> = mutableListOf(TextBlock())
    private var titleText: String = ""
    private var noteId: Long = 0L
    private var loaded = false
    private var dirty = false
    private var saveJob: Job? = null

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()

    val id: Long get() = noteId
    val title: String get() = titleText
    val blocks: List<Block> get() = working

    fun setTitle(value: String) {
        if (titleText == value) return
        titleText = value
        markDirty()
    }

    fun blockAt(position: Int): Block? = working.getOrNull(position)

    // ------------------------------------------------------------------- loading

    fun load(id: Long) {
        if (loaded && id == noteId) return
        val first = !loaded
        loaded = true
        noteId = id
        viewModelScope.launch(Dispatchers.IO) {
            val entity = notes.getNote(id)
            val parsed = entity?.let { notes.bodyOf(it) } ?: mutableListOf(TextBlock())
            withContext(Dispatchers.Main) {
                _note.value = entity
                titleText = entity?.title.orEmpty()
                working = parsed
                if (first) {
                    undoStack.clear()
                    redoStack.clear()
                }
                updateHistoryFlags()
                _revision.value++
            }
        }
    }

    // -------------------------------------------------------------- modifications

    /** The text of a block changed: no re-submit, the row is already up to date. */
    fun markDirty() {
        dirty = true
        scheduleSave()
    }

    /** A block was added, removed or converted: remember the state for the undo. */
    fun pushHistory() {
        undoStack.addLast(Snapshot(titleText, NoteBody.toJson(working)))
        while (undoStack.size > HISTORY_LIMIT) undoStack.removeFirst()
        redoStack.clear()
        updateHistoryFlags()
    }

    /**
     * Applies a structural change (a block added, removed or converted) to the working copy
     * of the note: the previous state goes in the undo stack and the adapter is re-submitted.
     */
    fun change(block: (MutableList<Block>) -> Unit) {
        pushHistory()
        block(working)
        invalidate()
    }

    /** The list of blocks has to be re-submitted to the adapter. */
    fun invalidate() {
        dirty = true
        _revision.value++
        scheduleSave()
    }

    fun undo(): Boolean {
        val snapshot = undoStack.removeLastOrNull() ?: return false
        redoStack.addLast(Snapshot(titleText, NoteBody.toJson(working)))
        restore(snapshot)
        return true
    }

    fun redo(): Boolean {
        val snapshot = redoStack.removeLastOrNull() ?: return false
        undoStack.addLast(Snapshot(titleText, NoteBody.toJson(working)))
        restore(snapshot)
        return true
    }

    private fun restore(snapshot: Snapshot) {
        titleText = snapshot.title
        working = NoteBody.fromJson(snapshot.body)
        dirty = true
        updateHistoryFlags()
        _revision.value++
        scheduleSave()
    }

    private fun updateHistoryFlags() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
    }

    /** True when the note has no title and no content at all. */
    fun isEmpty(): Boolean {
        if (titleText.isNotBlank()) return false
        return NoteBody.toPlainText(working).isBlank()
    }

    // -------------------------------------------------------------------- saving

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DELAY)
            saveNow()
        }
    }

    /**
     * Writes the note. Empty notes are deleted instead of being stored: it is exactly what
     * Samsung Notes does, and it keeps the list free of drafts the user did not want.
     *
     * @return true when the note was deleted because it was empty
     */
    suspend fun save(): Boolean = withContext(Dispatchers.IO) {
        saveJob?.cancel()
        if (!dirty || noteId == 0L) return@withContext false
        if (isEmpty()) {
            notes.deleteForever(listOf(noteId))
            dirty = false
            return@withContext true
        }
        notes.save(noteId, titleText, working)
        _note.value = notes.getNote(noteId)
        dirty = false
        false
    }

    fun saveNow() {
        viewModelScope.launch(Dispatchers.IO) { save() }
    }

    // ------------------------------------------------------------ note actions

    fun setPinned(pinned: Boolean) = update { notes.setPinned(listOf(noteId), pinned) }
    fun setArchived(archived: Boolean) = update { notes.setArchived(listOf(noteId), archived) }
    fun setLocked(locked: Boolean) = update { notes.setLocked(noteId, locked) }
    fun setColor(color: Int) = update { notes.setColor(listOf(noteId), color) }
    fun moveTo(folderId: Long?) = update { notes.setFolder(listOf(noteId), folderId) }
    fun trash() = update { notes.moveToTrash(listOf(noteId)) }
    fun restoreFromTrash() = update { notes.restore(listOf(noteId)) }
    fun deleteForever() = update { notes.deleteForever(listOf(noteId)) }

    fun setReminder(atMillis: Long?) = update {
        notes.setReminder(noteId, atMillis)
    }

    fun setTagNames(names: List<String>) = update {
        notes.setTags(noteId, names)
    }

    suspend fun currentTagNames(): List<String> =
        withContext(Dispatchers.IO) { tags.tagsForNote(noteId).map { it.name } }

    suspend fun duplicate(): Long = withContext(Dispatchers.IO) {
        save()
        notes.duplicate(noteId)
    }

    /** Markdown of the note, used to copy it or to share it with another app. */
    suspend fun markdown(): String = withContext(Dispatchers.IO) {
        NoteBody.toMarkdown(titleText, working)
    }

    fun copyText(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = markdown()
            withContext(Dispatchers.Main) {
                context.copyToClipboard(context.getString(R.string.app_name), text)
                context.toast(context.getString(R.string.msg_copy_done))
            }
        }
    }

    fun share(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = markdown()
            withContext(Dispatchers.Main) {
                context.shareText(titleText.ifBlank { context.getString(R.string.app_name) }, text)
            }
        }
    }

    /** Exports the note as Markdown, plain text or PDF. */
    fun export(context: Context, format: String, onDone: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = notes.getNote(noteId)
            val file = when (format) {
                "pdf" -> current?.let { container.export.exportPdf(it) }
                "txt" -> current?.let { container.export.exportText(it) }
                else -> current?.let { container.export.exportMarkdown(it) }
            }
            withContext(Dispatchers.Main) {
                if (file != null) onDone(file) else context.toast(context.getString(R.string.msg_error_generic))
            }
        }
    }

    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            save()
            block()
            _note.value = notes.getNote(noteId)
        }
    }

    private data class Snapshot(val title: String, val body: String)

    companion object {
        private const val SAVE_DELAY = 700L
        private const val HISTORY_LIMIT = 80
    }
}
