package com.lbc.breadcrumb.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FtsQueryTest {

    @Test
    fun `a single word becomes a quoted prefix term`() {
        assertEquals("\"qualcomm*\"", FtsQuery.matchExpression("qualcomm"))
    }

    @Test
    fun `multiple words become quoted prefix terms separated by space`() {
        assertEquals("\"qualcomm*\" \"intern*\"", FtsQuery.matchExpression("qualcomm intern"))
    }

    @Test
    fun `blank or null input produces null`() {
        assertNull(FtsQuery.matchExpression(null))
        assertNull(FtsQuery.matchExpression(""))
        assertNull(FtsQuery.matchExpression("   "))
    }

    @Test
    fun `punctuation only input produces null`() {
        assertNull(FtsQuery.matchExpression("\""))
        assertNull(FtsQuery.matchExpression("-"))
        assertNull(FtsQuery.matchExpression("\"--\""))
        assertNull(FtsQuery.matchExpression(":::"))
    }

    @Test
    fun `hyphens separate words`() {
        assertEquals("\"qualcomm*\" \"intern*\"", FtsQuery.matchExpression("qualcomm-intern"))
    }

    @Test
    fun `quotes are stripped and words are extracted safely`() {
        assertEquals("\"qualcomm*\" \"intern*\"", FtsQuery.matchExpression("\"qualcomm\" \"intern\""))
    }

    @Test
    fun `or operator is treated as a literal search term`() {
        assertEquals("\"qualcomm*\" \"or*\" \"intern*\"", FtsQuery.matchExpression("qualcomm OR intern"))
    }

    @Test
    fun `column filter syntax is stripped into words`() {
        assertEquals("\"title*\" \"qualcomm*\"", FtsQuery.matchExpression("title:qualcomm"))
    }

    @Test
    fun `accents and unicode letters are preserved in words`() {
        assertEquals("\"café*\"", FtsQuery.matchExpression("Café"))
    }

    @Test
    fun `digits and letters form words together`() {
        assertEquals("\"k9*\" \"unit2*\" \"2026*\"", FtsQuery.matchExpression("k9 unit2 2026"))
    }

    @Test
    fun `leading trailing and multiple consecutive whitespace are ignored`() {
        assertEquals("\"hello*\" \"world*\"", FtsQuery.matchExpression("   hello     world   "))
    }
}
