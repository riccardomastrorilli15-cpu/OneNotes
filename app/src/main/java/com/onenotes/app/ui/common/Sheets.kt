package com.onenotes.app.ui.common

import android.content.Context
import android.content.DialogInterface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.onenotes.app.R
import com.onenotes.app.data.model.FolderWithCount
import com.onenotes.app.data.model.NoteListItem
import com.onenotes.app.data.model.TagWithCount
import com.onenotes.app.data.repository.FoldersRepository
import com.onenotes.app.databinding.ItemChoiceBinding
import com.onenotes.app.databinding.ItemColorSwatchBinding
import com.onenotes.app.databinding.SheetActionsBinding
import com.onenotes.app.databinding.SheetColorsBinding
import com.onenotes.app.databinding.SheetFoldersBinding
import com.onenotes.app.databinding.SheetTagsBinding
import com.onenotes.app.util.colorArray
import com.onenotes.app.util.setVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Actions offered by the "more" menu of a note. */
enum class NoteAction {
    PIN, UNPIN, ARCHIVE, UNARCHIVE, COLOR, FOLDER, TAGS, REMINDER,
    LOCK, UNLOCK, SHARE, EXPORT, DUPLICATE, COPY_TEXT, DELETE, RESTORE, DELETE_FOREVER
}

/**
 * The bottom sheets of the app. They follow the One UI style: rounded top corners, generous
 * spacing and a small drag handle, and they never cover the whole screen.
 */
object NoteSheets {

    // ------------------------------------------------------------ note actions

    fun showNoteOptions(
        context: Context,
        item: NoteListItem,
        inTrash: Boolean,
        onAction: (NoteAction) -> Unit
    ) {
        val binding = SheetActionsBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)
        binding.sheetTitle.text = item.note.title.ifBlank { context.getString(R.string.title_activity_editor) }

        val actions = ArrayList<Pair<Int, NoteAction>>()
        if (inTrash) {
            actions += R.string.action_restore to NoteAction.RESTORE
            actions += R.string.action_delete_forever to NoteAction.DELETE_FOREVER
        } else {
            if (item.note.pinned) {
                actions += R.string.action_unpin to NoteAction.UNPIN
            } else {
                actions += R.string.action_pin to NoteAction.PIN
            }
            if (item.note.archived) {
                actions += R.string.action_unarchive to NoteAction.UNARCHIVE
            } else {
                actions += R.string.action_archive to NoteAction.ARCHIVE
            }
            actions += R.string.action_color to NoteAction.COLOR
            actions += R.string.action_move_to_folder to NoteAction.FOLDER
            actions += R.string.action_tags to NoteAction.TAGS
            actions += R.string.action_reminder to NoteAction.REMINDER
            if (item.note.locked) {
                actions += R.string.action_unlock to NoteAction.UNLOCK
            } else {
                actions += R.string.action_lock to NoteAction.LOCK
            }
            actions += R.string.action_share to NoteAction.SHARE
            actions += R.string.action_export to NoteAction.EXPORT
            actions += R.string.action_duplicate to NoteAction.DUPLICATE
            actions += R.string.action_copy_text to NoteAction.COPY_TEXT
            actions += R.string.action_delete to NoteAction.DELETE
        }

