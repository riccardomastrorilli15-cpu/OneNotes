package com.onenotes.app.ui.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.onenotes.app.R
import com.onenotes.app.databinding.ActivityEditorBinding
import com.onenotes.app.util.TimeUtils
import com.onenotes.app.util.toast
import kotlinx.coroutines.launch

/**
 * Full screen editor. It owns the One UI app bar (title, navigation, contextual menu) and
 * delegates everything about the content to [EditorFragment], which is reused as is by the
 * two pane layout of the main screen.
 */
class EditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding

    private val editorFragment: EditorFragment?
        get() = supportFragmentManager.findFragmentById(R.id.editor_container) as? EditorFragment

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.onenotes.app.ui.common.FontScale.apply(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.editor_container, EditorFragment.newInstance(noteId))
                .commit()
        }

        binding.editorToolbarLayout.setNavigationButtonAsBack()
        binding.editorToolbarLayout.setNavigationButtonOnClickListener { leave() }
        observeNote()
    }

    private fun observeNote() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val fragment = editorFragment ?: return@repeatOnLifecycle
                fragment.viewModel.note.collect { note ->
                    val title = note?.title.orEmpty()
                    binding.editorToolbarLayout.setTitle(
                        title.ifBlank { getString(R.string.title_activity_editor) },
                        title.ifBlank { getString(R.string.title_activity_editor) }
                    )
                    val updated = note?.updatedAt ?: System.currentTimeMillis()
                    binding.editorToolbarLayout.setCollapsedSubtitle(
                        TimeUtils.shortDateTime(this@EditorActivity, updated)
                    )
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_editor, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val note = editorFragment?.viewModel?.note?.value
        menu.findItem(R.id.action_pin)?.apply {
            val pinned = note?.pinned == true
            setTitle(if (pinned) R.string.action_unpin else R.string.action_pin)
            setIcon(if (pinned) R.drawable.ic_pin_filled else R.drawable.ic_pin)
        }
        menu.findItem(R.id.action_lock)?.apply {
            val locked = note?.locked == true
            setTitle(if (locked) R.string.action_unlock else R.string.action_lock)
            setIcon(if (locked) R.drawable.ic_lock_open else R.drawable.ic_lock)
        }
        menu.findItem(R.id.action_archive)?.setTitle(
            if (note?.archived == true) R.string.action_unarchive else R.string.action_archive
        )
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val handled = editorFragment?.onMenuAction(item.itemId) ?: false
        return handled || super.onOptionsItemSelected(item)
    }

    override fun onPause() {
        editorFragment?.saveNow()
        super.onPause()
    }

    override fun onBackPressed() {
        leave()
    }

    /** Saves the note and closes the editor; an empty note is discarded. */
    private fun leave() {
        lifecycleScope.launch {
            val deleted = editorFragment?.saveAndClose() ?: false
            if (deleted) toast(R.string.editor_empty_note_deleted)
            finish()
        }
    }

    companion object {
        private const val EXTRA_NOTE_ID = "note_id"

        fun newIntent(context: Context, noteId: Long): Intent =
            Intent(context, EditorActivity::class.java).putExtra(EXTRA_NOTE_ID, noteId)
    }
}
