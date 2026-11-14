package com.onenotes.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationBarView
import com.onenotes.app.R
import com.onenotes.app.data.model.ListLayout
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.data.model.NoteSection
import com.onenotes.app.databinding.FragmentNoteListBinding
import com.onenotes.app.ui.common.NoteAction
import com.onenotes.app.ui.common.NoteSheets
import com.onenotes.app.ui.reminder.NotificationHelper
import com.onenotes.app.util.setVisible
import com.onenotes.app.util.shareFile
import com.onenotes.app.util.toast
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * The list of the notes. It owns the RecyclerView, the empty state, the floating button and the
 * One UI action mode (multi selection with the contextual bottom bar).
 */
class NoteListFragment : Fragment(R.layout.fragment_note_list) {

    /** Everything the fragment needs from the activity that hosts it. */
    interface Host {
        fun toolbar(): ToolbarLayout
        fun openNote(id: Long)
        fun openNewNote()
        fun isTwoPane(): Boolean
    }

    private var _binding: FragmentNoteListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MainViewModel by activityViewModels()

    private lateinit var adapter: NotesAdapter
    private var actionModeVisible = false
    private var currentBottomMenu = 0

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) pickReminderForSelection()
        }

    private val host: Host get() = requireActivity() as Host

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNoteListBinding.bind(view)

        setupRecycler()
        setupActionMode()
        observeState()

        binding.fabNewNote.setOnClickListener { host.openNewNote() }
    }

    override fun onDestroyView() {
        if (actionModeVisible) {
            runCatching { host.toolbar().dismissActionMode() }
            actionModeVisible = false
        }
        binding.notesRecycler.adapter = null
        _binding = null
        super.onDestroyView()
    }

    // ------------------------------------------------------------------- setup

    private fun setupRecycler() {
        adapter = NotesAdapter(
            onNoteClick = { id ->
                if (viewModel.selection.value.isNotEmpty()) {
                    viewModel.toggleSelection(id)
                } else {
                    host.openNote(id)
                }
            },
            onNoteLongClick = { id ->
                viewModel.toggleSelection(id)
                true
            },
            onMoreClick = { item, _ -> showNoteOptions(item) }
        )
        binding.notesRecycler.adapter = adapter
        binding.notesRecycler.setHasFixedSize(true)
        binding.notesRecycler.itemAnimator = null
        applyLayout(viewModel.layout.value)
    }

    private fun applyLayout(layout: ListLayout) {
        val context = requireContext()
        binding.notesRecycler.layoutManager = if (layout == ListLayout.GRID) {
            val span = resources.getInteger(R.integer.note_grid_span_phone)
            GridLayoutManager(context, span, RecyclerView.VERTICAL, false)
        } else {
            LinearLayoutManager(context)
        }
        adapter.updateLayout(layout)
    }

    private fun setupActionMode() {
        val toolbar = host.toolbar()
        toolbar.setActionModeToolbarMenu(R.menu.menu_action_mode_top)
        currentBottomMenu = R.menu.menu_action_mode_bottom
        toolbar.setActionModeMenu(currentBottomMenu)

        toolbar.setActionModeBottomMenuListener(
            NavigationBarView.OnItemSelectedListener { item ->
                onActionModeItem(item.itemId)
                true
            }
        )
        toolbar.setActionModeToolbarMenuListener(
            Toolbar.OnMenuItemClickListener { item ->
                if (item.groupId == ToolbarLayout.AMT_GROUP_MENU_ID) {
                    onActionModeItem(item.itemId)
                } else {
                    onActionModeToolbarItem(item.itemId)
                }
                true
            }
        )
        toolbar.setActionModeCheckboxListener { _, checked ->
            if (checked) {
                viewModel.setSelection(adapter.currentList.map { it.note.id })
            } else {
                viewModel.clearSelection()
            }
        }
        toolbar.setOnActionModeListener(object : ToolbarLayout.ActionModeCallback {
            override fun onShow(toolbarLayout: ToolbarLayout) = Unit

            override fun onDismiss(toolbarLayout: ToolbarLayout) {
                if (viewModel.selection.value.isNotEmpty()) viewModel.clearSelection()
            }
        })
    }

    // ------------------------------------------------------------------ state

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.notesList.collect { list ->
                        adapter.submitList(list)
                        updateEmptyState(list.size)
                    }
                }
                launch {
                    viewModel.layout.collect { layout -> applyLayout(layout) }
                }
                launch {
                    viewModel.selection.collect { ids -> updateSelection(ids) }
                }
                launch {
                    viewModel.filter.collect { updateHeader() }
                }
                launch {
                    viewModel.counts.collect { updateHeader() }
                }
            }
        }
    }

    private fun updateEmptyState(count: Int) {
        val empty = count == 0
        binding.emptyState.setVisible(empty)
        if (!empty) return
        val context = requireContext()
        val filter = viewModel.filter.value
        when {
            filter.query.isNotBlank() -> {
                binding.emptyTitle.setText(R.string.empty_search_title)
                binding.emptyBody.setText(R.string.empty_search_body)
            }
            filter.section == NoteSection.TRASH -> {
                binding.emptyTitle.setText(R.string.empty_trash_title)
                binding.emptyBody.setText(R.string.empty_trash_body)
            }
            filter.section == NoteSection.ARCHIVE -> {
                binding.emptyTitle.setText(R.string.empty_archive_title)
                binding.emptyBody.setText(R.string.empty_notes_body)
            }
            else -> {
                binding.emptyTitle.setText(R.string.empty_notes_title)
                binding.emptyBody.setText(R.string.empty_notes_body)
            }
        }
        binding.emptyIcon.contentDescription = context.getString(R.string.cd_note_preview)
    }

    private fun updateHeader() {
        val context = requireContext()
        val filter = viewModel.filter.value
        val counts = viewModel.counts.value
        val title = when {
            filter.query.isNotBlank() -> context.getString(R.string.action_search)
            filter.section == NoteSection.TRASH -> context.getString(R.string.nav_trash)
            filter.section == NoteSection.ARCHIVE -> context.getString(R.string.nav_archive)
            filter.tagId != null -> viewModel.tagsList.value
                .firstOrNull { it.tag.id == filter.tagId }?.tag?.name
                ?: context.getString(R.string.nav_tags)
            filter.noFolder -> context.getString(R.string.nav_no_folder)
            filter.folderId != null -> viewModel.foldersList.value
                .firstOrNull { it.folder.id == filter.folderId }?.folder?.name
                ?: context.getString(R.string.nav_all_notes)
            else -> context.getString(R.string.nav_all_notes)
        }
        val subtitle = when {
            filter.section == NoteSection.TRASH -> counts.trash.toString()
            filter.section == NoteSection.ARCHIVE -> counts.archive.toString()
            else -> resources.getQuantityString(R.plurals.notes_count, counts.all, counts.all)
        }
        host.toolbar().setTitle(title)
        host.toolbar().setExpandedSubtitle(subtitle)

        val wantedMenu = if (filter.section == NoteSection.TRASH) {
            R.menu.menu_trash
        } else {
            R.menu.menu_action_mode_bottom
        }
        if (wantedMenu != currentBottomMenu) {
            currentBottomMenu = wantedMenu
            host.toolbar().setActionModeMenu(wantedMenu)
        }
    }

    private fun updateSelection(ids: Set<Long>) {
        adapter.updateSelection(ids.isNotEmpty(), ids)
        val toolbar = host.toolbar()
        if (ids.isNotEmpty() && !actionModeVisible) {
            actionModeVisible = true
            toolbar.showActionMode()
        } else if (ids.isEmpty() && actionModeVisible) {
            actionModeVisible = false
            toolbar.dismissActionMode()
        }
        if (actionModeVisible) {
            val total = adapter.itemCount
            toolbar.setActionModeAllSelector(ids.size, true, total > 0 && ids.size >= total)
        }
    }

    // ----------------------------------------------------------- action mode

    private fun onActionModeItem(itemId: Int) {
        val ids = viewModel.selection.value.toList()
        if (ids.isEmpty()) return
        when (itemId) {
            R.id.action_pin -> {
                val allPinned = isAllPinned(ids)
                viewModel.perform(if (allPinned) NoteAction.UNPIN else NoteAction.PIN, ids)
            }
            R.id.action_color -> showColorPicker(ids)
            R.id.action_folder -> showFolderPicker(ids)
            R.id.action_archive -> {
                val allArchived = ids.all { id ->
                    viewModel.notesList.value.firstOrNull { it.note.id == id }?.note?.archived == true
                }
                viewModel.perform(
                    if (allArchived) NoteAction.UNARCHIVE else NoteAction.ARCHIVE,
                    ids
                )
            }
            R.id.action_delete -> confirmDelete(ids)
            R.id.action_restore -> viewModel.perform(NoteAction.RESTORE, ids)
            R.id.action_delete_forever -> confirmDeleteForever(ids)
        }
    }

    private fun onActionModeToolbarItem(itemId: Int) {
        val ids = viewModel.selection.value.toList()
        if (ids.isEmpty()) return
        when (itemId) {
            R.id.action_select_all -> viewModel.setSelection(adapter.currentList.map { it.note.id })
            R.id.action_tags -> showTagEditor(ids)
            R.id.action_lock -> {
                val allLocked = ids.all { id ->
                    viewModel.notesList.value.firstOrNull { it.note.id == id }?.note?.locked == true
                }
                viewModel.perform(if (allLocked) NoteAction.UNLOCK else NoteAction.LOCK, ids)
            }
            R.id.action_share -> viewModel.shareNotes(requireContext(), ids)
            R.id.action_export -> showExportPicker(ids)
            R.id.action_duplicate -> viewModel.perform(NoteAction.DUPLICATE, ids)
        }
    }

    private fun isAllPinned(ids: List<Long>): Boolean = ids.all { id ->
        viewModel.notesList.value.firstOrNull { it.note.id == id }?.note?.pinned == true
    }

    // -------------------------------------------------------------- note sheet

    private fun showNoteOptions(item: NoteListItem) {
        val context = requireContext()
        NoteSheets.showNoteOptions(
            context = context,
            item = item,
            inTrash = item.note.deleted
        ) { action ->
            val ids = listOf(item.note.id)
            when (action) {
                NoteAction.COLOR -> showColorPicker(ids)
                NoteAction.FOLDER -> showFolderPicker(ids)
                NoteAction.TAGS -> showTagEditor(ids)
                NoteAction.REMINDER -> {
                    if (item.note.reminderAt != null) {
                        NoteSheets.confirm(
                            context = context,
                            title = context.getString(R.string.dialog_reminder_title),
                            message = context.getString(R.string.action_remove_reminder),
                            confirmLabel = context.getString(R.string.action_ok)
                        ) { viewModel.setReminder(ids, null) }
                    } else {
                        pendingReminderIds = ids
                        requestReminderPermissionIfNeeded()
                    }
                }
                NoteAction.SHARE -> viewModel.shareNotes(context, ids)
                NoteAction.EXPORT -> showExportPicker(ids)
                NoteAction.COPY_TEXT -> viewModel.copyText(context, ids)
                NoteAction.DELETE -> confirmDelete(ids)
                NoteAction.DELETE_FOREVER -> confirmDeleteForever(ids)
                NoteAction.RESTORE -> viewModel.perform(NoteAction.RESTORE, ids)
                else -> viewModel.perform(action, ids)
            }
        }
    }

    private fun confirmDelete(ids: List<Long>) {
        val context = requireContext()
        NoteSheets.confirm(
            context = context,
            title = context.getString(R.string.dialog_delete_title),
            message = context.getString(R.string.dialog_delete_message),
            confirmLabel = context.getString(R.string.action_delete)
        ) { viewModel.perform(NoteAction.DELETE, ids) }
    }

    private fun confirmDeleteForever(ids: List<Long>) {
        val context = requireContext()
        NoteSheets.confirm(
            context = context,
            title = context.getString(R.string.dialog_delete_title),
            message = context.getString(R.string.dialog_delete_forever_message),
            confirmLabel = context.getString(R.string.action_delete_forever)
        ) { viewModel.perform(NoteAction.DELETE_FOREVER, ids) }
    }

    private fun showColorPicker(ids: List<Long>) {
        val context = requireContext()
        NoteSheets.showColors(
            context = context,
            title = context.getString(R.string.action_color),
            colors = NoteSheets.noteColors(context),
            names = resources.getStringArray(R.array.note_color_names),
            selected = viewModel.notesList.value
                .firstOrNull { it.note.id == ids.firstOrNull() }?.note?.color ?: 0
        ) { color -> viewModel.setColorOf(ids, color) }
    }

    private fun showFolderPicker(ids: List<Long>) {
        val context = requireContext()
        val container = com.onenotes.app.OneNotesApp.containerOf(context)
        NoteSheets.showFolders(
            context = context,
            scope = viewLifecycleOwner.lifecycleScope,
            repository = container.folders,
            folders = viewModel.foldersList.value,
            currentId = viewModel.notesList.value
                .firstOrNull { it.note.id == ids.firstOrNull() }?.note?.folderId
        ) { folderId -> viewModel.moveTo(ids, folderId) }
    }

    private fun showTagEditor(ids: List<Long>) {
        val context = requireContext()
        val noteId = ids.firstOrNull() ?: return
        val container = com.onenotes.app.OneNotesApp.containerOf(context)
        viewLifecycleOwner.lifecycleScope.launch {
            val selected = container.tags.tagsForNote(noteId).map { it.name }.toSet()
            NoteSheets.showTags(
                context = context,
                tags = viewModel.tagsList.value,
                selectedNames = selected
            ) { names -> viewModel.setTagsOf(ids, names) }
        }
    }

    private fun showExportPicker(ids: List<Long>) {
        val context = requireContext()
        NoteSheets.showList(
            context = context,
            title = context.getString(R.string.dialog_export_title),
            entries = resources.getStringArray(R.array.export_format_entries)
        ) { index ->
            val format = resources.getStringArray(R.array.export_format_values)[index]
            viewModel.exportNotes(ids, format) { file ->
                context.toast(context.getString(R.string.msg_export_done))
                context.shareFile(file, mimeOf(format), context.getString(R.string.action_export))
            }
        }
    }

    private fun mimeOf(format: String): String = when (format) {
        "pdf" -> "application/pdf"
        else -> "text/plain"
    }

    // ---------------------------------------------------------------- reminder

    private var pendingReminderIds: List<Long> = emptyList()

    private fun requestReminderPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            pickReminderForSelection()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            pickReminderForSelection()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.permission_notifications_title)
            .setMessage(R.string.permission_notifications_message)
            .setNegativeButton(R.string.permission_deny, null)
            .setPositiveButton(R.string.permission_grant) { _, _ ->
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            .show()
    }

    private fun pickReminderForSelection() {
        val ids = pendingReminderIds
        if (ids.isEmpty()) return
        val calendar = Calendar.getInstance()
        val context = requireContext()
        android.app.DatePickerDialog(
            context,
            { _, year, month, day ->
                android.app.TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        calendar.set(year, month, day, hour, minute, 0)
                        viewModel.setReminder(ids, calendar.timeInMillis)
                        pendingReminderIds = emptyList()
                        if (!NotificationHelper.canPostNotifications(context)) {
                            context.toast(context.getString(R.string.permission_notifications_title))
                        } else {
                            context.toast(context.getString(R.string.msg_reminder_set))
                        }
                    },
                    calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE),
                    true
                ).show()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun clearSelection() {
        viewModel.clearSelection()
    }
}
