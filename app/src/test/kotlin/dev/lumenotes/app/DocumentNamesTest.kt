package dev.lumenotes.app

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentNamesTest {
    @Test fun importKeepsNamesWithoutExtensions() {
        assertEquals("Meeting notes", importTitle("Meeting notes"))
        assertEquals(".hidden", importTitle(".hidden"))
        assertEquals("Notes.v2", importTitle("Notes.v2.md"))
        assertEquals("x".repeat(120), importTitle("x".repeat(200)))
    }
}
