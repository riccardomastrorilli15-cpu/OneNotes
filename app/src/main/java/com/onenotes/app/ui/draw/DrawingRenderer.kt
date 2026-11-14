package com.onenotes.app.ui.draw

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.abs

/**
 * Draws the strokes of a note both on the screen ([DrawingView]) and off screen, when a drawing
 * has to be exported to an image or to a PDF page.
 */
object DrawingRenderer {

    /**
     * Renders [strokes] into a new bitmap of the requested size.
     *
     * @param background the colour of the paper (the drawing canvas is transparent on screen)
     */
    fun render(
        strokes: List<Stroke>,
        width: Int,
        height: Int,
        background: Int = Color.TRANSPARENT
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (background != Color.TRANSPARENT) canvas.drawColor(background)
        draw(canvas, strokes)
        return bitmap
    }

    fun draw(canvas: Canvas, strokes: List<Stroke>) {
        // The highlighters are painted first, so that the ink stays readable on top of them.
        strokes.filter { it.highlighter && !it.eraser }.forEach { canvas.drawStroke(it) }
        strokes.filter { it.eraser }.forEach { canvas.drawStroke(it) }
        strokes.filter { !it.highlighter && !it.eraser }.forEach { canvas.drawStroke(it) }
    }

    private fun Canvas.drawStroke(stroke: Stroke) {
        val points = stroke.points
        if (points.isEmpty()) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = stroke.color
            alpha = if (stroke.highlighter) (stroke.alpha * 0.45f).toInt() else stroke.alpha
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            if (stroke.eraser) {
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                alpha = 255
            }
        }

        if (points.size == 1) {
            val point = points.first()
            paint.strokeWidth = stroke.baseWidth * widthFor(point.pressure)
            drawPoint(point.x, point.y, paint)
            return
        }

        val path = Path()
        var previous = points.first()
        path.moveTo(previous.x, previous.y)
        for (index in 1 until points.size) {
            val current = points[index]
            // Quadratic smoothing: it removes the "polygon" look of fast strokes.
            val midX = (previous.x + current.x) / 2f
            val midY = (previous.y + current.y) / 2f
            path.quadTo(previous.x, previous.y, midX, midY)
            previous = current
        }
        path.lineTo(previous.x, previous.y)

        val averagePressure = points.map { it.pressure }.average().toFloat()
        paint.strokeWidth = stroke.baseWidth * widthFor(averagePressure).coerceIn(0.35f, 2.2f)
        drawPath(path, paint)
    }

    /** The pressure of the pen modulates the thickness of the stroke. */
    fun widthFor(pressure: Float): Float {
        val value = if (pressure <= 0f) 0.6f else pressure.coerceIn(0.15f, 1f)
        return 0.55f + value * 0.75f
    }

    /** Distance between two points, used to drop the points that are too close. */
    fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float =
        abs(x1 - x2) + abs(y1 - y2)
}
