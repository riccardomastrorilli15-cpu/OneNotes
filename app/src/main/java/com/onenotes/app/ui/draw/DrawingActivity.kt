package com.onenotes.app.ui.draw

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.onenotes.app.OneNotesApp
import com.onenotes.app.R
import com.onenotes.app.databinding.ActivityDrawingBinding
import com.onenotes.app.ui.common.showPalette
import com.onenotes.app.util.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileOutputStream

/**
 * Full screen drawing canvas.
 *
 * It is opened from the editor (new drawing or edit of an existing one) and it returns the name
 * of the file where the strokes have been saved, together with the height of the drawing so that
 * the editor can reserve the right amount of space for it.
 */
class DrawingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawingBinding
    private val container by lazy { OneNotesApp.containerOf(this) }

    private var fileName: String? = null
    private var blockId: String? = null
    private var noteColor: Int = 0
    private var dirty = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fileName = intent.getStringExtra(EXTRA_FILE)?.takeIf { it.isNotBlank() }
        blockId = intent.getStringExtra(EXTRA_BLOCK_ID)
        noteColor = intent.getIntExtra(EXTRA_COLOR, 0)

        binding.drawingView.paperColor = if (noteColor != 0) noteColor else android.graphics.Color.TRANSPARENT
        binding.drawingView.onHistoryChanged = {
            dirty = binding.drawingView.strokes.isNotEmpty()
            refreshHistory()
        }

        loadExisting()
        setupTools()
        setupColors()
        setupSizes()
        lifecycleScope.launch {
            binding.drawingView.penOnly = container.settings.penOnly.first()
            binding.drawPenOnly.isChecked = binding.drawingView.penOnly
        }
        refreshHistory()
    }

    private fun loadExisting() {
        val name = fileName ?: return
        lifecycleScope.launch {
            val strokes = withContext(Dispatchers.IO) {
                runCatching { StrokeSerializer.fromJson(container.fileStorage.readDrawing(name)) }
                    .getOrDefault(mutableListOf())
            }
            binding.drawingView.load(strokes)
        }
    }

    private fun setupTools() {
        binding.drawClose.setOnClickListener { finish() }
        binding.drawUndo.setOnClickListener { binding.drawingView.undo() }
        binding.drawRedo.setOnClickListener { binding.drawingView.redo() }
        binding.drawClear.setOnClickListener { binding.drawingView.clear() }
        binding.drawSave.setOnClickListener { save() }
        binding.drawPenOnly.setOnCheckedChangeListener { _, checked ->
            binding.drawingView.penOnly = checked
            lifecycleScope.launch { container.settings.setPenOnly(checked) }
        }

        binding.drawToolPen.setOnClickListener { selectTool(DrawingView.Tool.PEN) }
        binding.drawToolHighlighter.setOnClickListener { selectTool(DrawingView.Tool.HIGHLIGHTER) }
        binding.drawToolEraser.setOnClickListener { selectTool(DrawingView.Tool.ERASER) }
        selectTool(DrawingView.Tool.PEN)
    }

    private fun selectTool(tool: DrawingView.Tool) {
        binding.drawingView.tool = tool
        binding.drawToolPen.isSelected = tool == DrawingView.Tool.PEN
        binding.drawToolHighlighter.isSelected = tool == DrawingView.Tool.HIGHLIGHTER
        binding.drawToolEraser.isSelected = tool == DrawingView.Tool.ERASER
        val tint = ContextCompat.getColor(this, R.color.onenotes_primary)
        val normal = ContextCompat.getColor(this, R.color.onenotes_text_primary)
        binding.drawToolPen.setColorFilter(if (tool == DrawingView.Tool.PEN) tint else normal)
        binding.drawToolHighlighter.setColorFilter(if (tool == DrawingView.Tool.HIGHLIGHTER) tint else normal)
        binding.drawToolEraser.setColorFilter(if (tool == DrawingView.Tool.ERASER) tint else normal)
    }

    private fun setupColors() {
        val colors = resources.getIntArray(R.array.pen_colors)
        if (binding.drawingView.strokeColor == 0) {
            binding.drawingView.strokeColor = colors.firstOrNull() ?: android.graphics.Color.BLACK
        }
        binding.drawColors.showPalette(colors, binding.drawingView.strokeColor) { color ->
            binding.drawingView.strokeColor = color
            setupColors()
        }
    }

    private fun setupSizes() {
        val sizes = resources.getIntArray(R.array.pen_sizes)
        binding.drawSizes.removeAllViews()
        sizes.forEach { size ->
            val dot = TextView(this).apply {
                text = "●"
                // The sizes of the array are in dp: a 24dp dot is a thick marker.
                textSize = size.toFloat()
                setTextColor(
                    if (binding.drawingView.strokeWidth == size.toFloat()) {
                        ContextCompat.getColor(this@DrawingActivity, R.color.onenotes_primary)
                    } else {
                        ContextCompat.getColor(this@DrawingActivity, R.color.onenotes_text_primary)
                    }
                )
                val pad = (8 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad / 2, pad, pad / 2)
                setOnClickListener {
                    binding.drawingView.strokeWidth = size.toFloat()
                    setupSizes()
                }
            }
            binding.drawSizes.addView(dot)
        }
    }

    private fun refreshHistory() {
        binding.drawUndo.isEnabled = binding.drawingView.canUndo()
        binding.drawUndo.alpha = if (binding.drawingView.canUndo()) 1f else 0.35f
        binding.drawRedo.isEnabled = binding.drawingView.canRedo()
        binding.drawRedo.alpha = if (binding.drawingView.canRedo()) 1f else 0.35f
    }

    private fun save() {
        val view = binding.drawingView
        if (view.strokes.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        lifecycleScope.launch {
            val name = fileName ?: container.fileStorage.newAttachmentName("json")
            val height = withContext(Dispatchers.IO) {
                container.fileStorage.writeDrawing(name, StrokeSerializer.toJson(view.strokes))
                writePreview(name, view)
                view.contentHeight()
            }
            val data = Intent()
                .putExtra(RESULT_FILE, name)
                .putExtra(RESULT_HEIGHT, height.coerceAtLeast(200))
                .putExtra(RESULT_BLOCK_ID, blockId)
            setResult(Activity.RESULT_OK, data)
            toast(R.string.draw_saved)
            dirty = false
            finish()
        }
    }

    /** PNG version of the drawing: it is what the editor shows inside the note. */
    private fun writePreview(name: String, view: DrawingView) {
        val width = view.width.coerceAtLeast(1)
        val height = view.height.coerceAtLeast(1)
        val rendered = DrawingRenderer.render(
            strokes = view.strokes,
            width = width,
            height = height,
            background = if (noteColor != 0) noteColor else android.graphics.Color.TRANSPARENT
        )
        val scale = (1080f / width).coerceAtMost(1f)
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(rendered, (width * scale).toInt(), (height * scale).toInt(), true)
        } else {
            rendered
        }
        val target = container.fileStorage.drawingPreview(name)
        FileOutputStream(target).use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (scaled !== rendered) scaled.recycle()
        if (rendered !== scaled) rendered.recycle()
    }

    override fun onBackPressed() {
        if (dirty) {
            save()
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        private const val EXTRA_FILE = "file"
        private const val EXTRA_BLOCK_ID = "block_id"
        private const val EXTRA_COLOR = "color"

        const val RESULT_FILE = "result_file"
        const val RESULT_HEIGHT = "result_height"
        const val RESULT_BLOCK_ID = "result_block_id"

        fun intent(context: Context, fileName: String?, blockId: String?, noteColor: Int): Intent =
            Intent(context, DrawingActivity::class.java)
                .putExtra(EXTRA_FILE, fileName)
                .putExtra(EXTRA_BLOCK_ID, blockId)
                .putExtra(EXTRA_COLOR, noteColor)
    }
}
