package com.onenotes.app.ui.main

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.onenotes.app.OneNotesApp
import com.onenotes.app.data.model.FolderWithCount
import com.onenotes.app.data.model.ListLayout
import com.onenotes.app.data.model.NoteFilter
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.data.model.NoteSection
import com.onenotes.app.data.model.SortMode
import com.onenotes.app.data.model.TagWithCount
import com.onenotes.app.data.repository.SettingsRepository
import com.onenotes.app.ui.common.NoteAction
import com.onenotes.app.util.copyToClipboard
import com.onenotes.app.util.shareText
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Counters shown next to the entries of the navigation drawer. */
data class DrawerCounts(
    val all: Int = 0,
    val pinned: Int = 0,
    val archive: Int = 0,
    val trash: Int = 0,
    val reminders: Int = 0
)

/**
 * State of the main screen: the current filter, the notes, the drawer content and the
 * multi selection. It is scoped to the activity, so on tablets and on the unfolded Fold the
 * list and the editor share it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = OneNotesApp.containerOf(app)
    private val notes = container.notes
    private val folders = container.folders
    private val tags = container.tags
    private val settings = container.settings

    private val _filter = MutableStateFlow(NoteFilter())
    val filter: StateFlow<NoteFilter> = _filter.asStateFlow()

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()

    val notesList: StateFlow<List<NoteListItem>> = _filter
        .flatMapLatest { notes.observeNotes(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val counts: StateFlow<DrawerCounts> = combine(
        notes.observeAllCount(),
        notes.observePinnedCount(),
        notes.observeArchiveCount(),
        notes.observeTrashCount(),
        notes.observeReminderCount()
    ) { all, pinned, archive, trash, reminders ->
        DrawerCounts(all, pinned, archive, trash, reminders)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DrawerCounts())

    val foldersList: StateFlow<List<FolderWithCount>> = folders.observeWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val tagsList: StateFlow<List<TagWithCount>> = tags.observeWithCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val layout: StateFlow<ListLayout> = settings.listLayout
        .map { if (it == SettingsRepository.LAYOUT_GRID) ListLayout.GRID else ListLayout.LIST }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListLayout.LIST)

    val sortMode: StateFlow<SortMode> = settings.sortMode
        .map { SortMode.fromCode(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortMode.UPDATED)

    // ---------------------------------------------------------------- filters

    fun setSection(section: NoteSection) {
        _filter.value = _filter.value.copy(section = section, folderId = null, tagId = null, noFolder = false)
    }

    fun setFolder(folderId: Long?) {
        _filter.value = _filter.value.copy(
            section = NoteSection.NOTES,
            folderId = folderId,
            tagId = null,
            noFolder = false
        )
    }

    fun setNoFolder() {
        _filter.value = _filter.value.copy(
            section = NoteSection.NOTES,
            folderId = null,
            tagId = null,
            noFolder = true
        )
    }

    fun setTag(tagId: Long) {
        _filter.value = _filter.value.copy(
            section = NoteSection.NOTES,
            folderId = null,
            tagId = tagId,
            noFolder = false
        )
    }

    fun setQuery(query: String) {
        if (_filter.value.query == query) return
        _filter.value = _filter.value.copy(query = query)
    }

    /** Only the notes pinned to the top. */
    fun setPinnedFilter() {
        _filter.value = NoteFilter(pinnedOnly = true)
    }

    /** Only the notes that have a reminder pending. */
    fun setReminderFilter() {
        _filter.value = NoteFilter(reminderOnly = true)
    }

    /** Drops every filter and goes back to the plain list. */
    fun clearFilters() {
        _filter.value = NoteFilter(sort = _filter.value.sort)
    }

    fun setSort(mode: SortMode) {
        _filter.value = _filter.value.copy(sort = mode)
        viewModelScope.launch(Dispatchers.IO) { settings.setSortMode(mode.code) }
    }

    fun setLayout(layout: ListLayout) {
        viewModelScope.launch(Dispatchers.IO) {
            settings.setListLayout(
                if (layout == ListLayout.GRID) SettingsRepository.LAYOUT_GRID
                else SettingsRepository.LAYOUT_LIST
            )
        }
    }

    // -------------------------------------------------------------- selection

    fun toggleSelection(id: Long) {
        val current = _selection.value.toMutableSet()
        if (!current.add(id)) current.remove(id)
        _selection.value = current
    }

    fun setSelection(ids: Collection<Long>) {
        _selection.value = ids.toSet()
    }

    fun clearSelection() {
        _selection.value = emptySet()
    }

    // ---------------------------------------------------------------- actions

    fun createNote(onCreated: (Long) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val folderId = _filter.value.folderId
            val id = notes.create(folderId = folderId)
            withContext(Dispatchers.Main) { onCreated(id) }
        }
    }

    fun pinSelected(pinned: Boolean) = launchAction { notes.setPinned(it, pinned) }
    fun colorSelected(color: Int) = launchAction { notes.setColor(it, color) }
    fun moveSelectedTo(folderId: Long?) = launchAction { notes.setFolder(it, folderId) }
    fun archiveSelected(archived: Boolean) = launchAction { notes.setArchived(it, archived) }
    fun trashSelected() = launchAction { notes.moveToTrash(it) }
    fun restoreSelected() = launchAction { notes.restore(it) }
    fun deleteSelectedForever() = launchAction { notes.deleteForever(it) }
    fun lockSelected(locked: Boolean) = launchAction { ids -> ids.forEach { notes.setLocked(it, locked) } }
    fun duplicateSelected() = launchAction { ids -> ids.forEach { notes.duplicate(it) } }
    fun setTagsForSelected(names: List<String>) =
        launchAction { ids -> ids.forEach { notes.setTags(it, names) } }

    fun trashIds(ids: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) { notes.moveToTrash(ids) }
    }

    fun emptyTrash() {
        viewModelScope.launch(Dispatchers.IO) { notes.emptyTrash() }
    }

    // ------------------------------------------------- actions on explicit ids

    fun perform(action: NoteAction, ids: Collection<Long>) {
        if (ids.isEmpty()) return
        _selection.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) {
            when (action) {
                NoteAction.PIN -> notes.setPinned(ids, true)
                NoteAction.UNPIN -> notes.setPinned(ids, false)
                NoteAction.ARCHIVE -> notes.setArchived(ids, true)
                NoteAction.UNARCHIVE -> notes.setArchived(ids, false)
                NoteAction.LOCK -> ids.forEach { notes.setLocked(it, true) }
                NoteAction.UNLOCK -> ids.forEach { notes.setLocked(it, false) }
                NoteAction.DUPLICATE -> ids.forEach { notes.duplicate(it) }
                NoteAction.DELETE -> notes.moveToTrash(ids)
                NoteAction.RESTORE -> notes.restore(ids)
                NoteAction.DELETE_FOREVER -> notes.deleteForever(ids)
                else -> Unit
            }
        }
    }

    fun setReminder(ids: Collection<Long>, at: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            ids.forEach { notes.setReminder(it, at) }
        }
    }

    fun setColorOf(ids: Collection<Long>, color: Int) {
        viewModelScope.launch(Dispatchers.IO) { notes.setColor(ids, color) }
    }

    fun moveTo(ids: Collection<Long>, folderId: Long?) {
        viewModelScope.launch(Dispatchers.IO) { notes.setFolder(ids, folderId) }
    }

    fun setTagsOf(ids: Collection<Long>, names: List<String>) {
        viewModelScope.launch(Dispatchers.IO) { ids.forEach { notes.setTags(it, names) } }
    }

    /** Plain text of the notes, ready to be copied or shared. */
    suspend fun textOf(ids: Collection<Long>): String {
        val parts = ArrayList<String>()
        ids.forEach { id ->
            val note = notes.getNote(id) ?: return@forEach
            val blocks = notes.bodyOf(note)
            parts += com.onenotes.app.data.model.NoteBody.toMarkdown(note.title, blocks)
        }
        return parts.joinToString("\n\n---\n\n")
    }

    fun copyText(context: Context, ids: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = textOf(ids)
            withContext(Dispatchers.Main) {
                context.copyToClipboard("OneNotes", text)
                context.toast(context.getString(R.string.msg_copy_done))
            }
        }
    }

    fun shareNotes(context: Context, ids: Collection<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            val text = textOf(ids)
            withContext(Dispatchers.Main) {
                context.shareText(context.getString(R.string.app_name), text)
            }
        }
    }

    /** Exports the notes in the given format ("md", "txt" or "pdf"). */
    fun exportNotes(ids: Collection<Long>, format: String, onDone: (java.io.File) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val files = ArrayList<java.io.File>()
            ids.forEach { id ->
                val note = notes.getNote(id) ?: return@forEach
                val file = when (format) {
                    "pdf" -> container.export.exportPdf(note)
                    "txt" -> container.export.exportText(note)
                    else -> container.export.exportMarkdown(note)
                }
                files += file
            }
            withContext(Dispatchers.Main) {
                files.firstOrNull()?.let { onDone(it) }
            }
        }
    }

    private fun launchAction(block: suspend (Collection<Long>) -> Unit) {
        val ids = _selection.value.toList()
        if (ids.isEmpty()) return
        _selection.value = emptySet()
        viewModelScope.launch(Dispatchers.IO) { block(ids) }
    }
}
