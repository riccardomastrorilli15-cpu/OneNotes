package dev.lumenotes.app

import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportTextTest {
    @Test fun acceptsValidUtf8BomAndExactlyTheByteLimit() = runBlocking {
        val bytes = "\uFEFFCaffè 🦋".toByteArray(Charsets.UTF_8)
        assertEquals("Caffè 🦋", readTextDocument(bytes.inputStream(), bytes.size))
        assertEquals("", readTextDocument(byteArrayOf().inputStream(), 0))
    }

    @Test fun rejectsOversizedOrMalformedTextInsteadOfReplacingCharacters() = runBlocking {
        assertNotNull(runCatching { readTextDocument("abc".byteInputStream(), 2) }.exceptionOrNull())
        val malformed = byteArrayOf(0xc3.toByte(), 0x28)
        assertNotNull(runCatching { readTextDocument(malformed.inputStream()) }.exceptionOrNull())
    }

    @Test fun rejectsAStreamThatNeverMakesProgress() = runBlocking {
        val stalled = object : InputStream() {
            override fun read(): Int = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = 0
        }
        assertNotNull(runCatching { readTextDocument(stalled) }.exceptionOrNull())
    }

    @Test fun observesCancellationBeforeReading() = runBlocking {
        val cancelled = Job().apply { cancel() }
        val error = runCatching { withContext(cancelled) { readTextDocument("text".byteInputStream()) } }.exceptionOrNull()
        assertTrue(error is CancellationException)
    }
}
