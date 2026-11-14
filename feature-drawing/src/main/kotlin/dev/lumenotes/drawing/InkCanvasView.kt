package dev.lumenotes.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** A simple vector notebook canvas. This is not a Jetpack low-latency ink renderer. */
class InkCanvasView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private val strokes = mutableListOf<InkStroke>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private var current = mutableListOf<InkPoint>()
    private var activePointer = MotionEvent.INVALID_POINTER_ID
    private var erasing = false
    private var changedDuringGesture = false

    var inkColor: Int = Color.rgb(31, 36, 52)
    var inkWidthDp: Float = 3f
    var eraserSelected: Boolean = false
    var stylusOnly: Boolean = false
    var onDocumentChanged: ((List<InkStroke>) -> Unit)? = null

    init {
        isClickable = true
    }

    fun setDocument(value: List<InkStroke>) {
        strokes.clear()
        strokes.addAll(value)
        current.clear()
        invalidate()
    }

    fun document(): List<InkStroke> = strokes.toList()

    fun undoLastStroke() {
        if (strokes.isEmpty()) return
        strokes.removeAt(strokes.lastIndex)
        invalidate()
        onDocumentChanged?.invoke(document())
    }

    fun clearDocument() {
        if (strokes.isEmpty()) return
        strokes.clear()
        invalidate()
        onDocumentChanged?.invoke(document())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { drawStroke(canvas, it) }
        if (current.isNotEmpty()) drawStroke(canvas, InkStroke(inkColor, inkWidthDp, current))
    }

    private fun drawStroke(canvas: Canvas, stroke: InkStroke) {
        if (stroke.points.isEmpty()) return
        paint.color = stroke.color
        val scale = resources.displayMetrics.density
        val first = stroke.points.first()
        if (stroke.points.size == 1) {
            paint.strokeWidth = stroke.widthDp * scale * first.pressure.coerceAtLeast(0.25f)
            canvas.drawPoint(first.x * width, first.y * height, paint)
            return
        }
        for (index in 1 until stroke.points.size) {
            val previous = stroke.points[index - 1]
            val point = stroke.points[index]
            paint.strokeWidth = stroke.widthDp * scale * ((previous.pressure + point.pressure) / 2f).coerceAtLeast(0.25f)
            canvas.drawLine(previous.x * width, previous.y * height, point.x * width, point.y * height, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || width == 0 || height == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val type = event.getToolType(0)
                val isStylus = type == MotionEvent.TOOL_TYPE_STYLUS || type == MotionEvent.TOOL_TYPE_ERASER
                if (stylusOnly && !isStylus) return false
                parent?.requestDisallowInterceptTouchEvent(true)
                activePointer = event.getPointerId(0)
                erasing = eraserSelected || type == MotionEvent.TOOL_TYPE_ERASER ||
                    (event.buttonState and MotionEvent.BUTTON_STYLUS_PRIMARY) != 0
                changedDuringGesture = false
                current.clear()
                addPointOrErase(event.getX(0), event.getY(0), event.getPressure(0))
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (index < 0) return true
                for (history in 0 until event.historySize) {
                    addPointOrErase(
                        event.getHistoricalX(index, history),
                        event.getHistoricalY(index, history),
                        event.getHistoricalPressure(index, history)
                    )
                }
                addPointOrErase(event.getX(index), event.getY(index), event.getPressure(index))
                invalidate()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) != activePointer) return true
                addPointOrErase(event.getX(event.actionIndex), event.getY(event.actionIndex), event.getPressure(event.actionIndex))
                if (!erasing && current.isNotEmpty()) {
                    strokes.add(InkStroke(inkColor, inkWidthDp, current.toList()))
                    changedDuringGesture = true
                }
                current.clear()
                activePointer = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (changedDuringGesture) onDocumentChanged?.invoke(document())
                performClick()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                current.clear()
                activePointer = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                if (changedDuringGesture) onDocumentChanged?.invoke(document())
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun addPointOrErase(x: Float, y: Float, pressure: Float) {
        if (erasing) {
            val radius = 24f * resources.displayMetrics.density
            val removed = strokes.removeAll { stroke ->
                stroke.points.any { hypot(it.x * width - x, it.y * height - y) <= radius }
            }
            changedDuringGesture = changedDuringGesture || removed
        } else {
            current.add(InkPoint((x / width).coerceIn(0f, 1f), (y / height).coerceIn(0f, 1f), pressure.coerceIn(0.1f, 1.5f)))
        }
    }
}
