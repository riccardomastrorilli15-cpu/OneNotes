package com.onenotes.app.ui.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.onenotes.app.R
import com.onenotes.app.data.model.AudioBlock
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.BlockType
import com.onenotes.app.data.model.DrawingBlock
import com.onenotes.app.data.model.FileBlock
import com.onenotes.app.data.model.ImageBlock
import com.onenotes.app.data.model.SpanKind
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.data.repository.FileStorage
import com.onenotes.app.databinding.ItemBlockAudioBinding
import com.onenotes.app.databinding.ItemBlockDrawingBinding
import com.onenotes.app.databinding.ItemBlockFileBinding
import com.onenotes.app.databinding.ItemBlockImageBinding
import com.onenotes.app.databinding.ItemBlockTextBinding
import com.onenotes.app.util.TimeUtils
import com.onenotes.app.util.setVisible
import java.io.File

/** What the user did on an attachment. */
enum class AttachmentAction { OPEN, EDIT, REMOVE }

/**
 * Adapter of the editor: one row per block.
 *
 * The text of a block is kept inside the [TextBlock] instance, so the ViewModel always sees the
 * latest version even if the row has been recycled. The list is only re-submitted when the
 * structure of the note changes (a block added, removed or converted), never while typing.
 */
class BlockAdapter(
    private val storage: FileStorage,
    private val onEdit: () -> Unit,
    private val onSplit: (position: Int, index: Int) -> Unit,
    private val onMerge: (position: Int) -> Unit,
    private val onRemove: (position: Int) -> Unit,
    private val onFocus: (position: Int?) -> Unit,
    private val onAttachment: (block: Block, action: AttachmentAction) -> Unit
) : ListAdapter<Block, RecyclerView.ViewHolder>(DIFF) {

    private val handler = Handler(Looper.getMainLooper())
    private var audioHolder: AudioHolder? = null

    private var pendingFocusBlockId: String? = null
    private var pendingSelection: Int = 0

    fun requestFocusOn(blockId: String, selection: Int) {
        pendingFocusBlockId = blockId
        pendingSelection = selection
    }

    /** Called after submitList: moves the cursor where the user left it. */
    fun applyPendingFocus(recycler: RecyclerView) {
        val id = pendingFocusBlockId ?: return
        val position = currentList.indexOfFirst { it.id == id }
        if (position < 0) return
        recycler.post {
            val holder = recycler.findViewHolderForAdapterPosition(position)
            if (holder is TextHolder) {
                holder.focus(pendingSelection)
                pendingFocusBlockId = null
            } else {
                recycler.scrollToPosition(position)
                recycler.post {
                    (recycler.findViewHolderForAdapterPosition(position) as? TextHolder)
                        ?.focus(pendingSelection)
                    pendingFocusBlockId = null
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is TextBlock -> TYPE_TEXT
        is ImageBlock -> TYPE_IMAGE
        is AudioBlock -> TYPE_AUDIO
        is DrawingBlock -> TYPE_DRAWING
        is FileBlock -> TYPE_FILE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_IMAGE -> ImageHolder(
                ItemBlockImageBinding.inflate(inflater, parent, false),
                storage, onRemove, onAttachment
            )
            TYPE_AUDIO -> AudioHolder(
                ItemBlockAudioBinding.inflate(inflater, parent, false),
                storage, handler, onRemove, onAttachment, ::onAudioStarted
            )
            TYPE_DRAWING -> DrawingHolder(
                ItemBlockDrawingBinding.inflate(inflater, parent, false),
                storage, onRemove, onAttachment
            )
            TYPE_FILE -> FileHolder(
                ItemBlockFileBinding.inflate(inflater, parent, false),
                storage, onRemove, onAttachment
            )
            else -> TextHolder(
                ItemBlockTextBinding.inflate(inflater, parent, false),
                ::ordinalOf, onEdit, onSplit, onMerge, onFocus
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TextHolder -> holder.bind(getItem(position) as TextBlock, position)
            is ImageHolder -> holder.bind(getItem(position) as ImageBlock)
            is AudioHolder -> holder.bind(getItem(position) as AudioBlock)
            is DrawingHolder -> holder.bind(getItem(position) as DrawingBlock)
            is FileHolder -> holder.bind(getItem(position) as FileBlock)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is AudioHolder) holder.release()
        super.onViewRecycled(holder)
    }

    private fun onAudioStarted(holder: AudioHolder) {
        if (audioHolder != holder) {
            audioHolder?.release()
            audioHolder = holder
        }
    }

    /** Stops the playback when the editor is closed. */
    fun releaseAudio() {
        audioHolder?.release()
        audioHolder = null
    }

    /** Ordinal of a numbered block inside its group. */
    private fun ordinalOf(position: Int): Int {
        var ordinal = 1
        for (index in position - 1 downTo 0) {
            val previous = getOrNull(index)
            if (previous is TextBlock && previous.blockType == BlockType.NUMBER) ordinal++ else break
        }
        return ordinal
    }

    private fun getOrNull(position: Int): Block? = currentList.getOrNull(position)

    // ------------------------------------------------------------------ holders

    class TextHolder(
        private val binding: ItemBlockTextBinding,
        private val ordinalOf: (Int) -> Int,
        private val onEdit: () -> Unit,
        private val onSplit: (position: Int, index: Int) -> Unit,
        private val onMerge: (position: Int) -> Unit,
        private val onFocus: (position: Int?) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var block: TextBlock? = null
        private var programmatic = false
        private var pendingSplit: Int? = null

        private val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (programmatic) return
                if (count > before && start < s.length && s[start] == '\n') {
                    pendingSplit = start
                }
            }

            override fun afterTextChanged(s: Editable) {
                if (programmatic) return
                val splitAt = pendingSplit
                pendingSplit = null

                if (splitAt != null) {
                    val end = (splitAt + 1).coerceAtMost(s.length)
                    programmatic = true
                    s.delete(splitAt, end)
                    programmatic = false
                    commit(s.toString(), s)
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION) onSplit(position, splitAt)
                    return
                }

                commit(s.toString(), s)
                onEdit()
            }

            private fun commit(text: String, editable: Editable) {
                val current = block ?: return
                current.text = text
                current.spans = SpanMapper.extract(editable).toMutableList()
            }
        }

        init {
            binding.blockText.addTextChangedListener(watcher)
            binding.blockText.onBackspaceAtStart = {
                val position = bindingAdapterPosition
                if (position > 0) {
                    onMerge(position)
                    true
                } else {
                    false
                }
            }
            binding.blockText.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    onFocus(if (bindingAdapterPosition == RecyclerView.NO_POSITION) null else bindingAdapterPosition)
                }
            }
            binding.blockText.setOnClickListener {
                onFocus(bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION })
            }
        }

        fun bind(item: TextBlock, position: Int) {
            block = item
            programmatic = true
            binding.blockText.setText(SpanMapper.build(item.text, item.spans))
            programmatic = false

            val context = binding.root.context
            val isCheck = item.blockType == BlockType.CHECK
            val isQuote = item.blockType == BlockType.QUOTE
            val isBullet = item.blockType == BlockType.BULLET
            val isNumber = item.blockType == BlockType.NUMBER

            binding.blockCheck.setVisible(isCheck)
            binding.blockMarker.setVisible(isBullet || isNumber)
            binding.blockQuoteBar.setVisible(isQuote)

            if (isBullet) binding.blockMarker.text = "•"
            if (isNumber) binding.blockMarker.text = "${ordinalOf(position)}."

            binding.blockText.hint = if (isCheck) {
                context.getString(R.string.editor_checklist_hint)
            } else if (position == 0) {
                context.getString(R.string.editor_hint_body)
            } else {
                ""
            }

            val size = when (item.blockType) {
                BlockType.HEADING1 -> R.dimen.editor_heading_1
                BlockType.HEADING2 -> R.dimen.editor_heading_2
                BlockType.HEADING3 -> R.dimen.editor_heading_3
                else -> R.dimen.editor_text_size
            }
            binding.blockText.setTextSize(
                TypedValue.COMPLEX_UNIT_PX,
                context.resources.getDimension(size)
            )

            val bold = item.blockType in listOf(
                BlockType.HEADING1, BlockType.HEADING2, BlockType.HEADING3
            )
            val italic = item.blockType == BlockType.QUOTE
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            binding.blockText.setTypeface(Typeface.DEFAULT, style)

            val flags = if (item.checked) {
                binding.blockText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                binding.blockText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            binding.blockText.paintFlags = flags
            binding.blockText.alpha = if (item.checked) 0.6f else 1f

            // The listener is set to null first: it is a state change, not a user action.
            binding.blockCheck.setOnCheckedChangeListener(null)
            binding.blockCheck.isChecked = item.checked
            binding.blockCheck.setOnCheckedChangeListener { _, checked ->
                item.checked = checked
                binding.blockText.paintFlags = if (checked) {
                    binding.blockText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    binding.blockText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                }
                binding.blockText.alpha = if (checked) 0.6f else 1f
                onEdit()
            }
        }

        fun focus(selection: Int) {
            binding.blockText.requestFocus()
            val length = binding.blockText.text?.length ?: 0
            binding.blockText.setSelection(selection.coerceIn(0, length))
        }

        /** Current selection of the row, or null when nothing is selected. */
        fun selectionRange(): IntRange? {
            val start = binding.blockText.selectionStart
            val end = binding.blockText.selectionEnd
            if (start < 0 || end < 0 || end <= start) return null
            return start until end
        }

        fun textLength(): Int = binding.blockText.text?.length ?: 0

        /**
         * Applies (or removes) an inline format on the current selection.
         * The result is written back into the block, so it survives the recycling of the row.
         */
        fun toggleSpan(kind: SpanKind, value: String?) {
            val editable = binding.blockText.text ?: return
            val range = selectionRange() ?: return
            SpanMapper.toggle(editable, range.first, range.last + 1, kind, value)
            val current = block ?: return
            current.spans = SpanMapper.extract(editable).toMutableList()
            onEdit()
        }

        /** Removes every occurrence of [kind] from the current selection. */
        fun removeSpan(kind: SpanKind) {
            val editable = binding.blockText.text ?: return
            val range = selectionRange() ?: return
            SpanMapper.remove(editable, range.first, range.last + 1, kind)
            val current = block ?: return
            current.spans = SpanMapper.extract(editable).toMutableList()
            onEdit()
        }

        /** True when [kind] is already applied to the whole selection. */
        fun hasSpan(kind: SpanKind): Boolean {
            val editable = binding.blockText.text ?: return false
            val range = selectionRange() ?: return false
            return SpanMapper.hasKind(editable, range.first, range.last + 1, kind)
        }

        /** Text between [start] (exclusive) and the end of the row. */
        fun tailAfter(start: Int): String {
            val text = binding.blockText.text?.toString().orEmpty()
            return text.substring(start.coerceIn(0, text.length))
        }
    }

    class ImageHolder(
        private val binding: ItemBlockImageBinding,
        private val storage: FileStorage,
        private val onRemove: (position: Int) -> Unit,
        private val onAttachment: (block: Block, action: AttachmentAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ImageBlock) {
            val file = storage.attachment(item.fileName)
            val bitmap = loadImage(file, 1600)
            if (bitmap != null) {
                binding.blockImage.setImageBitmap(bitmap)
            } else {
                binding.blockImage.setImageResource(R.drawable.ic_image)
            }
            binding.blockImageName.text = item.name
            binding.blockRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onRemove(position)
            }
            binding.blockImage.setOnClickListener { onAttachment(item, AttachmentAction.OPEN) }
        }
    }

    class DrawingHolder(
        private val binding: ItemBlockDrawingBinding,
        private val storage: FileStorage,
        private val onRemove: (position: Int) -> Unit,
        private val onAttachment: (block: Block, action: AttachmentAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DrawingBlock) {
            val bitmap = loadImage(storage.drawingPreview(item.fileName), 1600)
            if (bitmap != null) {
                binding.blockDrawingPreview.setImageBitmap(bitmap)
            } else {
                binding.blockDrawingPreview.setImageResource(R.drawable.ic_pen)
            }
            binding.blockDrawingEdit.setOnClickListener { onAttachment(item, AttachmentAction.EDIT) }
            binding.blockRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onRemove(position)
            }
            binding.blockDrawingPreview.setOnClickListener { onAttachment(item, AttachmentAction.EDIT) }
        }
    }

    class FileHolder(
        private val binding: ItemBlockFileBinding,
        private val storage: FileStorage,
        private val onRemove: (position: Int) -> Unit,
        private val onAttachment: (block: Block, action: AttachmentAction) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: FileBlock) {
            binding.blockFileName.text = item.name.ifBlank { item.fileName }
            val file = storage.attachment(item.fileName)
            binding.blockFileSize.text =
                TimeUtils.fileSize(if (item.size > 0) item.size else file.length())
            binding.blockFileOpen.setOnClickListener { onAttachment(item, AttachmentAction.OPEN) }
            binding.blockRemove.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onRemove(position)
            }
        }
    }

    class AudioHolder(
        private val binding: ItemBlockAudioBinding,
        private val storage: FileStorage,
        private val handler: Handler,
        private val onRemove: (position: Int) -> Unit,
        private val onAttachment: (block: Block, action: AttachmentAction) -> Unit,
        private val onStarted: (AudioHolder) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        private var player: MediaPlayer? = null
        private var ticker: Runnable? = null
        private var file: File? = null

        fun bind(item: AudioBlock) {
            file = storage.attachment(item.fileName)
            binding.blockAudioName.text = item.name.ifBlank { "Registrazione" }
            binding.blockAudioDuration.text = TimeUtils.duration(item.durationMs)
            binding.blockAudioSeek.progress = 0
            binding.blockAudioPlay.setImageResource(R.drawable.ic_play)
            binding.blockAudioPlay.setOnClickListener { toggle() }
            binding.blockRemove.setOnClickListener {
                release()
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) onRemove(position)
            }
        }

        private fun toggle() {
            val current = player
            if (current?.isPlaying == true) {
                current.pause()
                binding.blockAudioPlay.setImageResource(R.drawable.ic_play)
                stopTicker()
                return
            }
            if (current != null) {
                current.start()
                binding.blockAudioPlay.setImageResource(R.drawable.ic_pause)
                startTicker()
                return
            }
            val target = file ?: return
            if (!target.exists()) return
            val created = try {
                MediaPlayer().apply {
                    setDataSource(target.absolutePath)
                    prepare()
                }
            } catch (t: Throwable) {
                return
            }
            created.setOnCompletionListener {
                binding.blockAudioPlay.setImageResource(R.drawable.ic_play)
                binding.blockAudioSeek.progress = binding.blockAudioSeek.max
                stopTicker()
            }
            player = created
            binding.blockAudioSeek.max = created.duration
            binding.blockAudioSeek.isEnabled = true
            binding.blockAudioSeek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seek: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) player?.seekTo(progress)
                }

                override fun onStartTrackingTouch(seek: SeekBar?) = Unit
                override fun onStopTrackingTouch(seek: SeekBar?) = Unit
            })
            created.start()
            binding.blockAudioPlay.setImageResource(R.drawable.ic_pause)
            onStarted(this)
            startTicker()
        }

        private fun startTicker() {
            stopTicker()
            val runnable = object : Runnable {
                override fun run() {
                    val current = player ?: return
                    if (current.isPlaying) {
                        binding.blockAudioSeek.progress = current.currentPosition
                        binding.blockAudioDuration.text = TimeUtils.duration(current.currentPosition.toLong())
                    }
                    ticker = this
                    handler.postDelayed(this, 250)
                }
            }
            ticker = runnable
            handler.post(runnable)
        }

        private fun stopTicker() {
            ticker?.let { handler.removeCallbacks(it) }
            ticker = null
        }

        fun release() {
            stopTicker()
            try {
                player?.stop()
            } catch (ignored: Throwable) {
                // The player was not started: nothing to stop.
            }
            player?.release()
            player = null
            binding.blockAudioPlay.setImageResource(R.drawable.ic_play)
        }
    }

    companion object {
        private const val TYPE_TEXT = 0
        private const val TYPE_IMAGE = 1
        private const val TYPE_AUDIO = 2
        private const val TYPE_DRAWING = 3
        private const val TYPE_FILE = 4

        /** Decodes an image with a sample size that keeps the memory usage low. */
        fun loadImage(file: File, maxWidth: Int): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / sample > maxWidth) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeFile(file.absolutePath, options)
        }

        private val DIFF = object : DiffUtil.ItemCallback<Block>() {
            override fun areItemsTheSame(oldItem: Block, newItem: Block): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Block, newItem: Block): Boolean {
                if (oldItem === newItem) return true
                // The text changes while typing: the row must not be rebound, otherwise the
                // cursor would jump. Only structural changes produce a new instance.
                if (oldItem is TextBlock && newItem is TextBlock) {
                    return oldItem.blockType == newItem.blockType && oldItem.checked == newItem.checked
                }
                return oldItem == newItem
            }
        }
    }
}
