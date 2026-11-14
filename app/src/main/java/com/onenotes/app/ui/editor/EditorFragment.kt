package com.onenotes.app.ui.editor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.data.model.AudioBlock
import com.onenotes.app.data.model.Block
import com.onenotes.app.data.model.BlockType
import com.onenotes.app.data.model.DrawingBlock
import com.onenotes.app.data.model.FileBlock
import com.onenotes.app.data.model.ImageBlock
import com.onenotes.app.data.model.InlineSpan
import com.onenotes.app.data.model.SpanKind
import com.onenotes.app.data.model.TextBlock
import com.onenotes.app.databinding.FragmentEditorBinding
import com.onenotes.app.di.AppContainer
import com.onenotes.app.ui.common.NoteSheets
import com.onenotes.app.ui.common.Pickers
import com.onenotes.app.ui.common.showPalette
import com.onenotes.app.ui.draw.DrawingActivity
import com.onenotes.app.ui.lock.LockActivity
import com.onenotes.app.ui.main.MainActivity
import com.onenotes.app.ui.scan.ScanActivity
import com.onenotes.app.util.contentUriFor
import com.onenotes.app.util.hideKeyboard
import com.onenotes.app.util.openFile
import com.onenotes.app.util.shareFile
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The editor of a note.
 *
 * The same fragment is used full screen (inside [EditorActivity]) and as the right pane of
 * [MainActivity] on tablets and on the unfolded Fold: everything that belongs to the app bar
 * lives in the activity, everything that belongs to the content lives here.
 */
class EditorFragment : Fragment() {

    /** Identifier of the note being edited. */
    val noteId: Long
        get() = arguments?.getLong(ARG_NOTE_ID) ?: 0L

    private var _binding: FragmentEditorBinding? = null
    private val binding get() = _binding!!

    /** Public because the host activity builds its app bar from the state of the note. */
    val viewModel: EditorViewModel by viewModels()

    private lateinit var appContainer: AppContainer
    private lateinit var adapter: BlockAdapter

    private var focusedPosition: Int? = null
    private var unlocked = false
    private var paletteColors: IntArray? = null

    private var cameraFile: File? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingStart = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var recordingDialog: AlertDialog? = null

