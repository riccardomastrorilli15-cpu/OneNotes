package dev.lumenotes.drawing

import org.json.JSONArray
import org.json.JSONObject

/** Points are normalized to the view's dimensions to survive screen resizing. */
data class InkPoint(val x: Float, val y: Float, val pressure: Float)
data class InkStroke(val color: Int, val widthDp: Float, val points: List<InkPoint>)

object InkDocument {
    private const val MAX_CHARACTERS = 8_000_000
    private const val MAX_STROKES = 10_000
    private const val MAX_POINTS = 200_000

    fun encode(strokes: List<InkStroke>): String {
        if (strokes.isEmpty()) return ""
        // Never persist a document that our own decoder would refuse to reopen.
        validate(strokes)
        val array = JSONArray()
        strokes.forEach { stroke ->
            val points = JSONArray()
            stroke.points.forEach { point ->
                points.put(JSONArray().put(point.x.toDouble()).put(point.y.toDouble()).put(point.pressure.toDouble()))
            }
            array.put(JSONObject().put("color", stroke.color).put("width", stroke.widthDp.toDouble()).put("points", points))
        }
        val encoded = JSONObject().put("version", 1).put("strokes", array).toString()
        require(encoded.length <= MAX_CHARACTERS) { "Drawing exceeds the size limit" }
        return encoded
    }

    private fun validate(strokes: List<InkStroke>) {
        require(strokes.size <= MAX_STROKES) { "Too many strokes" }
        var count = 0L
        strokes.forEach { stroke ->
            require(stroke.widthDp.isFinite() && stroke.widthDp in 0.5f..40f)
            require(stroke.points.isNotEmpty())
            count += stroke.points.size
            require(count <= MAX_POINTS) { "Too many points" }
            stroke.points.forEach { point ->
                require(point.x.isFinite() && point.y.isFinite() && point.pressure.isFinite())
                require(point.x in 0f..1f && point.y in 0f..1f && point.pressure in 0.1f..1.5f)
            }
        }
    }

    /** A malformed/oversized document is reported; never silently replace it with empty ink. */
    fun decode(data: String): Result<List<InkStroke>> = runCatching {
        if (data.isBlank()) return@runCatching emptyList()
        require(data.length <= MAX_CHARACTERS)
        val root = JSONObject(data)
        require(root.getInt("version") == 1)
        val array = root.getJSONArray("strokes")
        require(array.length() <= MAX_STROKES)
        var count = 0
        List(array.length()) { index ->
            val stroke = array.getJSONObject(index)
            val points = stroke.getJSONArray("points")
            require(points.length() > 0)
            count += points.length()
            require(count <= MAX_POINTS)
            val width = stroke.getDouble("width").toFloat()
            require(width.isFinite() && width in 0.5f..40f)
            InkStroke(
                stroke.getInt("color"),
                width,
                List(points.length()) { pointIndex ->
                    val point = points.getJSONArray(pointIndex)
                    val x = point.getDouble(0).toFloat()
                    val y = point.getDouble(1).toFloat()
                    val pressure = point.getDouble(2).toFloat()
                    require(x.isFinite() && y.isFinite() && pressure.isFinite())
                    require(x in 0f..1f && y in 0f..1f && pressure in 0.1f..1.5f)
                    InkPoint(x, y, pressure)
                }
            )
        }
    }
}
