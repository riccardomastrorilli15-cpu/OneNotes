package dev.lumenotes.editor

/** A deliberately small, HTML-escaped Markdown preview, not a full CommonMark parser. */
object MarkdownPreview {
    fun toSafeHtml(markdown: String): String = markdown.lineSequence().joinToString("<br>") { raw ->
        val escaped = escape(raw)
        when {
            escaped.startsWith("### ") -> "<b>${inline(escaped.drop(4))}</b>"
            escaped.startsWith("## ") -> "<big><b>${inline(escaped.drop(3))}</b></big>"
            escaped.startsWith("# ") -> "<big><b>${inline(escaped.drop(2))}</b></big>"
            escaped.startsWith("- [ ] ") -> "☐ ${inline(escaped.drop(6))}"
            escaped.startsWith("- [x] ", ignoreCase = true) -> "☑ ${inline(escaped.drop(6))}"
            escaped.startsWith("- ") -> "• ${inline(escaped.drop(2))}"
            escaped.startsWith("&gt; ") -> "<i>${inline(escaped.drop(5))}</i>"
            else -> inline(escaped)
        }
    }

    private fun inline(value: String): String = value
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>")
        .replace(Regex("(?<!\\*)\\*(?!\\*)(.+?)(?<!\\*)\\*(?!\\*)"), "<i>$1</i>")
        .replace(Regex("~~(.+?)~~"), "<strike>$1</strike>")
        .replace(Regex("`([^`]+)`"), "<tt>$1</tt>")

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
