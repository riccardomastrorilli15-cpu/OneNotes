package com.onenotes.app.ui.draw

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.max

/**
 * The freehand canvas of OneNotes.
 *
 * Strokes are kept as vectors (see [Stroke]) so that they can be re-rendered at any size, and
 * a bitmap cache holds everything that has already been drawn: while the user is drawing only
 * the stroke in progress is re-painted, which keeps the canvas smooth even with hundreds of
 * strokes on the screen.
 *
 * The S Pen is fully supported: the pressure modulates the thickness of the line (see
 * [DrawingRenderer.widthFor]) and the button of the pen turns the tip into an eraser.
 */
class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Tool { PEN, HIGHLIGHTER, ERASER }

    var tool: Tool = Tool.PEN
    var strokeColor: Int = Color.BLACK
    var strokeWidth: Float = 6f
    var penOnly: Boolean = false
    var paperColor: Int = Color.TRANSPARENT

    /** Called whenever the undo / redo stacks change. */
    var onHistoryChanged: (() -> Unit)? = null

    val strokes: MutableList<Stroke> = ArrayList()
    private val undone: MutableList<Stroke> = ArrayList()

    private var active: Stroke? = null
    private var activePointerId: Int = MotionEvent.INVALID_POINTER_ID
    private var cache: Bitmap? = null
    private var cacheCanvas: Canvas? = null
    private var strokeEraser = false

    // ------------------------------------------------------------------ content

    fun canUndo(): Boolean = strokes.isNotEmpty()
    fun canRedo(): Boolean = undone.isNotEmpty()

    fun undo() {
        val last = strokes.removeLastOrNull() ?: return
        undone.add(last)
        rebuildCache()
        historyChanged()
    }

    fun redo() {
        val next = undone.removeLastOrNull() ?: return
        strokes.add(next)
        rebuildCache()
        historyChanged()
    }

    fun clear() {
        if (strokes.isEmpty()) return
        undone.addAll(strokes)
        strokes.clear()
        rebuildCache()
        historyChanged()
    }

    fun load(newStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(newStrokes)
        undone.clear()
        rebuildCache()
        historyChanged()
    }

    /** Bottom of the drawing, in the coordinates of the canvas. */
    fun contentHeight(): Int {
        var bottom = 0f
        strokes.forEach { stroke ->
            stroke.points.forEach { point -> bottom = max(bottom, point.y) }
        }
        return bottom.toInt()
    }

    private fun historyChanged() {
        onHistoryChanged?.invoke()
    }

    // ------------------------------------------------------------------ drawing

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildCache()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        cache?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        val current = active ?: return
        canvas.save()
        DrawingRenderer.draw(canvas, listOf(current))
        canvas.restore()
    }

    private fun rebuildCache() {
        val width = width
        val height = height
        if (width <= 0 || height <= 0) return
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (paperColor != Color.TRANSPARENT) {
            canvas.drawColor(paperColor)
        } else {
            canvas.drawColor(Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
        }
        DrawingRenderer.draw(canvas, strokes)
        cache = bitmap
        cacheCanvas = canvas
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = event.actionIndex
        val isStylus = event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS

        // "S Pen only" mode: the hand resting on the screen must not draw.
        if (penOnly && !isStylus) return true

        // While a stroke is in progress every other pointer is ignored (palm rejection).
        if (active != null && event.getPointerId(index) != activePointerId) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                startStroke(event, index, isStylus)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                extendStroke(event, index)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                endStroke(event.actionMasked == MotionEvent.ACTION_CANCEL)
                return true
            }
        }
        return true
    }

    private fun startStroke(event: MotionEvent, index: Int, isStylus: Boolean) {
        val eraser = tool == Tool.ERASER ||
            (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
        val pressure = if (isStylus) pressureOf(event, index) else 0.5f
        strokeEraser = eraser
        active = Stroke(
            color = if (eraser) Color.BLACK else strokeColor,
            baseWidth = if (tool == Tool.HIGHLIGHTER && !eraser) strokeWidth * 3f else strokeWidth,
            alpha = if (tool == Tool.HIGHLIGHTER && !eraser) 140 else 255,
            eraser = eraser,
            highlighter = tool == Tool.HIGHLIGHTER && !eraser,
            points = mutableListOf(DrawPoint(event.getX(index), event.getY(index), pressure))
        )
        activePointerId = event.getPointerId(index)
        invalidate()
    }

    private fun extendStroke(event: MotionEvent, index: Int) {
        val stroke = active ?: return
        val x = event.getX(index)
        val y = event.getY(index)
        val last = stroke.points.lastOrNull()
        if (last != null && DrawingRenderer.distance(last.x, last.y, x, y) < 1.5f) return
        val isStylus = event.getToolType(index) == MotionEvent.TOOL_TYPE_STYLUS
        stroke.points.add(DrawPoint(x, y, if (isStylus) pressureOf(event, index) else 0.5f))
        invalidate()
    }

    private fun endStroke(cancelled: Boolean) {
        val stroke = active ?: return
        active = null
        activePointerId = MotionEvent.INVALID_POINTER_ID
        if (cancelled) {
            invalidate()
            return
        }
        if (stroke.points.size == 1) {
            // A single tap is a dot: give it a radius so that it is visible.
            val point = stroke.points.first()
            stroke.points.add(DrawPoint(point.x + 0.01f, point.y, point.pressure))
        }
        val canvas = cacheCanvas
        if (canvas != null) {
            DrawingRenderer.draw(canvas, listOf(stroke))
        } else {
            rebuildCache()
        }
        strokes.add(stroke)
        undone.clear()
        invalidate()
        historyChanged()
    }

    private fun pressureOf(event: MotionEvent, index: Int): Float {
        val value = event.getPressure(index)
        // Some devices always report 1.0: a flat stroke is still better than a broken one.
        return if (value <= 0f) 0.5f else value.coerceIn(0.1f, 1f)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
