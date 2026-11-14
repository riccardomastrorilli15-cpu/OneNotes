package com.onenotes.app.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Type of a block of a note. The editor keeps one block per paragraph / list item /
 * attachment, very much like Samsung Notes does.
 */
enum class BlockType { TEXT, HEADING1, HEADING2, HEADING3, QUOTE, BULLET, NUMBER, CHECK }

/** Inline formatting that can be applied to a portion of the text of a block. */
enum class SpanKind {
    BOLD, ITALIC, UNDERLINE, STRIKETHROUGH, COLOR, HIGHLIGHT, LINK;

    companion object {
        fun fromCode(code: String): SpanKind? = entries.firstOrNull { it.code == code }
    }
}

val SpanKind.code: String
    get() = when (this) {
        SpanKind.BOLD -> "b"
        SpanKind.ITALIC -> "i"
        SpanKind.UNDERLINE -> "u"
        SpanKind.STRIKETHROUGH -> "s"
        SpanKind.COLOR -> "c"
        SpanKind.HIGHLIGHT -> "h"
        SpanKind.LINK -> "l"
    }

/**
 * An inline format applied between [start] (inclusive) and [end] (exclusive).
 * [value] carries the extra payload: the "#AARRGGBB" colour for [SpanKind.COLOR] and
 * [SpanKind.HIGHLIGHT], the url for [SpanKind.LINK].
 */
data class InlineSpan(
    var start: Int,
    var end: Int,
    var kind: SpanKind,
    var value: String? = null
)

/** A piece of a note: a paragraph, a list item, an image, a recording, a drawing or a file. */
sealed class Block {
    abstract val id: String

    fun copyWithId(id: String = this.id): Block = when (this) {
        is TextBlock -> copy(id = id)
        is ImageBlock -> copy(id = id)
        is AudioBlock -> copy(id = id)
        is DrawingBlock -> copy(id = id)
        is FileBlock -> copy(id = id)
    }
}

data class TextBlock(
    override val id: String = newBlockId(),
    var blockType: BlockType = BlockType.TEXT,
    var text: String = "",
    var spans: MutableList<InlineSpan> = mutableListOf(),
    var checked: Boolean = false
) : Block()

data class ImageBlock(
    override val id: String = newBlockId(),
    var fileName: String = "",
    var name: String = "",
    var width: Int = 0,
    var height: Int = 0
) : Block()

data class AudioBlock(
    override val id: String = newBlockId(),
    var fileName: String = "",
    var name: String = "",
    var durationMs: Long = 0L
) : Block()

data class DrawingBlock(
    override val id: String = newBlockId(),
    var fileName: String = "",
    var height: Int = 0
) : Block()

data class FileBlock(
    override val id: String = newBlockId(),
    var fileName: String = "",
    var name: String = "",
    var size: Long = 0L,
    var mime: String = ""
) : Block()

fun newBlockId(): String = UUID.randomUUID().toString()

/**
 * Serialises / parses the body of a note.
 *
 * The format is a plain JSON array of blocks; it is intentionally verbose so that a backup
 * written by a future version of the app can still be read, and unknown block types are
 * skipped instead of failing the whole note.
 */
object NoteBody {

    private const val TYPE_TEXT = "text"
    private const val TYPE_IMAGE = "image"
    private const val TYPE_AUDIO = "audio"
    private const val TYPE_DRAWING = "drawing"
    private const val TYPE_FILE = "file"

    val emptyJson: String get() = JSONArray().apply {
        put(TextBlock().toJson())
    }.toString()

    fun toJson(blocks: List<Block>): String {
        val array = JSONArray()
        blocks.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun fromJson(raw: String?): MutableList<Block> {
        val result = mutableListOf<Block>()
        if (raw.isNullOrBlank()) {
            result.add(TextBlock())
            return result
        }
        val array = try {
            JSONArray(raw)
        } catch (t: Throwable) {
            JSONArray()
        }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            parseBlock(obj)?.let { result.add(it) }
        }
        if (result.isEmpty()) result.add(TextBlock())
        return result
    }

