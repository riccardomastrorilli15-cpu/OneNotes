package com.onenotes.app.ui.share

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.ImageBlock
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.ui.editor.EditorActivity
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * "OneNotes" target of the share sheet of Android.
 *
 * The activity is transparent: it builds the note in the background, opens the editor on it and
 * closes itself right away, so from the point of view of the user the note simply appears.
 */
class CreateNoteActivity : AppCompatActivity() {

    private val container by lazy { OneNotesApp.containerOf(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            val id = buildNote(intent)
            if (id == 0L) {
                toast(R.string.msg_error_generic)
                finish()
            } else {
                startActivity(
                    EditorActivity.newIntent(this@CreateNoteActivity, id)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                finish()
            }
        }
    }

    private suspend fun buildNote(intent: Intent?): Long {
        if (intent == null) return 0L
        val action = intent.action
        val blocks = ArrayList<Block>()
        var title = ""

        val sharedText = when {
            action == Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            intent.hasExtra(Intent.EXTRA_TEXT) -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            else -> null
        }
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        val html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT)

        if (!sharedText.isNullOrBlank()) {
            title = subject?.takeIf { it.isNotBlank() }
                ?: sharedText.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty()
            blocks += TextBlock(text = (html ?: sharedText).trim())
        }

        val images = imageUris(intent)
        if (images.isNotEmpty()) {
            val imported = withContext(Dispatchers.IO) {
                images.mapNotNull { uri ->
                    val name = runCatching { container.fileStorage.import(uri, "jpg") }.getOrNull()
                        ?: return@mapNotNull null
                    ImageBlock(fileName = name, name = "")
                }
            }
            blocks += imported
            if (title.isBlank()) title = getString(R.string.editor_insert_image)
        }

        if (blocks.isEmpty()) return 0L
        return withContext(Dispatchers.IO) {
            container.notes.create(title = title, blocks = blocks)
        }
    }

    private fun imageUris(intent: Intent): List<Uri> {
        val result = ArrayList<Uri>()
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null && isImage(intent)) result += uri
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val list = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                list?.forEach { uri -> result += uri }
            }
        }
        return result
    }

    private fun isImage(intent: Intent): Boolean =
        intent.type?.startsWith("image/") == true
}
