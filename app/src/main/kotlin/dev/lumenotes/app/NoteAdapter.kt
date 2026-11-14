package dev.lumenotes.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.lumenotes.app.databinding.ItemNoteBinding
import dev.lumenotes.model.NoteSummary
import java.text.DateFormat
import java.util.Date

class NoteAdapter(private val onOpen: (NoteSummary) -> Unit, private val onOptions: (NoteSummary) -> Unit) :
    ListAdapter<NoteSummary, NoteAdapter.Holder>(DIFF) {
    private val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)

    init {
        setHasStableIds(true)
    }
    override fun getItemId(position: Int): Long = getItem(position).id

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val note = getItem(position)
        val context = holder.itemView.context
        holder.binding.title.text = note.title.ifBlank { context.getString(R.string.untitled) }
        holder.binding.preview.text = note.preview.ifBlank { context.getString(R.string.empty_body) }
        val label = listOf(note.folder, note.tags).filter { it.isNotBlank() }.joinToString(" · ")
        holder.binding.folder.text = label
        holder.binding.folder.visibility = if (label.isBlank()) View.GONE else View.VISIBLE
        holder.binding.updated.text = dateFormat.format(Date(note.updatedAt))
        holder.binding.favoriteBadge.visibility = if (note.favorite) View.VISIBLE else View.GONE
        holder.binding.pinBadge.visibility = if (note.pinned) View.VISIBLE else View.GONE
        holder.binding.drawingBadge.visibility = if (note.hasDrawing) View.VISIBLE else View.GONE
        holder.binding.moreOptions.contentDescription = context.getString(
            R.string.note_actions_for,
            note.title.ifBlank { context.getString(R.string.untitled) }
        )
        holder.binding.moreOptions.setOnClickListener { onOptions(note) }
        holder.itemView.setOnClickListener { onOpen(note) }
        holder.itemView.setOnLongClickListener {
            onOptions(note)
            true
        }
    }

    class Holder(val binding: ItemNoteBinding) : RecyclerView.ViewHolder(binding.root)

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<NoteSummary>() {
            override fun areItemsTheSame(oldItem: NoteSummary, newItem: NoteSummary) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: NoteSummary, newItem: NoteSummary) = oldItem == newItem
        }
    }
}