    /** Flattened text of the note, used for the search index and the previews. */
    fun toPlainText(blocks: List<Block>): String {
        val builder = StringBuilder()
        blocks.forEach { block ->
            when (block) {
                is TextBlock -> {
                    if (block.text.isNotBlank()) {
                        if (builder.isNotEmpty()) builder.append('\n')
                        builder.append(block.text)
                    }
                }
                is ImageBlock -> appendToken(builder, block.name.ifBlank { "Immagine" })
                is AudioBlock -> appendToken(builder, block.name.ifBlank { "Registrazione" })
                is DrawingBlock -> appendToken(builder, "Disegno")
                is FileBlock -> appendToken(builder, block.name.ifBlank { "Allegato" })
            }
        }
        return builder.toString().trim()
    }

    private fun appendToken(builder: StringBuilder, token: String) {
        if (builder.isNotEmpty()) builder.append('\n')
        builder.append(token)
    }

    /** Names of the files stored in the private storage and referenced by these blocks. */
    fun attachmentFiles(blocks: List<Block>): List<String> = blocks.mapNotNull { block ->
        when (block) {
            is ImageBlock -> block.fileName
            is AudioBlock -> block.fileName
            is DrawingBlock -> block.fileName
            is FileBlock -> block.fileName
            is TextBlock -> null
        }
    }.filter { it.isNotBlank() }

    // ------------------------------------------------------------------ JSON

    private fun Block.toJson(): JSONObject = when (this) {
        is TextBlock -> JSONObject().apply {
            put("id", id)
            put("type", TYPE_TEXT)
            put("style", blockType.name)
            put("text", text)
            put("checked", checked)
            put("spans", JSONArray().apply {
                spans.forEach { span ->
                    put(JSONArray().apply {
                        put(span.start)
                        put(span.end)
                        put(span.kind.code)
                        if (span.value != null) put(span.value)
                    })
                }
            })
        }
        is ImageBlock -> JSONObject().apply {
            put("id", id)
            put("type", TYPE_IMAGE)
            put("file", fileName)
            put("name", name)
            put("w", width)
            put("h", height)
        }
        is AudioBlock -> JSONObject().apply {
            put("id", id)
            put("type", TYPE_AUDIO)
            put("file", fileName)
            put("name", name)
            put("dur", durationMs)
        }
        is DrawingBlock -> JSONObject().apply {
            put("id", id)
            put("type", TYPE_DRAWING)
            put("file", fileName)
            put("h", height)
        }
        is FileBlock -> JSONObject().apply {
            put("id", id)
            put("type", TYPE_FILE)
            put("file", fileName)
            put("name", name)
            put("size", size)
            put("mime", mime)
        }
    }

    private fun parseBlock(obj: JSONObject): Block? {
        return when (obj.optString("type")) {
            TYPE_TEXT -> {
                val style = runCatching { BlockType.valueOf(obj.optString("style", BlockType.TEXT.name)) }
                    .getOrDefault(BlockType.TEXT)
                val spans = mutableListOf<InlineSpan>()
                val spansArray = obj.optJSONArray("spans")
                if (spansArray != null) {
                    for (i in 0 until spansArray.length()) {
                        val item = spansArray.optJSONArray(i) ?: continue
                        val kind = SpanKind.fromCode(item.optString(2)) ?: continue
                        spans.add(
                            InlineSpan(
                                start = item.optInt(0, 0),
                                end = item.optInt(1, 0),
                                kind = kind,
                                value = item.optString(3).takeIf { it.isNotBlank() }
                            )
                        )
                    }
                }
                TextBlock(
                    id = obj.optString("id").ifBlank { newBlockId() },
                    blockType = style,
                    text = obj.optString("text", ""),
                    spans = spans,
                    checked = obj.optBoolean("checked", false)
                )
            }
            TYPE_IMAGE -> ImageBlock(
                id = obj.optString("id").ifBlank { newBlockId() },
                fileName = obj.optString("file"),
                name = obj.optString("name"),
                width = obj.optInt("w", 0),
                height = obj.optInt("h", 0)
            )
            TYPE_AUDIO -> AudioBlock(
                id = obj.optString("id").ifBlank { newBlockId() },
                fileName = obj.optString("file"),
                name = obj.optString("name"),
                durationMs = obj.optLong("dur", 0L)
            )
            TYPE_DRAWING -> DrawingBlock(
                id = obj.optString("id").ifBlank { newBlockId() },
                fileName = obj.optString("file"),
                height = obj.optInt("h", 0)
            )
            TYPE_FILE -> FileBlock(
                id = obj.optString("id").ifBlank { newBlockId() },
                fileName = obj.optString("file"),
                name = obj.optString("name"),
                size = obj.optLong("size", 0L),
                mime = obj.optString("mime")
            )
            else -> null
        }
    }

