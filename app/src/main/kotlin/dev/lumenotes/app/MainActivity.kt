package dev.lumenotes.app

import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import dev.lumenotes.app.databinding.ActivityMainBinding
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.model.NoteCollection
import dev.lumenotes.model.NoteSummary
import dev.oneuiproject.oneui.layout.ToolbarLayout
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: LibraryViewModel
    private lateinit var adapter: NoteAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeInsets(window, binding.root)
        viewModel = ViewModelProvider(this)[LibraryViewModel::class.java]
        setupList()
        setupNavigation()
        setupSearch()
        observeState()
        if (savedInstanceState == null) handleIncoming(intent)
    }

    private fun setupList() {
        adapter = NoteAdapter(
            onOpen = { note -> if (note.trashedAt == null) openEditor(note.id) else showOptions(note) },
            onOptions = ::showOptions
        )
        val columns = if (resources.configuration.screenWidthDp >= 600) 2 else 1
        val grid = GridLayoutManager(this, columns)
        binding.notesList.layoutManager = grid
        binding.notesList.adapter = adapter
        if (columns > 1) {
            val gap = (6 * resources.displayMetrics.density).toInt()
            binding.notesList.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    outRect.left = gap
                    outRect.right = gap
                }
            })
        }
        binding.notesList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0 && grid.findLastVisibleItemPosition() >= adapter.itemCount - 8) viewModel.loadMore()
            }
        })
        binding.newNoteButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            viewModel.create()
        }
        binding.retryButton.setOnClickListener { viewModel.retry() }
        binding.folderFilter.setOnClickListener { viewModel.setFolder(null) }
    }

    private fun setupNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            val selected = when (item.itemId) {
                R.id.nav_notes -> NoteCollection.NOTES
                R.id.nav_favorites -> NoteCollection.FAVORITES
                R.id.nav_archive -> NoteCollection.ARCHIVE
                R.id.nav_trash -> NoteCollection.TRASH
                else -> return@setOnItemSelectedListener false
            }
            viewModel.setCollection(selected)
            true
        }
        binding.drawerNotes.setOnClickListener { selectCollection(NoteCollection.NOTES) }
        binding.drawerFavorites.setOnClickListener { selectCollection(NoteCollection.FAVORITES) }
        binding.drawerArchive.setOnClickListener { selectCollection(NoteCollection.ARCHIVE) }
        binding.drawerTrash.setOnClickListener { selectCollection(NoteCollection.TRASH) }
        binding.drawerFolders.setOnClickListener {
            closeDrawer()
            showFolders()
        }
        binding.drawerTemplates.setOnClickListener {
            closeDrawer()
            showTemplates()
        }
        binding.drawerImport.setOnClickListener {
            closeDrawer()
            importDocument()
        }
        binding.drawerSettings.setOnClickListener {
            closeDrawer()
            openSettings()
        }
    }

    private fun setupSearch() {
        binding.libraryToolbar.setSearchModeListener(object : ToolbarLayout.SearchModeListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.libraryToolbar.searchView.clearFocus()
                return true
            }
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
            override fun onSearchModeToggle(searchView: SearchView?, visible: Boolean) {
                if (!visible) viewModel.setQuery("")
            }
        })
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch {
                    viewModel.collection.collect { collection ->
                        binding.sectionTitle.setText(collection.label())
                        val menuId = collection.navigationId()
                        if (binding.bottomNavigation.selectedItemId != menuId) binding.bottomNavigation.selectedItemId = menuId
                    }
                }
                launch {
                    viewModel.folder.collect { folder ->
                        binding.folderFilter.visibility = if (folder == null) View.GONE else View.VISIBLE
                        if (folder != null) binding.folderFilter.text = getString(R.string.folder_filter, folder)
                    }
                }
                launch {
                    viewModel.events.collect { event ->
                        when (event) {
                            is LibraryEvent.OpenEditor -> openEditor(event.id)
                            LibraryEvent.Error -> toast(R.string.action_failed)
                        }
                    }
                }
            }
        }
    }

    private fun render(state: LibraryState) {
        binding.loading.visibility = if (state is LibraryState.Loading) View.VISIBLE else View.GONE
        if (state is LibraryState.Loading) return
        val notes = (state as? LibraryState.Content)?.notes.orEmpty()
        adapter.submitList(notes)
        val empty = state is LibraryState.Error || notes.isEmpty()
        binding.notesList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.retryButton.visibility = if (state is LibraryState.Error) View.VISIBLE else View.GONE
        if (state is LibraryState.Error) {
            binding.emptyTitle.setText(R.string.load_error)
            binding.emptyMessage.text = ""
        } else if (viewModel.query.value.isNotBlank() || viewModel.folder.value != null) {
            binding.emptyTitle.setText(R.string.empty_search_title)
            binding.emptyMessage.setText(R.string.empty_search_message)
        } else {
            binding.emptyTitle.setText(R.string.empty_title)
            binding.emptyMessage.setText(R.string.empty_message)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_library, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_search -> {
            binding.libraryToolbar.showSearchMode()
            true
        }

        R.id.action_folders -> {
            showFolders()
            true
        }

        R.id.action_templates -> {
            showTemplates()
            true
        }

        R.id.action_import -> {
            importDocument()
            true
        }

        R.id.action_settings -> {
            openSettings()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && event.keyCode == KeyEvent.KEYCODE_K) {
            binding.libraryToolbar.showSearchMode()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun selectCollection(value: NoteCollection) {
        closeDrawer()
        viewModel.setCollection(value)
    }
    private fun closeDrawer() {
        binding.libraryToolbar.setDrawerOpen(false, true)
    }
    private fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }
    private fun openEditor(id: Long) {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(EXTRA_NOTE_ID, id))
    }

    private fun showFolders() {
        val folders = viewModel.folders.value
        val choices = listOf(getString(R.string.all_folders)) + folders
        AlertDialog.Builder(this).setTitle(R.string.folders).setItems(choices.toTypedArray()) { _, index ->
            viewModel.setFolder(if (index == 0) null else folders[index - 1])
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showTemplates() {
        val templates = listOf(
            R.string.template_meeting to R.string.template_meeting_body,
            R.string.template_diary to R.string.template_diary_body,
            R.string.template_shopping to R.string.template_shopping_body,
            R.string.template_study to R.string.template_study_body,
            R.string.template_project to R.string.template_project_body,
            R.string.template_travel to R.string.template_travel_body
        )
        AlertDialog.Builder(this).setTitle(R.string.templates)
            .setItems(templates.map { getString(it.first) }.toTypedArray()) { _, index ->
                val template = templates[index]
                viewModel.create(getString(template.first), getString(template.second))
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private data class NoteAction(val label: Int, val run: suspend (NotesRepository) -> Unit)

    private fun showOptions(note: NoteSummary) {
        val actions = if (note.trashedAt != null) {
            listOf(
                NoteAction(R.string.restore) {
                    it.restore(note.id)
                    Unit
                }
            )
        } else {
            listOf(
                NoteAction(if (note.pinned) R.string.unpin else R.string.pin) {
                    it.pin(note.id, !note.pinned)
                    Unit
                },
                NoteAction(if (note.favorite) R.string.remove_favorite else R.string.add_favorite) {
                    it.favorite(note.id, !note.favorite)
                    Unit
                },
                NoteAction(if (note.archived) R.string.unarchive else R.string.move_archive) {
                    it.archive(note.id, !note.archived)
                    Unit
                },
                NoteAction(R.string.move_trash) {
                    it.trash(note.id)
                    Unit
                }
            )
        }
        val labels = actions.map { getString(it.label) }.toMutableList()
        if (note.trashedAt != null) labels += getString(R.string.delete_forever)
        AlertDialog.Builder(this).setTitle(R.string.note_actions).setItems(labels.toTypedArray()) { _, index ->
            if (index < actions.size) viewModel.perform(actions[index].run) else confirmDelete(note.id)
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun confirmDelete(id: Long) {
        AlertDialog.Builder(this).setMessage(R.string.delete_confirmation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewModel.perform {
                    it.deletePermanently(id)
                    Unit
                }
            }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun importDocument() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "text/markdown"))
        }
        try {
            startActivityForResult(intent, REQUEST_IMPORT)
        } catch (_: Exception) {
            toast(R.string.import_failed)
        }
    }

    @Deprecated("Activity result APIs remain supported on API 26 and the SESL activity base")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMPORT && resultCode == RESULT_OK) data?.data?.let(::readImport)
    }

    private fun readImport(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (title, body) = withContext(Dispatchers.IO) {
                    val name = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }.orEmpty().substringBeforeLast('.', "").take(120)
                    val input = contentResolver.openInputStream(uri) ?: error("No input stream")
                    val bytes = input.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val read = stream.read(buffer)
                            if (read < 0) break
                            total += read
                            require(total <= MAX_IMPORT_BYTES)
                            output.write(buffer, 0, read)
                        }
                        output.toByteArray()
                    }
                    name to bytes.toString(Charsets.UTF_8).removePrefix("\uFEFF")
                }
                viewModel.create(title, body)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.import_failed)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncoming(intent)
    }

    private fun handleIncoming(intent: Intent?) {
        when (intent?.action) {
            ACTION_QUICK_NOTE -> viewModel.create()

            Intent.ACTION_SEND -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
                if (text.length <= MAX_IMPORT_BYTES) {
                    viewModel.create(intent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty().take(120), text)
                } else {
                    toast(R.string.import_failed)
                }
            }
        }
        // Keep the launch Intent intact. Re-delivery is prevented by the
        // savedInstanceState check in onCreate, while onNewIntent represents
        // a new user request. Mutating the action breaks ActivityScenario's
        // intent matching (and can also surprise other lifecycle consumers).
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    private fun NoteCollection.label(): Int = when (this) {
        NoteCollection.NOTES -> R.string.all_notes
        NoteCollection.FAVORITES -> R.string.favorites
        NoteCollection.ARCHIVE -> R.string.archive
        NoteCollection.TRASH -> R.string.trash
    }
    private fun NoteCollection.navigationId(): Int = when (this) {
        NoteCollection.NOTES -> R.id.nav_notes
        NoteCollection.FAVORITES -> R.id.nav_favorites
        NoteCollection.ARCHIVE -> R.id.nav_archive
        NoteCollection.TRASH -> R.id.nav_trash
    }

    companion object {
        private const val REQUEST_IMPORT = 1001
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        private const val ACTION_QUICK_NOTE = "dev.lumenotes.action.QUICK_NOTE"
    }
}
