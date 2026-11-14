package dev.lumenotes.app

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.lumenotes.data.AutosaveQueue
import dev.lumenotes.data.LatestSaveQueue
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.editor.TextHistory
import dev.lumenotes.model.Note
import dev.lumenotes.model.NoteDraft
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

const val EXTRA_NOTE_ID = "note_id"

@HiltViewModel
class EditorViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val repository: NotesRepository,
    private val autosave: AutosaveQueue
) : ViewModel() {
    val id = handle.get<Long>(EXTRA_NOTE_ID) ?: -1L
    private val history = TextHistory()
    private val mutableDraft = MutableStateFlow<NoteDraft?>(null)
    private val mutableNote = MutableStateFlow<Note?>(null)
    private val mutableLoadError = MutableStateFlow(false)
    val draft: StateFlow<NoteDraft?> = mutableDraft
    val note: StateFlow<Note?> = mutableNote
    val loadError: StateFlow<Boolean> = mutableLoadError
    val saveStatus = autosave.status.map { it[id]?.phase }

    init {
        viewModelScope.launch {
            repository.observeNote(id).catch { mutableLoadError.value = true }.collect { note ->
                mutableNote.value = note
                if (note == null || note.trashedAt != null) {
                    mutableLoadError.value = true
                } else if (mutableDraft.value == null) {
                    mutableDraft.value = NoteDraft(note.id, note.title, note.body, note.folder, note.tags)
                }
            }
        }
    }

    fun update(draft: NoteDraft) {
        val previous = mutableDraft.value ?: return
        if (draft == previous) return
        history.record(previous.body, draft.body)
        mutableDraft.value = draft
        autosave.submit(draft)
    }

    fun undo(): String? = changeHistory { current -> history.undo(current) }
    fun redo(): String? = changeHistory { current -> history.redo(current) }

    private fun changeHistory(operation: (String) -> String?): String? {
        val draft = mutableDraft.value ?: return null
        val body = operation(draft.body) ?: return null
        val changed = draft.copy(body = body)
        mutableDraft.value = changed
        autosave.submit(changed)
        return body
    }

    fun retrySave() {
        mutableDraft.value?.let(autosave::submit)
    }

    suspend fun flush(): Boolean {
        val current = mutableDraft.value ?: return false
        val revision = autosave.submit(current)
        return autosave.await(id, revision)
    }

    suspend fun backlinks(): List<Note> {
        val current = mutableNote.value ?: return emptyList()
        val title = mutableDraft.value?.title ?: current.title
        return repository.backlinks(current.copy(title = title))
    }
}
