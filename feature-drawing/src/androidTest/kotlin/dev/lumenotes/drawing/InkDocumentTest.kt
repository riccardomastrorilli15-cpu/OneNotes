package dev.lumenotes.drawing

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InkDocumentTest {
    private val point = InkPoint(0.25f, 0.75f, 1.0f)
    private val stroke = InkStroke(0xff123456.toInt(), 3f, listOf(point))

    @Test fun roundTripAndEmptyDocument() {
        assertEquals("", InkDocument.encode(emptyList()))
        assertEquals(emptyList<InkStroke>(), InkDocument.decode("").getOrThrow())
        assertEquals(listOf(stroke), InkDocument.decode(InkDocument.encode(listOf(stroke))).getOrThrow())
    }

    @Test fun invalidDocumentsAreNeverEncodedOrSilentlyCleared() {
        assertTrue(InkDocument.decode("{broken").isFailure)
        assertTrue(InkDocument.decode("{\"version\":99,\"strokes\":[]}").isFailure)
        assertFalse(runCatching { InkDocument.encode(listOf(stroke.copy(widthDp = Float.NaN))) }.isSuccess)
        assertFalse(runCatching { InkDocument.encode(listOf(stroke.copy(points = listOf(point.copy(x = 2f))))) }.isSuccess)
        assertFalse(runCatching { InkDocument.encode(listOf(stroke.copy(points = emptyList()))) }.isSuccess)
    }

    @Test fun oversizedDocumentsCannotBeSaved() {
        assertTrue(runCatching { InkDocument.encode(List(10_001) { stroke }) }.isFailure)
        assertTrue(runCatching { InkDocument.encode(listOf(stroke.copy(points = List(200_001) { point }))) }.isFailure)
        assertTrue(InkDocument.decode("x".repeat(8_000_001)).isFailure)
    }
}