    // ------------------------------------------------------------------ results

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) insertImage(uri)
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) insertFile(uri)
    }

    private val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        if (saved) insertCameraPhoto()
    }

    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else requireContext().toast(R.string.permission_audio_title)
    }

    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) pickReminder() else requireContext().toast(R.string.permission_notifications_title)
    }

    /** Asks the user to verify his identity (biometrics or device PIN). */
    private val authenticate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            closeEditor()
            return@registerForActivityResult
        }
        val reason = result.data?.getStringExtra(LockActivity.RESULT_REASON).orEmpty()
        unlocked = true
        if (reason == LockActivity.REASON_LOCK) {
            viewModel.setLocked(true)
            requireContext().toast(R.string.msg_note_locked)
        }
        showLocked(false)
    }

    private val drawLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val name = data.getStringExtra(DrawingActivity.RESULT_FILE) ?: return@registerForActivityResult
        val height = data.getIntExtra(DrawingActivity.RESULT_HEIGHT, 0)
        val editedId = data.getStringExtra(DrawingActivity.RESULT_BLOCK_ID)
        if (editedId != null) {
            updateDrawing(editedId, name, height)
        } else {
            insertBlock(DrawingBlock(fileName = name, height = height))
        }
    }

    private val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val name = data.getStringExtra(ScanActivity.RESULT_FILE) ?: return@registerForActivityResult
        insertBlock(ImageBlock(fileName = name, name = getString(R.string.title_activity_scan)))
        val text = data.getStringExtra(ScanActivity.RESULT_TEXT)
        if (!text.isNullOrBlank()) appendText(text)
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        appContainer = OneNotesApp.containerOf(requireContext())
        setupRecycler()
        setupTitle()
        setupFormatBar()
        setupLockOverlay()
        observeState()
        viewModel.load(noteId)
    }

    override fun onPause() {
        // In the two pane layout the pane can be replaced at any moment: the pending
        // autosave would die with the fragment, so the note is flushed here.
        saveNow()
        super.onPause()
    }

    override fun onDestroyView() {
        stopRecording(false)
        adapter.releaseAudio()
        binding.editorTitle.setOnFocusChangeListener(null)
        _binding = null
        super.onDestroyView()
    }

    // -------------------------------------------------------------------- setup

    private fun setupRecycler() {
        adapter = BlockAdapter(
            storage = appContainer.fileStorage,
            onEdit = { viewModel.markDirty() },
            onSplit = { position, index -> splitBlock(position, index) },
            onMerge = { position -> mergeBlock(position) },
            onRemove = { position -> removeBlock(position) },
            onFocus = { position ->
                focusedPosition = position
                hidePalette()
            },
            onAttachment = { block, action -> handleAttachment(block, action) }
        )
        binding.editorBlocks.layoutManager = LinearLayoutManager(requireContext())
        binding.editorBlocks.adapter = adapter
        binding.editorBlocks.itemAnimator = null
        adapter.submitList(viewModel.blocks.toList())
    }

    private fun setupTitle() {
        val input = binding.editorTitle
        input.setText(viewModel.title)
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable) {
                viewModel.setTitle(s.toString())
            }
        })
    }

    private fun setupLockOverlay() {
        binding.editorUnlock.setOnClickListener { requestUnlock() }
    }

    private fun observeState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.note.collect { note ->
                        if (note != null && note.locked && !unlocked) {
                            showLocked(true)
                            requestUnlock()
                        } else {
                            showLocked(false)
                        }
                        updateMenuState()
                    }
                }
                launch {
                    viewModel.revision.collect {
                        // An undo can bring back a previous title.
                        if (binding.editorTitle.text?.toString() != viewModel.title) {
                            binding.editorTitle.setText(viewModel.title)
                        }
                        adapter.submitList(viewModel.blocks.toList()) {
                            adapter.applyPendingFocus(binding.editorBlocks)
                        }
                        ensureTrailingBlock()
                    }
                }
                launch {
                    viewModel.canUndo.collect { enabled ->
                        binding.btnUndo.isEnabled = enabled
                        binding.btnUndo.alpha = if (enabled) 1f else 0.35f
                    }
                }
                launch {
                    viewModel.canRedo.collect { enabled ->
                        binding.btnRedo.isEnabled = enabled
                        binding.btnRedo.alpha = if (enabled) 1f else 0.35f
                    }
                }
            }
        }
    }

    private fun showLocked(locked: Boolean) {
        binding.editorLocked.visibility = if (locked) View.VISIBLE else View.GONE
        binding.editorTitle.visibility = if (locked) View.GONE else View.VISIBLE
    }

    private fun requestUnlock() {
        authenticate.launch(LockActivity.intent(requireContext(), LockActivity.REASON_UNLOCK))
    }

    private fun updateMenuState() {
        activity?.invalidateOptionsMenu()
    }

    // -------------------------------------------------------------- format bar

    private fun setupFormatBar() {
        binding.btnBold.setOnClickListener { applySpan(SpanKind.BOLD) }
        binding.btnItalic.setOnClickListener { applySpan(SpanKind.ITALIC) }
        binding.btnUnderline.setOnClickListener { applySpan(SpanKind.UNDERLINE) }
        binding.btnStrike.setOnClickListener { applySpan(SpanKind.STRIKETHROUGH) }
        binding.btnHeading.setOnClickListener { cycleHeading() }
        binding.btnBullet.setOnClickListener { setBlockType(BlockType.BULLET) }
        binding.btnNumber.setOnClickListener { setBlockType(BlockType.NUMBER) }
        binding.btnCheck.setOnClickListener { setBlockType(BlockType.CHECK) }
        binding.btnQuote.setOnClickListener { setBlockType(BlockType.QUOTE) }
        binding.btnTextColor.setOnClickListener { showPaletteFor(false) }
        binding.btnHighlight.setOnClickListener { showPaletteFor(true) }
        binding.btnLink.setOnClickListener { promptLink() }

        binding.btnImage.setOnClickListener { pickImage.launch("image/*") }
        binding.btnCamera.setOnClickListener { capturePhoto() }
        binding.btnAudio.setOnClickListener { requestRecord() }
        binding.btnFile.setOnClickListener { pickFile.launch("*/*") }
        binding.btnDrawing.setOnClickListener { openDrawing(null) }
        binding.btnScan.setOnClickListener { scanLauncher.launch(ScanActivity.intent(requireContext())) }

        binding.btnUndo.setOnClickListener { viewModel.undo() }
        binding.btnRedo.setOnClickListener { viewModel.redo() }
    }

    private fun focusedTextPosition(): Int? {
        val position = focusedPosition ?: return null
        return if (viewModel.blocks.getOrNull(position) is TextBlock) position else null
    }

    private fun holderAt(position: Int): BlockAdapter.TextHolder? =
        binding.editorBlocks.findViewHolderForAdapterPosition(position) as? BlockAdapter.TextHolder

    private fun applySpan(kind: SpanKind, value: String? = null) {
        val position = focusedTextPosition() ?: return
        holderAt(position)?.toggleSpan(kind, value)
    }

    private fun showPaletteFor(highlight: Boolean) {
        val colors = if (highlight) {
            NoteSheets.highlightColors(requireContext())
        } else {
            NoteSheets.textColors(requireContext())
        }
        if (paletteColors.contentEquals(colors) && binding.colorPaletteRow.visibility == View.VISIBLE) {
            hidePalette()
            return
        }
        paletteColors = colors
        binding.colorPaletteRow.showPalette(colors, 0) { color ->
            val kind = if (highlight) SpanKind.HIGHLIGHT else SpanKind.COLOR
            val value = if (color == 0) null else SpanMapper.colorToHex(color)
            applySpan(kind, value)
            hidePalette()
        }
        binding.colorPaletteRow.visibility = View.VISIBLE
    }

    private fun hidePalette() {
        if (_binding == null) return
        if (binding.colorPaletteRow.visibility == View.VISIBLE) {
            binding.colorPaletteRow.visibility = View.GONE
            paletteColors = null
        }
    }

    private fun promptLink() {
        val position = focusedTextPosition() ?: return
        val holder = holderAt(position) ?: return
        Pickers.link(requireContext(), "") { url ->
            if (url.isEmpty()) {
                holder.removeSpan(SpanKind.LINK)
            } else {
                holder.toggleSpan(SpanKind.LINK, url)
            }
        }
    }

    private fun cycleHeading() {
        val position = focusedTextPosition() ?: return
        val block = viewModel.blocks.getOrNull(position) as? TextBlock ?: return
        val next = when (block.blockType) {
            BlockType.HEADING1 -> BlockType.HEADING2
            BlockType.HEADING2 -> BlockType.HEADING3
            BlockType.HEADING3 -> BlockType.TEXT
            else -> BlockType.HEADING1
        }
        changeBlockType(position, next)
    }

    private fun setBlockType(type: BlockType) {
        val position = focusedTextPosition() ?: return
        val block = viewModel.blocks.getOrNull(position) as? TextBlock ?: return
        changeBlockType(position, if (block.blockType == type) BlockType.TEXT else type)
    }

    private fun changeBlockType(position: Int, type: BlockType) {
        val block = viewModel.blocks.getOrNull(position) as? TextBlock ?: return
        viewModel.change { block.blockType = type }
        viewModel.markDirty()
    }

    // ------------------------------------------------------------ block editing

    private fun splitBlock(position: Int, index: Int) {
        val current = viewModel.blocks.getOrNull(position) as? TextBlock ?: return
        val safeIndex = index.coerceIn(0, current.text.length)
        val head = current.text.substring(0, safeIndex)
        val tail = current.text.substring(safeIndex)

        val headSpans = ArrayList<InlineSpan>()
        val tailSpans = ArrayList<InlineSpan>()
        current.spans.forEach { span ->
            when {
                span.end <= safeIndex -> headSpans += span
                span.start >= safeIndex ->
                    tailSpans += span.copy(start = span.start - safeIndex, end = span.end - safeIndex)
                else -> {
                    headSpans += span.copy(end = safeIndex)
                    tailSpans += span.copy(start = 0, end = span.end - safeIndex)
                }
            }
        }

        val next = TextBlock(
            blockType = if (current.blockType == BlockType.CHECK) BlockType.CHECK else current.blockType,
            text = tail,
            spans = tailSpans
        )
        viewModel.change {
            current.text = head
            current.spans.clear()
            current.spans.addAll(headSpans)
            it.add(position + 1, next)
        }
        adapter.requestFocusOn(next.id, 0)
    }

    private fun mergeBlock(position: Int) {
        val blocks = viewModel.blocks
        val current = blocks.getOrNull(position) as? TextBlock ?: return
        if (current.blockType != BlockType.TEXT && current.blockType != BlockType.CHECK) {
            // First backspace removes the style, exactly like Samsung Notes.
            viewModel.change { current.blockType = BlockType.TEXT }
            viewModel.markDirty()
            return
        }
        val previous = blocks.getOrNull(position - 1) as? TextBlock ?: return
        val offset = previous.text.length
        val merged = ArrayList<InlineSpan>(previous.spans)
        current.spans.forEach { span ->
            merged += span.copy(start = span.start + offset, end = span.end + offset)
        }
        viewModel.change {
            previous.text = previous.text + current.text
            previous.spans.clear()
            previous.spans.addAll(merged)
            it.removeAt(position)
        }
        adapter.requestFocusOn(previous.id, offset)
    }

    private fun removeBlock(position: Int) {
        if (viewModel.blocks.isEmpty()) return
        viewModel.change {
            if (position in it.indices) it.removeAt(position)
        }
        focusedPosition = null
    }

    /** The editor always ends with an empty text block, ready for the next paragraph. */
    private fun ensureTrailingBlock() {
        val last = viewModel.blocks.lastOrNull()
        if (last is TextBlock) return
        viewModel.change { it.add(TextBlock()) }
    }

    private fun insertPosition(): Int {
        val size = viewModel.blocks.size
        val position = focusedPosition ?: (size - 1)
        return (position + 1).coerceIn(0, size)
    }

    private fun insertBlock(block: Block) {
        val at = insertPosition()
        viewModel.change { it.add(at, block) }
        ensureTrailingBlock()
    }

    private fun appendText(text: String) {
        val block = TextBlock(text = text)
        viewModel.change { it.add(insertPosition(), block) }
        ensureTrailingBlock()
    }

    // ------------------------------------------------------------- attachments

    private fun insertImage(uri: Uri) {
        val ext = extensionOf(uri, "jpg")
        viewLifecycleOwner.lifecycleScope.launch {
            val name = importTo(uri, ext)
            if (name == null) {
                requireContext().toast(R.string.msg_error_generic)
                return@launch
            }
            val file = appContainer.fileStorage.attachment(name)
            val size = imageSize(file)
            insertBlock(
                ImageBlock(
                    fileName = name,
                    name = displayNameOf(uri),
                    width = size.first,
                    height = size.second
                )
            )
        }
    }

    private fun insertFile(uri: Uri) {
        val ext = extensionOf(uri, "bin")
        viewLifecycleOwner.lifecycleScope.launch {
            val name = importTo(uri, ext)
            if (name == null) {
                requireContext().toast(R.string.msg_error_generic)
                return@launch
            }
            val file = appContainer.fileStorage.attachment(name)
            insertBlock(
                FileBlock(
                    fileName = name,
                    name = displayNameOf(uri),
                    size = file.length(),
                    mime = requireContext().contentResolver.getType(uri).orEmpty()
                )
            )
        }
    }

    private fun capturePhoto() {
        val file = File(appContainer.fileStorage.attachmentsDir(), "camera_${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = runCatching { requireContext().contentUriFor(file) }.getOrNull()
        if (uri == null) {
            requireContext().toast(R.string.msg_error_camera)
            return
        }
        runCatching { takePhoto.launch(uri) }
            .onFailure { requireContext().toast(R.string.msg_error_camera) }
    }

    private fun insertCameraPhoto() {
        val file = cameraFile ?: return
        cameraFile = null
        if (!file.exists() || file.length() == 0L) {
            requireContext().toast(R.string.msg_error_camera)
            return
        }
        val name = appContainer.fileStorage.newAttachmentName("jpg")
        val target = appContainer.fileStorage.attachment(name)
        file.copyTo(target, overwrite = true)
        file.delete()
        val size = imageSize(target)
        insertBlock(ImageBlock(fileName = name, name = getString(R.string.editor_insert_photo),
            width = size.first, height = size.second))
    }

    private fun openDrawing(block: DrawingBlock?) {
        val intent = DrawingActivity.intent(
            context = requireContext(),
            fileName = block?.fileName,
            blockId = block?.id,
            noteColor = viewModel.note.value?.color ?: 0
        )
        drawLauncher.launch(intent)
    }

    private fun updateDrawing(blockId: String, fileName: String, height: Int) {
        val block = viewModel.blocks.firstOrNull { it.id == blockId } as? DrawingBlock ?: return
        viewModel.change {
            block.fileName = fileName
            block.height = height
        }
    }

    private fun handleAttachment(block: Block, action: AttachmentAction) {
        when (action) {
            AttachmentAction.OPEN -> openAttachment(block)
            AttachmentAction.EDIT -> if (block is DrawingBlock) openDrawing(block) else openAttachment(block)
            AttachmentAction.REMOVE -> {
                val position = viewModel.blocks.indexOfFirst { it.id == block.id }
                if (position >= 0) removeBlock(position)
            }
        }
    }

    private fun openAttachment(block: Block) {
        val name = when (block) {
            is ImageBlock -> block.fileName
            is AudioBlock -> block.fileName
            is FileBlock -> block.fileName
            is DrawingBlock -> block.fileName
            is TextBlock -> null
        } ?: return
        val file = appContainer.fileStorage.attachment(name)
        if (!file.exists()) {
            requireContext().toast(R.string.msg_attachment_missing)
            return
        }
        val mime = when (block) {
            is ImageBlock -> "image/*"
            is AudioBlock -> "audio/*"
            is FileBlock -> block.mime.ifBlank { "*/*" }
            else -> "*/*"
        }
        requireContext().openFile(file, mime)
    }

    private fun extensionOf(uri: Uri, fallback: String): String {
        val mime = requireContext().contentResolver.getType(uri)
        val ext = mime?.let { MimeTypeMap.getSingleton().getExtensionFromMimeType(it) }
        return if (ext.isNullOrBlank()) fallback else ext
    }

    private fun displayNameOf(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && it.moveToFirst()) return it.getString(index)
        }
        return uri.lastPathSegment.orEmpty()
    }

    private fun imageSize(file: File): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return options.outWidth to options.outHeight
    }

    private suspend fun importTo(uri: Uri, extension: String): String? = withContext(Dispatchers.IO) {
        runCatching { appContainer.fileStorage.import(uri, extension) }.getOrNull()
    }

    // ---------------------------------------------------------------- recording

    private fun requestRecord() {
        val context = requireContext()
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            startRecording()
            return
        }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            AlertDialog.Builder(context)
                .setTitle(R.string.permission_audio_title)
                .setMessage(R.string.permission_audio_message)
                .setNegativeButton(R.string.permission_deny, null)
                .setPositiveButton(R.string.permission_grant) { _, _ ->
                    requestAudio.launch(Manifest.permission.RECORD_AUDIO)
                }
                .show()
        } else {
            requestAudio.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val file = File(
            appContainer.fileStorage.attachmentsDir(),
            appContainer.fileStorage.newAttachmentName("m4a")
        )
        val mediaRecorder = try {
            MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (t: Throwable) {
            requireContext().toast(R.string.record_error)
            return
        }
        recorder = mediaRecorder
        recordingFile = file
        recordingStart = SystemClock.elapsedRealtime()

        val message = android.widget.TextView(requireContext()).apply {
            text = getString(R.string.record_title)
            textSize = 15f
            val pad = (24 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.onenotes_text_primary))
        }
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.editor_insert_audio)
            .setView(message)
            .setNegativeButton(R.string.record_stop) { _, _ -> stopRecording(true) }
            .setCancelable(false)
            .create()
        dialog.show()
        recordingDialog = dialog
        handler.post(object : Runnable {
            override fun run() {
                if (recorder == null) return
                message.text = com.onenotes.app.util.TimeUtils.duration(
                    SystemClock.elapsedRealtime() - recordingStart
                )
                handler.postDelayed(this, 500)
            }
        })
    }

    private fun stopRecording(keep: Boolean) {
        val mediaRecorder = recorder ?: return
        recorder = null
        val file = recordingFile
        recordingFile = null
        val elapsed = SystemClock.elapsedRealtime() - recordingStart
        recordingDialog?.dismiss()
        recordingDialog = null
        try {
            mediaRecorder.stop()
        } catch (ignored: Throwable) {
            // Nothing was recorded: the file is discarded below.
        }
        mediaRecorder.release()
        if (!keep || file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            return
        }
        insertBlock(
            AudioBlock(
                fileName = file.name,
                name = getString(R.string.editor_insert_audio),
                durationMs = elapsed
            )
        )
    }

    // -------------------------------------------------------------- note menu

    /** Called by the host activity when one of the menu items of the editor is tapped. */
    fun onMenuAction(itemId: Int): Boolean {
        val context = context ?: return false
        val note = viewModel.note.value
        when (itemId) {
            R.id.action_pin -> {
                val pinned = note?.pinned == true
                viewModel.setPinned(!pinned)
                context.toast(if (pinned) R.string.action_unpin else R.string.action_pin)
            }
            R.id.action_color -> showNoteColor()
            R.id.action_reminder -> {
                if (note?.reminderAt != null) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_reminder_title)
                        .setNegativeButton(R.string.action_cancel, null)
                        .setPositiveButton(R.string.action_remove_reminder) { _, _ ->
                            viewModel.setReminder(null)
                            context.toast(R.string.msg_reminder_removed)
                        }
                        .show()
                } else {
                    pickReminder()
                }
            }
            R.id.action_lock -> toggleLock()
            R.id.action_share -> viewModel.share(context)
            R.id.action_duplicate -> {
                lifecycleScope.launch {
                    val newId = viewModel.duplicate()
                    if (newId != 0L) openNote(newId)
                }
            }
            R.id.action_tags -> showTags()
            R.id.action_archive -> {
                val archived = note?.archived == true
                viewModel.setArchived(!archived)
                context.toast(if (archived) R.string.action_unarchive else R.string.msg_note_archived)
            }
            R.id.action_delete -> {
                NoteSheets.confirm(
                    context = context,
                    title = context.getString(R.string.dialog_delete_title),
                    message = context.getString(R.string.dialog_delete_message),
                    confirmLabel = context.getString(R.string.action_delete)
                ) {
                    viewModel.trash()
                    context.toast(R.string.msg_note_moved_to_trash)
                    closeEditor()
                }
            }
            R.id.action_export -> showExport()
            else -> return false
        }
        return true
    }

    private fun pickReminder() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        Pickers.dateTime(requireContext(), null) { at ->
            viewModel.setReminder(at)
            requireContext().toast(R.string.msg_reminder_set)
        }
    }

    private fun showNoteColor() {
        val colors = NoteSheets.noteColors(requireContext())
        NoteSheets.showColors(
            context = requireContext(),
            title = getString(R.string.action_color),
            colors = colors,
            names = resources.getStringArray(R.array.note_color_names),
            selected = viewModel.note.value?.color ?: 0
        ) { color ->
            viewModel.setColor(color)
        }
    }

    private fun toggleLock() {
        val locked = viewModel.note.value?.locked == true
        if (locked) {
            viewModel.setLocked(false)
            requireContext().toast(R.string.msg_note_unlocked)
        } else {
            authenticate.launch(
                LockActivity.intent(
                    context = requireContext(),
                    reason = LockActivity.REASON_LOCK,
                    title = getString(R.string.lock_title)
                )
            )
        }
    }

    private fun showTags() {
        viewLifecycleOwner.lifecycleScope.launch {
            val all = appContainer.tags.observeWithCount().first()
            val selected = viewModel.currentTagNames().toSet()
            NoteSheets.showTags(requireContext(), all, selected) { names ->
                viewModel.setTagNames(names)
            }
        }
    }

    private fun showExport() {
        val context = requireContext()
        val entries = context.resources.getStringArray(R.array.export_format_entries)
        val values = context.resources.getStringArray(R.array.export_format_values)
        NoteSheets.showList(context, context.getString(R.string.dialog_export_title), entries) { index ->
            val format = values.getOrNull(index) ?: "md"
            viewModel.export(context, format) { file ->
                val mime = when (format) {
                    "pdf" -> "application/pdf"
                    else -> "text/plain"
                }
                context.shareFile(file, mime, viewModel.title.ifBlank { context.getString(R.string.app_name) })
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Saves the note and returns true when it has been deleted because it was empty. */
    suspend fun saveAndClose(): Boolean {
        binding.editorTitle.hideKeyboard()
        return viewModel.save()
    }

    fun saveNow() = viewModel.saveNow()

    private fun closeEditor() {
        when (val host = activity) {
            is EditorActivity -> host.finish()
            is MainActivity -> host.closeEditorPane()
            else -> host?.finish()
        }
    }

    private fun openNote(id: Long) {
        when (val host = activity) {
            is MainActivity -> host.openNote(id)
            else -> startActivity(EditorActivity.newIntent(requireContext(), id))
        }
    }

    companion object {
        private const val ARG_NOTE_ID = "note_id"

        fun newInstance(noteId: Long): EditorFragment = EditorFragment().apply {
            arguments = Bundle().apply { putLong(ARG_NOTE_ID, noteId) }
        }
    }
}
