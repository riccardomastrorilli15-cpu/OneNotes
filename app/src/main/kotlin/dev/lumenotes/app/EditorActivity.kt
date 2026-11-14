package dev.lumenotes.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.lumenotes.app.databinding.ActivityEditorBinding
import dev.lumenotes.data.LatestSaveQueue
import dev.lumenotes.data.NotesRepository
import dev.lumenotes.editor.EditResult
import dev.lumenotes.editor.MarkdownEditing
import dev.lumenotes.editor.MarkdownPreview
import dev.lumenotes.model.NoteDraft
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class EditorActivity : AppCompatActivity() {
    @Inject lateinit var repository: NotesRepository
    private lateinit var binding: ActivityEditorBinding
    private lateinit var viewModel: EditorViewModel
    private var rendering = false
    private var previewing = false
    private var leaving = false
    private var pendingExport = FORMAT_MARKDOWN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySafeInsets(window, binding.root)
        binding.editorToolbar.setNavigationButtonAsBack()
        viewModel = ViewModelProvider(this)[EditorViewModel::class.java]
        pendingExport = savedInstanceState?.getString(STATE_EXPORT) ?: FORMAT_MARKDOWN
        setEditorEnabled(false)
        setupEditor()
        setupFormatting()
        observeState()
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    finishAfterSave()
                }
            }
        )
    }

    private fun setupEditor() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (rendering) return
                val existing = viewModel.draft.value ?: return
                val draft = existing.copy(
                    title = binding.titleField.text.toString(),
                    body = binding.bodyField.text.toString(),
                    folder = binding.folderField.text.toString(),
                    tags = binding.tagsField.text.toString()
                )
                viewModel.update(draft)
                updateCounter(draft.body)
            }
        }
        listOf(binding.titleField, binding.bodyField, binding.folderField, binding.tagsField).forEach { it.addTextChangedListener(watcher) }
        binding.saveStatus.setOnClickListener { viewModel.retrySave() }
    }

    private fun setupFormatting() {
        binding.formatBold.setOnClickListener { applyBodyCommand { text, start, end -> MarkdownEditing.wrap(text, start, end, "**") } }
        binding.formatItalic.setOnClickListener { applyBodyCommand { text, start, end -> MarkdownEditing.wrap(text, start, end, "*") } }
        binding.formatStrike.setOnClickListener { applyBodyCommand { text, start, end -> MarkdownEditing.wrap(text, start, end, "~~") } }
        binding.formatHeading.setOnClickListener {
            applyBodyCommand { text, start, end -> MarkdownEditing.prefixLine(text, start, end, "# ") }
        }
        binding.formatBullet.setOnClickListener {
            applyBodyCommand { text, start, end -> MarkdownEditing.prefixLine(text, start, end, "- ") }
        }
        binding.formatCheck.setOnClickListener {
            applyBodyCommand { text, start, end -> MarkdownEditing.prefixLine(text, start, end, "- [ ] ") }
        }
        binding.formatQuote.setOnClickListener {
            applyBodyCommand { text, start, end -> MarkdownEditing.prefixLine(text, start, end, "> ") }
        }
        binding.formatDate.setOnClickListener {
            val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date())
            applyBodyCommand { text, start, end -> MarkdownEditing.insert(text, start, end, date) }
        }
        binding.formatUndo.setOnClickListener { setHistoryText(viewModel.undo()) }
        binding.formatRedo.setOnClickListener { setHistoryText(viewModel.redo()) }
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.draft.collect { it?.let(::renderDraft) } }
                launch {
                    viewModel.saveStatus.collect { phase ->
                        binding.saveStatus.setText(
                            when (phase) {
                                LatestSaveQueue.Phase.SAVING -> R.string.saving
                                LatestSaveQueue.Phase.FAILED -> R.string.save_failed
                                else -> R.string.saved
                            }
                        )
                    }
                }
                launch {
                    viewModel.loadError.collect { error ->
                        if (error && !leaving) {
                            if (viewModel.draft.value == null) {
                                toast(R.string.note_unavailable)
                                leaving = true
                                finish()
                            } else {
                                // Do not discard an in-memory draft after a later database/observation failure.
                                toast(R.string.load_warning)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun renderDraft(draft: NoteDraft) {
        rendering = true
        if (binding.titleField.text.toString() != draft.title) binding.titleField.setText(draft.title)
        if (binding.bodyField.text.toString() != draft.body) binding.bodyField.setText(draft.body)
        if (binding.folderField.text.toString() != draft.folder) binding.folderField.setText(draft.folder)
        if (binding.tagsField.text.toString() != draft.tags) binding.tagsField.setText(draft.tags)
        rendering = false
        setEditorEnabled(true)
        updateCounter(draft.body)
        if (previewing) binding.previewText.text = Html.fromHtml(MarkdownPreview.toSafeHtml(draft.body), Html.FROM_HTML_MODE_COMPACT)
    }

    private fun setEditorEnabled(enabled: Boolean) {
        listOf(binding.titleField, binding.bodyField, binding.folderField, binding.tagsField).forEach { it.isEnabled = enabled }
        binding.formatBar.isEnabled = enabled
    }

    private fun updateCounter(body: String) {
        var words = 0
        var inWord = false
        body.forEach { character ->
            if (character.isWhitespace()) {
                inWord = false
            } else if (!inWord) {
                words++
                inWord = true
            }
        }
        binding.counter.text = getString(R.string.word_count, words, body.length)
    }

    private fun applyBodyCommand(command: (String, Int, Int) -> EditResult) {
        if (previewing || viewModel.draft.value == null) return
        val body = binding.bodyField
        val result = command(body.text.toString(), body.selectionStart, body.selectionEnd)
        body.setText(result.text)
        body.setSelection(result.selectionStart, result.selectionEnd)
        body.requestFocus()
    }

    private fun setHistoryText(text: String?) {
        if (text == null) return
        rendering = true
        binding.bodyField.setText(text)
        binding.bodyField.setSelection(text.length)
        rendering = false
        updateCounter(text)
        binding.bodyField.requestFocus()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_preview)?.setTitle(if (previewing) R.string.edit else R.string.preview)
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_preview -> {
            togglePreview()
            true
        }

        R.id.action_drawing -> {
            openDrawing()
            true
        }

        R.id.action_export -> {
            showExport()
            true
        }

        R.id.action_share -> {
            shareText()
            true
        }

        R.id.action_backlinks -> {
            showBacklinks()
            true
        }

        R.id.action_note_options -> {
            showNoteOptions()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.isCtrlPressed && !previewing) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_B -> {
                    binding.formatBold.performClick()
                    return true
                }

                KeyEvent.KEYCODE_I -> {
                    binding.formatItalic.performClick()
                    return true
                }

                KeyEvent.KEYCODE_Z -> {
                    if (event.isShiftPressed) binding.formatRedo.performClick() else binding.formatUndo.performClick()
                    return true
                }

                KeyEvent.KEYCODE_Y -> {
                    binding.formatRedo.performClick()
                    return true
                }

                KeyEvent.KEYCODE_S -> {
                    viewModel.retrySave()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun togglePreview() {
        val draft = viewModel.draft.value ?: return
        previewing = !previewing
        if (previewing) {
            binding.previewText.text = Html.fromHtml(MarkdownPreview.toSafeHtml(draft.body), Html.FROM_HTML_MODE_COMPACT)
            val keyboard = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            keyboard.hideSoftInputFromWindow(binding.bodyField.windowToken, 0)
        }
        binding.bodyField.visibility = if (previewing) View.GONE else View.VISIBLE
        binding.previewText.visibility = if (previewing) View.VISIBLE else View.GONE
        binding.formatBar.visibility = if (previewing) View.GONE else View.VISIBLE
        invalidateOptionsMenu()
    }

    private fun finishAfterSave() {
        if (leaving) return
        if (viewModel.draft.value == null) {
            leaving = true
            finish()
            return
        }
        leaving = true
        lifecycleScope.launch {
            if (viewModel.flush()) {
                finish()
            } else {
                leaving = false
                showLeaveUnsavedDialog()
            }
        }
    }

    private fun showLeaveUnsavedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.leave_unsaved_title)
            .setMessage(R.string.leave_unsaved_message)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.leave_unsaved) { _, _ ->
                leaving = true
                finish()
            }
            .show()
    }

    private fun openDrawing() {
        lifecycleScope.launch {
            if (viewModel.flush()) {
                startActivity(Intent(this@EditorActivity, DrawingActivity::class.java).putExtra(EXTRA_NOTE_ID, viewModel.id))
            } else {
                toast(R.string.save_failed)
            }
        }
    }

    private fun showNoteOptions() {
        val note = viewModel.note.value ?: return
        val labels = arrayOf(
            getString(if (note.pinned) R.string.unpin else R.string.pin),
            getString(if (note.favorite) R.string.remove_favorite else R.string.add_favorite),
            getString(if (note.archived) R.string.unarchive else R.string.move_archive),
            getString(R.string.move_trash)
        )
        AlertDialog.Builder(this).setTitle(R.string.note_actions).setItems(labels) { _, index ->
            lifecycleScope.launch {
                if (!viewModel.flush()) {
                    toast(R.string.save_failed)
                    return@launch
                }
                try {
                    when (index) {
                        0 -> repository.pin(note.id, !note.pinned)

                        1 -> repository.favorite(note.id, !note.favorite)

                        2 -> repository.archive(note.id, !note.archived)

                        3 -> {
                            leaving = true
                            repository.trash(note.id)
                            finish()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    leaving = false
                    toast(R.string.action_failed)
                }
            }
        }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun showBacklinks() {
        lifecycleScope.launch {
            try {
                val notes = viewModel.backlinks()
                if (notes.isEmpty()) {
                    AlertDialog.Builder(this@EditorActivity).setTitle(R.string.backlinks)
                        .setMessage(R.string.no_backlinks).setPositiveButton(R.string.close, null).show()
                } else {
                    val labels = notes.map { it.title.ifBlank { getString(R.string.untitled) } }.toTypedArray()
                    AlertDialog.Builder(this@EditorActivity).setTitle(R.string.backlinks).setItems(labels) { _, index ->
                        lifecycleScope.launch {
                            if (viewModel.flush()) {
                                startActivity(
                                    Intent(this@EditorActivity, EditorActivity::class.java).putExtra(EXTRA_NOTE_ID, notes[index].id)
                                )
                            } else {
                                toast(R.string.save_failed)
                            }
                        }
                    }.setNegativeButton(R.string.cancel, null).show()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.action_failed)
            }
        }
    }

    private fun showExport() {
        val labels = arrayOf(getString(R.string.export_markdown), getString(R.string.export_text))
        AlertDialog.Builder(this).setTitle(R.string.export).setItems(labels) { _, index ->
            pendingExport = if (index == 0) FORMAT_MARKDOWN else FORMAT_TEXT
            createExportDocument()
        }.setNegativeButton(R.string.cancel, null).show()
    }

    @Suppress("DEPRECATION")
    private fun createExportDocument() {
        val title = viewModel.draft.value?.title.orEmpty()
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().take(60)
            .ifBlank { getString(R.string.untitled) }
        val extension = if (pendingExport == FORMAT_MARKDOWN) "md" else "txt"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = if (pendingExport == FORMAT_MARKDOWN) "text/markdown" else "text/plain"
            putExtra(Intent.EXTRA_TITLE, "$title.$extension")
        }
        try {
            startActivityForResult(intent, REQUEST_EXPORT)
        } catch (_: Exception) {
            toast(R.string.export_failed)
        }
    }

    @Deprecated("Activity result APIs remain supported on API 26 and the SESL activity base")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EXPORT && resultCode == RESULT_OK) data?.data?.let(::writeExport)
    }

    private fun writeExport(uri: Uri) {
        val format = pendingExport
        lifecycleScope.launch {
            try {
                val draft = viewModel.draft.filterNotNull().first()
                val text = exportText(draft, format)
                withContext(Dispatchers.IO) {
                    val stream = contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                    stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                }
                toast(R.string.export_complete)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toast(R.string.export_failed)
            }
        }
    }

    private fun shareText() {
        val draft = viewModel.draft.value ?: return
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, exportText(draft, FORMAT_TEXT))
        try {
            startActivity(Intent.createChooser(intent, getString(R.string.share_text)))
        } catch (
            _: Exception
        ) {
            toast(R.string.action_failed)
        }
    }

    private fun exportText(draft: NoteDraft, format: String): String {
        val title = draft.title.trim()
        if (title.isEmpty()) return draft.body
        return (if (format == FORMAT_MARKDOWN) "# $title" else title) + "\n\n" + draft.body
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_EXPORT, pendingExport)
        super.onSaveInstanceState(outState)
    }

    private fun toast(message: Int) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    companion object {
        private const val REQUEST_EXPORT = 1002
        private const val FORMAT_MARKDOWN = "markdown"
        private const val FORMAT_TEXT = "text"
        private const val STATE_EXPORT = "export_format"
    }
}
