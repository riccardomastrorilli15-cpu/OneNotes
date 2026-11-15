package dev.lumenotes.drawing

import kotlin.math.abs

/** Squared distances in canvas pixels; also handles single-point and crossing segments. */
internal object InkGeometry {
    fun pointToSegmentSquared(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val t = if (lengthSquared == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0f, 1f)
        val offsetX = px - (ax + t * dx)
        val offsetY = py - (ay + t * dy)
        return offsetX * offsetX + offsetY * offsetY
    }

    fun segmentsSquared(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Float {
        val abX = bx - ax
        val abY = by - ay
        val cdX = dx - cx
        val cdY = dy - cy
        val determinant = abX * cdY - abY * cdX
        if (abs(determinant) > 0.000001f) {
            val acX = cx - ax
            val acY = cy - ay
            val t = (acX * cdY - acY * cdX) / determinant
            val u = (acX * abY - acY * abX) / determinant
            if (t in 0f..1f && u in 0f..1f) return 0f
        }
        return minOf(
            pointToSegmentSquared(ax, ay, cx, cy, dx, dy),
            pointToSegmentSquared(bx, by, cx, cy, dx, dy),
            pointToSegmentSquared(cx, cy, ax, ay, bx, by),
            pointToSegmentSquared(dx, dy, ax, ay, bx, by)
        )
    }
}