    // -------------------------------------------------------------- Markdown

    fun toMarkdown(title: String, blocks: List<Block>): String {
        val out = StringBuilder()
        if (title.isNotBlank()) {
            out.append("# ").append(title).append("\n\n")
        }
        var numberIndex = 0
        blocks.forEach { block ->
            when (block) {
                is TextBlock -> {
                    when (block.blockType) {
                        BlockType.HEADING1 -> out.append("# ").append(inline(block)).append('\n').append('\n')
                        BlockType.HEADING2 -> out.append("## ").append(inline(block)).append('\n').append('\n')
                        BlockType.HEADING3 -> out.append("### ").append(inline(block)).append('\n').append('\n')
                        BlockType.QUOTE -> out.append("> ").append(inline(block)).append('\n').append('\n')
                        BlockType.BULLET -> out.append("- ").append(inline(block)).append('\n')
                        BlockType.NUMBER -> {
                            numberIndex++
                            out.append(numberIndex).append(". ").append(inline(block)).append('\n')
                        }
                        BlockType.CHECK -> out.append(if (block.checked) "- [x] " else "- [ ] ")
                            .append(inline(block)).append('\n')
                        BlockType.TEXT -> {
                            numberIndex = 0
                            out.append(inline(block)).append('\n').append('\n')
                        }
                    }
                }
                is ImageBlock -> out.append("![")
                    .append(block.name.ifBlank { "image" })
                    .append("](").append(block.fileName).append(")\n\n")
                is AudioBlock -> out.append("🔊 ")
                    .append(block.name.ifBlank { "audio" })
                    .append(" (").append(block.fileName).append(")\n\n")
                is DrawingBlock -> out.append("[drawing: ").append(block.fileName).append("]\n\n")
                is FileBlock -> out.append("[")
                    .append(block.name.ifBlank { "file" })
                    .append("](").append(block.fileName).append(")\n\n")
            }
        }
        return out.toString().trim() + "\n"
    }

    private fun inline(block: TextBlock): String = applyInline(block.text, block.spans)

    /** Renders the inline formatting of a block as Markdown markers. */
    fun applyInline(text: String, spans: List<InlineSpan>): String {
        if (text.isEmpty() || spans.isEmpty()) return text
        val opens = HashMap<Int, MutableList<String>>()
        val closes = HashMap<Int, MutableList<String>>()
        for (span in spans) {
            val start = span.start.coerceIn(0, text.length)
            val end = span.end.coerceIn(start, text.length)
            if (start == end) continue
            val (open, close) = when (span.kind) {
                SpanKind.BOLD -> "**" to "**"
                SpanKind.ITALIC -> "*" to "*"
                SpanKind.UNDERLINE -> "<u>" to "</u>"
                SpanKind.STRIKETHROUGH -> "~~" to "~~"
                SpanKind.COLOR -> "<span>" to "</span>"
                SpanKind.HIGHLIGHT -> "==" to "=="
                SpanKind.LINK -> "[" to "](${span.value.orEmpty()})"
            }
            opens.getOrPut(start) { mutableListOf() }.add(open)
            closes.getOrPut(end) { mutableListOf() }.add(0, close)
        }
        val out = StringBuilder()
        for (index in 0..text.length) {
            closes[index]?.forEach { out.append(it) }
            opens[index]?.forEach { out.append(it) }
            if (index < text.length) out.append(text[index])
        }
        return out.toString()
    }
}
