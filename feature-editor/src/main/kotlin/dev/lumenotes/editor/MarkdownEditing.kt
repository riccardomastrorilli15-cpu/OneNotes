package dev.lumenotes.editor

/** Text and selection after an editor command; indices are UTF-16 offsets, as in EditText. */
data class EditResult(val text: String, val selectionStart: Int, val selectionEnd: Int)

object MarkdownEditing {
    fun wrap(text: String, start: Int, end: Int, marker: String): EditResult {
        val (a, b) = normalizedSelection(text, start, end)
        val selected = text.substring(a, b)
        val result = text.substring(0, a) + marker + selected + marker + text.substring(b)
        return EditResult(result, a + marker.length, b + marker.length)
    }

    fun prefixLine(text: String, start: Int, end: Int, prefix: String): EditResult {
        val (a, b) = normalizedSelection(text, start, end)
        val lineStart = if (a == 0) 0 else text.lastIndexOf('\n', a - 1) + 1
        val result = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        return EditResult(result, a + prefix.length, b + prefix.length)
    }

    fun insert(text: String, start: Int, end: Int, content: String): EditResult {
        val (a, b) = normalizedSelection(text, start, end)
        val result = text.substring(0, a) + content + text.substring(b)
        val caret = a + content.length
        return EditResult(result, caret, caret)
    }

    private fun normalizedSelection(text: String, start: Int, end: Int): Pair<Int, Int> {
        val a = start.coerceIn(0, text.length)
        val b = end.coerceIn(0, text.length)
        return minOf(a, b) to maxOf(a, b)
    }
}

/** Bounded snapshots prevent an editing session from growing without limit. */
class TextHistory(private val capacity: Int = 100) {
    private val undo = ArrayDeque<String>()
    private val redo = ArrayDeque<String>()

    init {
        require(capacity > 0)
    }

    fun record(previous: String, current: String) {
        if (previous == current) return
        undo.addLast(previous)
        while (undo.size > capacity) undo.removeFirst()
        redo.clear()
    }

    fun undo(current: String): String? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(current)
        return previous
    }

    fun redo(current: String): String? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(current)
        return next
    }
}
