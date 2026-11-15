package dev.lumenotes.app

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class JsonStringLimitReaderTest {
    @Test fun preservesEscapedQuotesNestedValuesAndSmallReadChunks() {
        val json = """{"a":"x\"y","b":["{}",{"c":"two"}]}"""
        assertEquals(json, read(json, valueLimit = 20, nameLimit = 1, chunk = 1))
    }

    @Test fun permitsExactValueAndNameLimitsButRejectsLongerTokens() {
        assertEquals("""{"abcd":"1234"}""", read("""{"abcd":"1234"}""", valueLimit = 4, nameLimit = 4))
        assertNotNull(runCatching { read("""{"abcd":"12345"}""", valueLimit = 4, nameLimit = 4) }.exceptionOrNull())
        assertNotNull(runCatching { read("""{"abcde":"ok"}""", valueLimit = 8, nameLimit = 4) }.exceptionOrNull())
    }

    @Test fun countsEscapesAsRawCharactersAcrossReadBoundaries() {
        val json = """{"a":"\u1234"}"""
        assertEquals(json, read(json, valueLimit = 6, nameLimit = 1, chunk = 1))
        assertNotNull(runCatching { read(json, valueLimit = 5, nameLimit = 1, chunk = 1) }.exceptionOrNull())
    }

    @Test fun rejectsExcessiveOrMismatchedNesting() {
        assertNotNull(runCatching { read("[[[]]]", depthLimit = 2) }.exceptionOrNull())
        assertNotNull(runCatching { read("{]") }.exceptionOrNull())
        assertNotNull(runCatching { read("}") }.exceptionOrNull())
    }

    private fun read(value: String, valueLimit: Int = 20, nameLimit: Int = 20, depthLimit: Int = 16, chunk: Int = 4): String =
        JsonStringLimitReader(StringReader(value), valueLimit, nameLimit, depthLimit).use { source ->
            val output = StringBuilder()
            val buffer = CharArray(chunk)
            while (true) {
                val count = source.read(buffer)
                if (count < 0) break
                output.append(buffer, 0, count)
            }
            output.toString()
        }
}
