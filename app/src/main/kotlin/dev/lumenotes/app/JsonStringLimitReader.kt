package dev.lumenotes.app

import java.io.Reader
import java.util.ArrayDeque

/** Bound names, raw string tokens and nesting before Android's JsonReader allocates them. */
internal class JsonStringLimitReader(
    private val source: Reader,
    private val valueLimit: Int,
    private val nameLimit: Int,
    private val depthLimit: Int = 16
) : Reader() {
    private data class Frame(val type: Char, var expectingName: Boolean)

    private val stack = ArrayDeque<Frame>()
    private var inside = false
    private var escaped = false
    private var size = 0
    private var tokenLimit = valueLimit

    init {
        require(valueLimit >= 0 && nameLimit >= 0 && depthLimit > 0)
    }

    override fun read(buffer: CharArray, offset: Int, length: Int): Int {
        val read = source.read(buffer, offset, length)
        for (index in offset until offset + maxOf(0, read)) {
            val char = buffer[index]
            if (inside) {
                if (!escaped && char == '"') {
                    inside = false
                } else {
                    require(++size <= tokenLimit) { "JSON string is too large" }
                    escaped = !escaped && char == '\\'
                }
            } else {
                when (char) {
                    '"' -> {
                        inside = true
                        size = 0
                        val frame = stack.peekLast()
                        tokenLimit = if (frame?.type == '{' && frame.expectingName) nameLimit else valueLimit
                    }

                    '{', '[' -> {
                        require(stack.size < depthLimit) { "JSON nesting is too deep" }
                        stack.addLast(Frame(char, char == '{'))
                    }

                    '}', ']' -> {
                        val expected = if (char == '}') '{' else '['
                        require(stack.peekLast()?.type == expected) { "Invalid JSON nesting" }
                        stack.removeLast()
                    }

                    ':' -> stack.peekLast()?.expectingName = false

                    ',' -> stack.peekLast()?.let { if (it.type == '{') it.expectingName = true }
                }
            }
        }
        return read
    }

    override fun close() = source.close()
}
