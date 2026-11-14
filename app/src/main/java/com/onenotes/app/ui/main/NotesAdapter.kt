package com.onenotes.app.ui.main

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.onenotes.app.R
import com.onenotes.app.data.model.ListLayout
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.databinding.ItemNoteBinding
import com.onenotes.app.util.TimeUtils

/**
 * Adapter of the notes list. It works both for the single column list and for the grid: the
 * fragment only changes the LayoutManager.
 */
class NotesAdapter(
    private val onNoteClick: (Long) -> Unit,
    private val onNoteLongClick: (Long) -> Boolean,
    private val onMoreClick: (NoteListItem, View) -> Unit
) : ListAdapter<NoteListItem, NotesAdapter.NoteViewHolder>(DIFF) {

    var selectionMode: Boolean = false
        private set
    var layout: ListLayout = ListLayout.LIST
        private set

    private var selectedIds: Set<Long> = emptySet()

    fun updateSelection(mode: Boolean, ids: Set<Long>) {
        val modeChanged = mode != selectionMode
        selectionMode = mode
        selectedIds = ids
        if (modeChanged) {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        } else {
            notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
        }
    }

    fun updateLayout(layout: ListLayout) {
        if (this.layout == layout) return
        this.layout = layout
        notifyItemRangeChanged(0, itemCount, PAYLOAD_SELECTION)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val binding = ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NoteViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(
        holder: NoteViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        @SuppressLint("SetTextI18n")
        fun bind(item: NoteListItem) {
            val note = item.note
            val context = binding.root.context

            val title = note.title.ifBlank {
                note.plainText.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty()
            }
            binding.noteTitle.text = title.ifBlank { context.getString(R.string.title_activity_editor) }

            val preview = when {
                note.locked -> context.getString(R.string.lock_locked_content)
                else -> note.plainText.replace('\n', ' ').trim()
            }
            binding.notePreview.text = preview
            binding.notePreview.maxLines = if (layout == ListLayout.GRID) 6 else 4

            binding.noteDate.text = TimeUtils.relative(context, note.updatedAt)
            binding.notePinned.setVisible(note.pinned)
            binding.noteLocked.setVisible(note.locked)
            binding.noteReminderIcon.setVisible(note.reminderAt != null && !note.reminderDone)
            binding.noteAttachmentIcon.setVisible(!note.locked && hasAttachments(note.content))

            val tags = item.tagNames?.takeIf { it.isNotBlank() }
            binding.noteTags.setVisible(tags != null)
            binding.noteTags.text = tags?.split(", ")?.joinToString("  ·  ") { "#$it" }.orEmpty()

            val selected = selectedIds.contains(note.id)
            binding.noteSelected.setVisible(selectionMode)
            binding.noteSelected.isChecked = selected
            binding.noteMore.setVisible(!selectionMode)
            binding.noteCard.isChecked = selected

            binding.noteCard.setCardBackgroundColor(
                if (note.color != 0) {
                    note.color
                } else {
                    ContextCompat.getColor(context, R.color.onenotes_surface)
                }
            )

            binding.root.setOnClickListener { onNoteClick(note.id) }
            binding.root.setOnLongClickListener { onNoteLongClick(note.id) }
            binding.noteMore.setOnClickListener { onMoreClick(item, it) }
        }

        /** Cheap check: the body of a note contains the JSON of its blocks. */
        private fun hasAttachments(content: String): Boolean =
            content.contains("\"type\":\"image\"") ||
                content.contains("\"type\":\"audio\"") ||
                content.contains("\"type\":\"drawing\"") ||
                content.contains("\"type\":\"file\"")
    }

    companion object {
        private const val PAYLOAD_SELECTION = "payload_selection"

        private val DIFF = object : DiffUtil.ItemCallback<NoteListItem>() {
            override fun areItemsTheSame(oldItem: NoteListItem, newItem: NoteListItem): Boolean =
                oldItem.note.id == newItem.note.id

            override fun areContentsTheSame(oldItem: NoteListItem, newItem: NoteListItem): Boolean =
                oldItem == newItem
        }
    }
}
