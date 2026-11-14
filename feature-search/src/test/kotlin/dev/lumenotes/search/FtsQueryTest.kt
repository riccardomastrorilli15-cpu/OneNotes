package dev.lumenotes.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {
    @Test fun prefixesAndUnicodeAreLiteral() {
        assertEquals("\"caffè\"* AND \"studio\"*", FtsQuery.fromUserInput("caffè studio"))
    }

    @Test fun operatorsAndQuotesAreNotExecuted() {
        assertEquals("\"uno\"* AND \"OR\"* AND \"due\"*", FtsQuery.fromUserInput("uno\" OR (due)"))
        assertNull(FtsQuery.fromUserInput("** ( ) \""))
    }

    @Test fun boundsQuerySize() {
        val input = (1..20).joinToString(" ") { "x".repeat(100) }
        val terms = FtsQuery.fromUserInput(input)!!.split(" AND ")
        assertEquals(12, terms.size)
        assertEquals(64, terms.first().removePrefix("\"").removeSuffix("\"*").length)
    }
}
