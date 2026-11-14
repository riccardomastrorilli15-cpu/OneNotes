package dev.lumenotes.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEditingTest {
    @Test fun wrapsSelectionAndKeepsItSelected() {
        assertEquals(EditResult("a **bc** d", 4, 6), MarkdownEditing.wrap("a bc d", 2, 4, "**"))
    }

    @Test fun addsPrefixAtLineStart() {
        assertEquals("first\n- second", MarkdownEditing.prefixLine("first\nsecond", 8, 8, "- ").text)
    }

    @Test fun handlesReversedAndInvalidSelection() {
        assertEquals("**abc**", MarkdownEditing.wrap("abc", 9, -1, "**").text)
    }

    @Test fun undoAndRedoDoNotMixBranches() {
        val history = TextHistory(2)
        history.record("", "a")
        history.record("a", "ab")
        assertEquals("a", history.undo("ab"))
        assertEquals("ab", history.redo("a"))
        assertEquals("a", history.undo("ab"))
        history.record("a", "ac")
        assertEquals(null, history.redo("ac"))
    }

    @Test fun previewEscapesUntrustedHtml() {
        val html = MarkdownPreview.toSafeHtml("# Title\n<script>alert('x')</script>\n**bold**")
        assertTrue(html.contains("&lt;script&gt;"))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("<b>bold</b>"))
    }
}
