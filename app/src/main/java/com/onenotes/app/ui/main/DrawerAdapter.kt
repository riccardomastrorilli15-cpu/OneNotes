package com.onenotes.app.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.onenotes.app.R
import com.onenotes.app.databinding.ItemDrawerFooterBinding
import com.onenotes.app.databinding.ItemDrawerHeaderBinding
import com.onenotes.app.databinding.ItemDrawerItemBinding

/** Where a drawer entry points to. */
enum class DrawerTarget {
    ALL_NOTES, PINNED, REMINDERS, FOLDER, TAG, ARCHIVE, TRASH, SETTINGS
}

/** One row of the navigation drawer. */
sealed class DrawerRow {
    data class Header(val title: String) : DrawerRow()
    data class Entry(
        val key: String,
        val title: String,
        val icon: Int,
        val count: Int = -1,
        val target: DrawerTarget,
        val payloadId: Long = 0L
    ) : DrawerRow()
    object NewFolder : DrawerRow()
}

/**
 * Drawer of the main screen: fixed entries, folders, tags and the settings shortcut.
 */
class DrawerAdapter(
    private val onEntryClick: (DrawerRow.Entry) -> Unit,
    private val onNewFolderClick: () -> Unit
) : ListAdapter<DrawerRow, RecyclerView.ViewHolder>(DIFF) {

    var selectedKey: String = "all"
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is DrawerRow.Header -> TYPE_HEADER
        is DrawerRow.Entry -> TYPE_ENTRY
        is DrawerRow.NewFolder -> TYPE_FOOTER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                ItemDrawerHeaderBinding.inflate(inflater, parent, false)
            )
            TYPE_FOOTER -> FooterViewHolder(
                ItemDrawerFooterBinding.inflate(inflater, parent, false)
            )
            else -> EntryViewHolder(
                ItemDrawerItemBinding.inflate(inflater, parent, false),
                onEntryClick
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is DrawerRow.Header -> (holder as HeaderViewHolder).bind(row)
            is DrawerRow.Entry -> (holder as EntryViewHolder).bind(row, row.key == selectedKey)
            is DrawerRow.NewFolder -> (holder as FooterViewHolder).bind(onNewFolderClick)
        }
    }

    class HeaderViewHolder(
        private val binding: ItemDrawerHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: DrawerRow.Header) {
            binding.drawerHeaderTitle.text = row.title
        }
    }

    class FooterViewHolder(
        private val binding: ItemDrawerFooterBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(onClick: () -> Unit) {
            binding.drawerNewFolder.setOnClickListener { onClick() }
        }
    }

    class EntryViewHolder(
        private val binding: ItemDrawerItemBinding,
        private val onClick: (DrawerRow.Entry) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: DrawerRow.Entry, selected: Boolean) {
            binding.root.isActivated = selected
            binding.drawerItemTitle.text = row.title
            binding.drawerItemIcon.setImageResource(row.icon)
            binding.drawerItemCount.text = if (row.count >= 0) row.count.toString() else ""
            binding.root.setOnClickListener { onClick(row) }
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ENTRY = 1
        private const val TYPE_FOOTER = 2

        private val DIFF = object : DiffUtil.ItemCallback<DrawerRow>() {
            override fun areItemsTheSame(old: DrawerRow, new: DrawerRow): Boolean = when {
                old is DrawerRow.Header && new is DrawerRow.Header -> old.title == new.title
                old is DrawerRow.Entry && new is DrawerRow.Entry -> old.key == new.key
                else -> old == new
            }

            override fun areContentsTheSame(old: DrawerRow, new: DrawerRow): Boolean = old == new
        }
    }
}
