package com.onenotes.app.ui.draw

import org.json.JSONArray
import org.json.JSONObject

/** A point of a stroke: x, y and the pressure reported by the S Pen. */
data class DrawPoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 0.5f
)

/**
 * A single stroke of the drawing canvas.
 *
 * @param color the colour of the stroke
 * @param baseWidth the thickness at full pressure (the pressure scales it)
 * @param alpha 0-255
 * @param eraser stroke drawn with the transparent blending (or with the S Pen button)
 * @param highlighter wide, translucent stroke drawn under the others
 */
data class Stroke(
    var color: Int = 0xFF1B1D1F.toInt(),
    var baseWidth: Float = 6f,
    var alpha: Int = 255,
    var eraser: Boolean = false,
    var highlighter: Boolean = false,
    val points: MutableList<DrawPoint> = mutableListOf()
)

/**
 * JSON persistence of a drawing. The format is intentionally compact because a drawing can
 * contain thousands of points: each stroke is an object and the points are a flat
 * [x, y, pressure] array.
 */
object StrokeSerializer {

    fun toJson(strokes: List<Stroke>): String {
        val array = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { point ->
                points.put(round(point.x))
                points.put(round(point.y))
                points.put(round(point.pressure))
            }
            array.put(
                JSONObject().apply {
                    put("c", stroke.color)
                    put("w", stroke.baseWidth.toDouble())
                    put("a", stroke.alpha)
                    put("e", if (stroke.eraser) 1 else 0)
                    put("h", if (stroke.highlighter) 1 else 0)
                    put("p", points)
                }
            )
        }
        return JSONObject().apply {
            put("version", 1)
            put("strokes", array)
        }.toString()
    }

    fun fromJson(raw: String): MutableList<Stroke> {
        val result = mutableListOf<Stroke>()
        if (raw.isBlank()) return result
        val root = try {
            JSONObject(raw)
        } catch (t: Throwable) {
            return result
        }
        val array = root.optJSONArray("strokes") ?: return result
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val stroke = Stroke(
                color = obj.optInt("c", 0xFF1B1D1F.toInt()),
                baseWidth = obj.optDouble("w", 6.0).toFloat(),
                alpha = obj.optInt("a", 255),
                eraser = obj.optInt("e", 0) == 1,
                highlighter = obj.optInt("h", 0) == 1
            )
            val points = obj.optJSONArray("p") ?: continue
            var index = 0
            while (index + 2 < points.length()) {
                stroke.points.add(
                    DrawPoint(
                        x = points.optDouble(index).toFloat(),
                        y = points.optDouble(index + 1).toFloat(),
                        pressure = points.optDouble(index + 2, 0.5).toFloat()
                    )
                )
                index += 3
            }
            if (stroke.points.isNotEmpty()) result.add(stroke)
        }
        return result
    }

    private fun round(value: Float): Double {
        val scaled = (value * 10).toInt() / 10.0
        return scaled
    }
}