        actions.forEach { (labelRes, action) ->
            val row = TextView(context).apply {
                text = context.getString(labelRes)
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.onenotes_text_primary))
                setCompoundDrawablesWithIntrinsicBounds(iconFor(action), 0, 0, 0)
                compoundDrawablePadding = dpToPx(context, 16)
                setPadding(dpToPx(context, 16), dpToPx(context, 12), dpToPx(context, 16), dpToPx(context, 12))
                setBackgroundResource(R.drawable.bg_drawer_item)
                setOnClickListener {
                    dialog.dismiss()
                    onAction(action)
                }
            }
            binding.sheetActionsContainer.addView(
                row,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        dialog.show()
    }

    private fun iconFor(action: NoteAction): Int = when (action) {
        NoteAction.PIN, NoteAction.UNPIN -> R.drawable.ic_pin
        NoteAction.ARCHIVE, NoteAction.UNARCHIVE -> R.drawable.ic_archive
        NoteAction.COLOR -> R.drawable.ic_text_color
        NoteAction.FOLDER -> R.drawable.ic_folder
        NoteAction.TAGS -> R.drawable.ic_tag
        NoteAction.REMINDER -> R.drawable.ic_reminder
        NoteAction.LOCK -> R.drawable.ic_lock
        NoteAction.UNLOCK -> R.drawable.ic_lock_open
        NoteAction.SHARE -> R.drawable.ic_share
        NoteAction.EXPORT -> R.drawable.ic_export
        NoteAction.DUPLICATE -> R.drawable.ic_copy
        NoteAction.COPY_TEXT -> R.drawable.ic_file
        NoteAction.DELETE, NoteAction.DELETE_FOREVER -> R.drawable.ic_delete
        NoteAction.RESTORE -> R.drawable.ic_restore
    }

    // ------------------------------------------------------------------ colours

    fun showColors(
        context: Context,
        title: String,
        colors: IntArray,
        names: Array<String>?,
        selected: Int,
        onPick: (Int) -> Unit
    ) {
        val binding = SheetColorsBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)
        binding.sheetColorsTitle.text = title

        val adapter = SwatchAdapter(colors, names, selected) { color ->
            dialog.dismiss()
            onPick(color)
        }
        binding.sheetColorsRecycler.layoutManager = GridLayoutManager(context, 6)
        binding.sheetColorsRecycler.adapter = adapter
        dialog.show()
    }

    /** Note colours of the app (index 0 = transparent = "no colour"). */
    fun noteColors(context: Context): IntArray = context.colorArray(R.array.note_colors)

    fun textColors(context: Context): IntArray = context.colorArray(R.array.text_colors)

    fun highlightColors(context: Context): IntArray = context.colorArray(R.array.highlight_colors)

    // ------------------------------------------------------------------ folders

    fun showFolders(
        context: Context,
        scope: CoroutineScope,
        repository: FoldersRepository,
        folders: List<FolderWithCount>,
        currentId: Long?,
        onPick: (Long?) -> Unit
    ) {
        val binding = SheetFoldersBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        val rows = ArrayList<ChoiceRow>()
        rows += ChoiceRow(
            key = 0L,
            title = context.getString(R.string.nav_no_folder),
            icon = R.drawable.ic_close,
            selected = currentId == null
        )
        folders.forEach { folder ->
            rows += ChoiceRow(
                key = folder.folder.id,
                title = folder.folder.name,
                icon = R.drawable.ic_folder,
                count = folder.noteCount,
                selected = folder.folder.id == currentId
            )
        }

        val adapter = ChoiceAdapter(rows) { row ->
            dialog.dismiss()
            onPick(if (row.key == 0L) null else row.key)
        }
        binding.sheetFoldersRecycler.layoutManager = LinearLayoutManager(context)
        binding.sheetFoldersRecycler.adapter = adapter

        binding.sheetFolderNew.setOnClickListener {
            showTextInput(
                context = context,
                title = context.getString(R.string.dialog_folder_name_title),
                hint = context.getString(R.string.dialog_folder_name_title),
                value = ""
            ) { name ->
                scope.launch {
                    val id = repository.create(name)
                    onPick(id)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    // --------------------------------------------------------------------- tags

    fun showTags(
        context: Context,
        tags: List<TagWithCount>,
        selectedNames: Set<String>,
        onDone: (List<String>) -> Unit
    ) {
        val binding = SheetTagsBinding.inflate(LayoutInflater.from(context))
        val dialog = BottomSheetDialog(context)
        dialog.setContentView(binding.root)

        val selected = selectedNames.toMutableSet()
        val rows = ArrayList<ChoiceRow>()

        fun refresh() {
            rows.clear()
            (selected + tags.map { it.tag.name })
                .distinct()
                .sortedWith(String.CASE_INSENSITIVE_ORDER)
                .forEach { name ->
                    rows += ChoiceRow(
                        key = 0L,
                        title = name,
                        icon = R.drawable.ic_tag,
                        selected = name in selected,
                        tag = name
                    )
                }
            binding.sheetTagsRecycler.adapter = ChoiceAdapter(rows.toList()) { row ->
                val name = row.tag.orEmpty()
                if (name in selected) selected.remove(name) else selected.add(name)
                refresh()
            }
        }

        binding.sheetTagsRecycler.layoutManager = LinearLayoutManager(context)
        refresh()

        binding.sheetTagAdd.setOnClickListener {
            val name = binding.sheetTagInput.text?.toString()?.trim().orEmpty()
            if (name.isNotEmpty()) {
                selected.add(name)
                binding.sheetTagInput.setText("")
                refresh()
            }
        }

        binding.sheetTagsDone.setOnClickListener {
            dialog.dismiss()
            onDone(selected.sortedWith(String.CASE_INSENSITIVE_ORDER))
        }

        dialog.show()
    }

    // ---------------------------------------------------------------- text input

    fun showTextInput(
        context: Context,
        title: String,
        hint: String,
        value: String,
        onDone: (String) -> Unit
    ) {
        val input = EditText(context).apply {
            setText(value)
            setHint(hint)
            setSingleLine()
            setPadding(dpToPx(context, 24), dpToPx(context, 8), dpToPx(context, 24), dpToPx(context, 8))
            setTextColor(ContextCompat.getColor(context, R.color.onenotes_text_primary))
            setHintTextColor(ContextCompat.getColor(context, R.color.onenotes_text_hint))
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(input)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(container)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _: DialogInterface, _: Int ->
                onDone(input.text?.toString()?.trim().orEmpty())
            }
            .create()
        dialog.show()
    }

    /** Confirmation dialog used before destructive actions. */
    fun confirm(
        context: Context,
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(confirmLabel) { _: DialogInterface, _: Int -> onConfirm() }
            .show()
    }

    /** Simple list dialog (export formats, sort order, …). */
    fun showList(
        context: Context,
        title: String,
        entries: Array<String>,
        onPick: (Int) -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setItems(entries) { _: DialogInterface, index: Int -> onPick(index) }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }
}

// ------------------------------------------------------------------- adapters

data class ChoiceRow(
    val key: Long,
    val title: String,
    val icon: Int,
    val count: Int = -1,
    val selected: Boolean = false,
    val tag: String? = null
)

private class ChoiceAdapter(
    private val rows: List<ChoiceRow>,
    private val onClick: (ChoiceRow) -> Unit
) : RecyclerView.Adapter<ChoiceAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemChoiceBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(rows[position])

    override fun getItemCount(): Int = rows.size

    inner class Holder(private val binding: ItemChoiceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(row: ChoiceRow) {
            binding.choiceName.text = row.title
            binding.choiceIcon.setImageResource(row.icon)
            binding.choiceSelected.setVisible(row.selected)
            binding.root.setOnClickListener { onClick(row) }
        }
    }
}

private class SwatchAdapter(
    private val colors: IntArray,
    private val names: Array<String>?,
    private val selected: Int,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<SwatchAdapter.Holder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemColorSwatchBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) =
        holder.bind(position, colors[position], colors[position] == selected)

    override fun getItemCount(): Int = colors.size

    inner class Holder(private val binding: ItemColorSwatchBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(index: Int, color: Int, isSelected: Boolean) {
            val swatch = binding.swatchColor
            val drawable = ContextCompat.getDrawable(itemView.context, R.drawable.bg_color_swatch)?.mutate()
            if (drawable is android.graphics.drawable.GradientDrawable) {
                if (color == 0) {
                    drawable.setColor(
                        ContextCompat.getColor(itemView.context, R.color.onenotes_surface_variant)
                    )
                } else {
                    drawable.setColor(color)
                }
            }
            swatch.setImageDrawable(drawable)
            swatch.contentDescription = names?.getOrNull(index) ?: itemView.context.getString(R.string.cd_note_color)
            binding.swatchSelected.setVisible(isSelected)
            itemView.setOnClickListener { onClick(color) }
        }
    }
}

private fun dpToPx(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()
