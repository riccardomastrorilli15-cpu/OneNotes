package com.onenotes.app.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.onenotes.app.R
import com.onenotes.app.data.model.NoteFilter
import com.onenotes.app.data.model.NoteSection
import com.onenotes.app.data.model.SortMode
import com.onenotes.app.databinding.ActivityMainBinding
import com.onenotes.app.ui.common.NoteSheets
import com.onenotes.app.ui.editor.EditorActivity
import com.onenotes.app.ui.editor.EditorFragment
import com.onenotes.app.ui.lock.LockActivity
import com.onenotes.app.ui.settings.SettingsActivity
import com.onenotes.app.util.toast
import dev.oneuiproject.oneui.layout.DrawerLayout
import dev.oneuiproject.oneui.layout.ToolbarLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Main screen: the One UI drawer, the notes list and - on tablets and on the unfolded Fold -
 * the editor next to it (two pane layout).
 */
class MainActivity : AppCompatActivity(), NoteListFragment.Host {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private var listFragment: NoteListFragment? = null
    private var editorFragment: EditorFragment? = null
    private var drawerAdapter: DrawerAdapter? = null

    private var twoPane: Boolean = false
    private var unlocked = false

    private val settingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // The settings screen may have changed the layout or the theme: the UI reacts to it
            // through the flows of the preferences, nothing else to do here.
        }

    private val lockLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                unlocked = true
            } else {
                finish()
            }
        }

    private val editorLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            refreshWidget()
        }

    // ---------------------------------------------------------------- lifecycle

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.onenotes.app.ui.common.FontScale.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        twoPane = resources.getBoolean(R.bool.two_pane)

        if (savedInstanceState == null) {
            val fragment = NoteListFragment()
            supportFragmentManager.commit {
                replace(R.id.list_container, fragment, TAG_LIST)
            }
            listFragment = fragment
        } else {
            listFragment = supportFragmentManager.findFragmentByTag(TAG_LIST) as? NoteListFragment
        }

        setupPanes()
        setupDrawer()
        setupToolbar()
        observeState()

        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        checkAppLock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onBackPressed() {
        val drawer = binding.drawerLayout
        when {
            drawer.isSearchMode() -> drawer.dismissSearchMode()
            drawer.isActionMode() -> listFragment?.clearSelection()
            drawer.isDrawerOpen() -> drawer.setDrawerOpen(false, true)
            else -> super.onBackPressed()
        }
    }

    // -------------------------------------------------------------------- setup

    private fun setupPanes() {
        binding.editorContainer.visibility = if (twoPane) android.view.View.VISIBLE else android.view.View.GONE
        binding.paneDivider.visibility = binding.editorContainer.visibility
        if (!twoPane) {
            editorFragment?.let { fragment ->
                supportFragmentManager.commit { remove(fragment) }
            }
            editorFragment = null
        }
    }

    private fun setupDrawer() {
        drawerAdapter = DrawerAdapter(
            onEntryClick = { entry -> onDrawerEntry(entry) },
            onNewFolderClick = { promptNewFolder() }
        )
        binding.drawerList.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = drawerAdapter
            itemAnimator = null
            setHasFixedSize(false)
        }
        binding.drawerLayout.setDrawerOpen(false, false)
    }

    private fun setupToolbar() {
        val toolbar = binding.drawerLayout
        toolbar.setNavigationButtonTooltip(getString(R.string.nav_open_navigation))

        toolbar.setSearchModeListener(object : ToolbarLayout.SearchModeListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                viewModel.setQuery(query)
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                viewModel.setQuery(newText)
                return true
            }

            override fun onSearchModeToggle(searchView: androidx.appcompat.widget.SearchView, visible: Boolean) {
                if (!visible) viewModel.setQuery("")
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.counts.collect { refreshDrawer() }
                }
                launch {
                    viewModel.foldersList.collect { refreshDrawer() }
                }
                launch {
                    viewModel.tagsList.collect { refreshDrawer() }
                }
                launch {
                    viewModel.filter.collect { refreshDrawer() }
                }
                launch {
                    viewModel.notesList.collect { list ->
                        if (twoPane) {
                            val currentId = editorFragment?.noteId
                            if (currentId != null && currentId != 0L && list.none { it.note.id == currentId }) {
                                // The note shown on the right is no longer part of the list
                                // (deleted, archived…): keep the pane consistent.
                                if (viewModel.filter.value.isPlain) {
                                    showEditor(0L)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------- drawer

    private fun refreshDrawer() {
        val counts = viewModel.counts.value
        val filter = viewModel.filter.value
        val rows = ArrayList<DrawerRow>()

        rows += DrawerRow.Entry(
            key = KEY_ALL,
            title = getString(R.string.nav_all_notes),
            icon = R.drawable.ic_view_list,
            count = counts.all,
            target = DrawerTarget.ALL_NOTES
        )
        rows += DrawerRow.Entry(
            key = KEY_PINNED,
            title = getString(R.string.nav_pinned),
            icon = R.drawable.ic_pin,
            count = counts.pinned,
            target = DrawerTarget.PINNED
        )
        rows += DrawerRow.Entry(
            key = KEY_REMINDERS,
            title = getString(R.string.nav_reminders),
            icon = R.drawable.ic_reminder,
            count = counts.reminders,
            target = DrawerTarget.REMINDERS
        )

        rows += DrawerRow.Header(getString(R.string.nav_folders))
        viewModel.foldersList.value.forEach { folder ->
            rows += DrawerRow.Entry(
                key = "$KEY_FOLDER${folder.folder.id}",
                title = folder.folder.name,
                icon = R.drawable.ic_folder,
                count = folder.noteCount,
                target = DrawerTarget.FOLDER,
                payloadId = folder.folder.id
            )
        }
        rows += DrawerRow.NewFolder

        if (viewModel.tagsList.value.isNotEmpty()) {
            rows += DrawerRow.Header(getString(R.string.nav_tags))
            viewModel.tagsList.value.forEach { tag ->
                rows += DrawerRow.Entry(
                    key = "$KEY_TAG${tag.tag.id}",
                    title = tag.tag.name,
                    icon = R.drawable.ic_tag,
                    count = tag.noteCount,
                    target = DrawerTarget.TAG,
                    payloadId = tag.tag.id
                )
            }
        }

        rows += DrawerRow.Header("")
        rows += DrawerRow.Entry(
            key = KEY_ARCHIVE,
            title = getString(R.string.nav_archive),
            icon = R.drawable.ic_archive,
            count = counts.archive,
            target = DrawerTarget.ARCHIVE
        )
        rows += DrawerRow.Entry(
            key = KEY_TRASH,
            title = getString(R.string.nav_trash),
            icon = R.drawable.ic_delete,
            count = counts.trash,
            target = DrawerTarget.TRASH
        )
        rows += DrawerRow.Entry(
            key = KEY_SETTINGS,
            title = getString(R.string.nav_settings),
            icon = R.drawable.ic_settings,
            target = DrawerTarget.SETTINGS
        )

        drawerAdapter?.let {
            it.selectedKey = selectedKeyOf(filter)
            it.submitList(rows)
        }
    }

    private fun selectedKeyOf(filter: NoteFilter): String = when {
        filter.section == NoteSection.TRASH -> KEY_TRASH
        filter.section == NoteSection.ARCHIVE -> KEY_ARCHIVE
        filter.tagId != null -> "$KEY_TAG${filter.tagId}"
        filter.folderId != null -> "$KEY_FOLDER${filter.folderId}"
        filter.pinnedOnly -> KEY_PINNED
        filter.reminderOnly -> KEY_REMINDERS
        else -> KEY_ALL
    }

    private fun onDrawerEntry(entry: DrawerRow.Entry) {
        binding.drawerLayout.setDrawerOpen(false, true)
        when (entry.target) {
            DrawerTarget.ALL_NOTES -> viewModel.clearFilters()
            DrawerTarget.PINNED -> viewModel.setPinnedFilter()
            DrawerTarget.REMINDERS -> viewModel.setReminderFilter()
            DrawerTarget.FOLDER -> viewModel.setFolder(entry.payloadId)
            DrawerTarget.TAG -> viewModel.setTag(entry.payloadId)
            DrawerTarget.ARCHIVE -> viewModel.setSection(NoteSection.ARCHIVE)
            DrawerTarget.TRASH -> viewModel.setSection(NoteSection.TRASH)
            DrawerTarget.SETTINGS -> openSettings()
        }
    }

    private fun promptNewFolder() {
        NoteSheets.showTextInput(
            context = this,
            title = getString(R.string.dialog_folder_name_title),
            hint = getString(R.string.dialog_folder_name_title),
            value = ""
        ) { name ->
            if (name.isBlank()) return@showTextInput
            lifecycleScope.launch {
                com.onenotes.app.OneNotesApp.containerOf(this@MainActivity).folders.create(name)
            }
        }
    }

    // ------------------------------------------------------------------- menus

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val trash = viewModel.filter.value.section == NoteSection.TRASH
        menu.findItem(R.id.action_empty_trash)?.isVisible = trash
        menu.findItem(R.id.action_sort)?.isVisible = !trash
        menu.findItem(R.id.action_view_type)?.isVisible = !trash
        val layout = viewModel.layout.value
        menu.findItem(R.id.action_view_type)?.setIcon(
            if (layout == com.onenotes.app.data.model.ListLayout.GRID) R.drawable.ic_view_list else R.drawable.ic_grid
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_search -> {
            binding.drawerLayout.showSearchMode()
            true
        }
        R.id.action_sort -> {
            val entries = resources.getStringArray(R.array.sort_entries)
            NoteSheets.showList(this, getString(R.string.action_sort), entries) { index ->
                viewModel.setSort(SortMode.fromCode(index))
            }
            true
        }
        R.id.action_view_type -> {
            viewModel.setLayout(
                if (viewModel.layout.value == com.onenotes.app.data.model.ListLayout.GRID) {
                    com.onenotes.app.data.model.ListLayout.LIST
                } else {
                    com.onenotes.app.data.model.ListLayout.GRID
                }
            )
            true
        }
        R.id.action_empty_trash -> {
            NoteSheets.confirm(
                context = this,
                title = getString(R.string.action_empty_trash),
                message = getString(R.string.dialog_empty_trash_message),
                confirmLabel = getString(R.string.action_empty_trash)
            ) { viewModel.emptyTrash() }
            true
        }
        R.id.action_settings -> {
            openSettings()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    // ---------------------------------------------------------------- navigation

    override fun toolbar(): ToolbarLayout = binding.drawerLayout

    override fun openNote(id: Long) {
        if (twoPane) {
            showEditor(id)
        } else {
            editorLauncher.launch(EditorActivity.newIntent(this, id))
        }
    }

    override fun openNewNote() {
        viewModel.createNote { id -> openNote(id) }
    }

    override fun isTwoPane(): Boolean = twoPane

    private fun showEditor(noteId: Long) {
        if (noteId == 0L) {
            editorFragment?.let { fragment ->
                supportFragmentManager.commit { remove(fragment) }
            }
            editorFragment = null
            return
        }
        val fragment = editorFragment
        if (fragment != null && fragment.noteId == noteId) return
        val newFragment = EditorFragment.newInstance(noteId)
        supportFragmentManager.commit {
            replace(R.id.editor_container, newFragment, TAG_EDITOR)
        }
        editorFragment = newFragment
    }

    /** Closes the right pane: used by the editor when the note it shows is deleted. */
    fun closeEditorPane() {
        showEditor(0L)
    }

    private fun openSettings() {
        settingsLauncher.launch(Intent(this, SettingsActivity::class.java))
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "onenotes" && data.host == "note") {
            val id = data.lastPathSegment?.toLongOrNull() ?: return
            openNote(id)
        }
    }

    // -------------------------------------------------------------------- other

    private fun checkAppLock() {
        val container = com.onenotes.app.OneNotesApp.containerOf(this)
        lifecycleScope.launch {
            val locked = container.settings.appLock.first()
            if (locked && !unlocked) {
                lockLauncher.launch(
                    LockActivity.intent(this@MainActivity, LockActivity.REASON_APP)
                )
            }
        }
    }

    private fun refreshWidget() {
        com.onenotes.app.ui.widget.NotesWidgetProvider.refresh(this)
    }

    /** The drawer view belongs to the One UI library, its id lives in the library R class. */
    private fun DrawerLayout.isDrawerOpen(): Boolean =
        findViewById<androidx.drawerlayout.widget.DrawerLayout>(
            dev.oneuiproject.oneui.design.R.id.drawerlayout_drawer
        ).isDrawerOpen(android.view.Gravity.START)

    companion object {
        private const val TAG_LIST = "note_list"
        private const val TAG_EDITOR = "editor"

        private const val KEY_ALL = "all"
        private const val KEY_PINNED = "pinned"
        private const val KEY_REMINDERS = "reminders"
        private const val KEY_FOLDER = "folder_"
        private const val KEY_TAG = "tag_"
        private const val KEY_ARCHIVE = "archive"
        private const val KEY_TRASH = "trash"
        private const val KEY_SETTINGS = "settings"
    }
}
