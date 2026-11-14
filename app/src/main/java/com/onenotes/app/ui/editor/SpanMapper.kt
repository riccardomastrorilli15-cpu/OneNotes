package com.onenotes.app.ui.editor

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.CharacterStyle
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.URLSpan
import com.onenotes.app.data.model.InlineSpan
import com.onenotes.app.data.model.SpanKind

/**
 * Bridge between the inline formatting stored in the note ([InlineSpan]) and the Android spans
 * used by the EditText of every block.
 */
object SpanMapper {

    /** Rebuilds the [Spannable] of a block from its text and its spans. */
    fun build(text: String, spans: List<InlineSpan>): Spannable {
        val editable = Editable.Factory.getInstance().newEditable(text) as Spannable
        spans.forEach { span -> apply(editable, span) }
        return editable
    }

    /** Applies a single logical span; out of range values are clamped. */
    fun apply(editable: Spannable, span: InlineSpan) {
        val start = span.start.coerceIn(0, editable.length)
        val end = span.end.coerceIn(start, editable.length)
        if (start == end) return
        editable.setSpan(
            toAndroid(span.kind, span.value),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun toAndroid(kind: SpanKind, value: String?): Any = when (kind) {
        SpanKind.BOLD -> StyleSpan(android.graphics.Typeface.BOLD)
        SpanKind.ITALIC -> StyleSpan(android.graphics.Typeface.ITALIC)
        SpanKind.UNDERLINE -> UnderlineSpan()
        SpanKind.STRIKETHROUGH -> StrikethroughSpan()
        SpanKind.COLOR -> ForegroundColorSpan(parseColor(value, Color.BLACK))
        SpanKind.HIGHLIGHT -> BackgroundColorSpan(parseColor(value, Color.YELLOW))
        SpanKind.LINK -> URLSpan(value.orEmpty())
    }

    /** Adds the format to [start]-[end], or removes it when it is already there. */
    fun toggle(editable: Editable, start: Int, end: Int, kind: SpanKind, value: String?) {
        if (start >= end) return
        val existing = editable.getSpans(start, end, spanClass(kind))
        val fullyCovered = existing.any {
            editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end
        }
        remove(editable, start, end, kind)
        if (!fullyCovered) {
            apply(editable, InlineSpan(start, end, kind, value))
        }
    }

    /** Removes every span of [kind] inside the range, keeping the parts outside of it. */
    fun remove(editable: Editable, start: Int, end: Int, kind: SpanKind) {
        val spans = editable.getSpans(start, end, spanClass(kind))
        spans.forEach { span ->
            val spanStart = editable.getSpanStart(span)
            val spanEnd = editable.getSpanEnd(span)
            val value = valueOf(editable, kind, span)
            editable.removeSpan(span)
            if (spanStart < start) apply(editable, InlineSpan(spanStart, start, kind, value))
            if (spanEnd > end) apply(editable, InlineSpan(end, spanEnd, kind, value))
        }
    }

    /** True when the whole selection already carries the format. */
    fun hasKind(editable: Spanned, start: Int, end: Int, kind: SpanKind): Boolean {
        if (start >= end) return false
        return editable.getSpans(start, end, spanClass(kind)).any {
            editable.getSpanStart(it) <= start && editable.getSpanEnd(it) >= end
        }
    }

    /** Reads back the logical spans of an editable (used before saving a block). */
    fun extract(spanned: Spanned): List<InlineSpan> {
        val result = ArrayList<InlineSpan>()
        val spans = spanned.getSpans(0, spanned.length, Any::class.java)
        spans.forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)
            if (end <= start) return@forEach
            when (span) {
                is StyleSpan -> {
                    when (span.style) {
                        android.graphics.Typeface.BOLD ->
                            result += InlineSpan(start, end, SpanKind.BOLD)
                        android.graphics.Typeface.ITALIC ->
                            result += InlineSpan(start, end, SpanKind.ITALIC)
                        else -> Unit
                    }
                }
                is UnderlineSpan -> result += InlineSpan(start, end, SpanKind.UNDERLINE)
                is StrikethroughSpan -> result += InlineSpan(start, end, SpanKind.STRIKETHROUGH)
                is ForegroundColorSpan ->
                    result += InlineSpan(start, end, SpanKind.COLOR, colorToHex(span.foregroundColor))
                is BackgroundColorSpan ->
                    result += InlineSpan(start, end, SpanKind.HIGHLIGHT, colorToHex(span.backgroundColor))
                is URLSpan -> result += InlineSpan(start, end, SpanKind.LINK, span.url)
            }
        }
        return result.sortedWith(compareBy({ it.start }, { it.end }))
    }

    private fun spanClass(kind: SpanKind): Class<out CharacterStyle> = when (kind) {
        SpanKind.BOLD, SpanKind.ITALIC -> StyleSpan::class.java
        SpanKind.UNDERLINE -> UnderlineSpan::class.java
        SpanKind.STRIKETHROUGH -> StrikethroughSpan::class.java
        SpanKind.COLOR -> ForegroundColorSpan::class.java
        SpanKind.HIGHLIGHT -> BackgroundColorSpan::class.java
        SpanKind.LINK -> URLSpan::class.java
    }

    private fun valueOf(spanned: Spanned, kind: SpanKind, span: Any): String? = when (kind) {
        SpanKind.COLOR -> (span as? ForegroundColorSpan)?.foregroundColor?.let { colorToHex(it) }
        SpanKind.HIGHLIGHT -> (span as? BackgroundColorSpan)?.backgroundColor?.let { colorToHex(it) }
        SpanKind.LINK -> (span as? URLSpan)?.url
        else -> null
    }

    private fun parseColor(value: String?, fallback: Int): Int = try {
        Color.parseColor(value ?: throw IllegalArgumentException())
    } catch (t: Throwable) {
        fallback
    }

    fun colorToHex(color: Int): String =
        String.format("#%08X", color)
}
