package com.lbc.breadcrumb.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkParserTest {

    @Test
    fun `parses netscape html export into one memory per bookmark`() {
        val html = """
            <!DOCTYPE NETSCAPE-Bookmark-file-1>
            <TITLE>Bookmarks</TITLE>
            <H1>Bookmarks</H1>
            <DL><p>
                <DT><H3 ADD_DATE="1609459200">Work</H3>
                <DL><p>
                    <DT><A HREF="https://qualcomm.com/internships" ADD_DATE="1711929600">Qualcomm SWE Internships</A>
                    <DT><A HREF="https://kotlinlang.org" ADD_DATE="1714521600">Kotlin Programming Language</A>
                </DL><p>
            </DL><p>
        """.trimIndent()

        val bookmarks = BookmarkParser.parse(html)
        assertEquals(2, bookmarks.size)

        val first = bookmarks[0]
        assertEquals("https://qualcomm.com/internships", first.url)
        assertEquals("Qualcomm SWE Internships", first.title)
        assertEquals(1711929600000L, first.addDateMillis)

        val second = bookmarks[1]
        assertEquals("https://kotlinlang.org", second.url)
        assertEquals("Kotlin Programming Language", second.title)
        assertEquals(1714521600000L, second.addDateMillis)
    }

    @Test
    fun `converts add date from epoch seconds to milliseconds`() {
        val html = """<DT><A HREF="https://example.com" ADD_DATE="1600000000">Example</A>"""
        val bookmarks = BookmarkParser.parse(html)

        assertEquals(1, bookmarks.size)
        assertEquals(1600000000000L, bookmarks[0].addDateMillis)
    }

    @Test
    fun `handles missing or zero add date gracefully`() {
        val html = """<DT><A HREF="https://example.com">No Date</A>"""
        val bookmarks = BookmarkParser.parse(html)

        assertEquals(1, bookmarks.size)
        assertNull(bookmarks[0].addDateMillis)
    }

    @Test
    fun `preserves browser title and unescapes html entities`() {
        val html = """
            <DT><A HREF="https://example.com/item" ADD_DATE="1700000000">
                Priya&#39;s &amp; Arjun&quot;s &lt;Best&gt; Café &copy;
            </A>
        """.trimIndent()

        val bookmarks = BookmarkParser.parse(html)
        assertEquals(1, bookmarks.size)
        assertEquals("Priya's & Arjun\"s <Best> Café ©", bookmarks[0].title)
    }

    @Test
    fun `strips inner tags inside anchor tag`() {
        val html = """<DT><A HREF="https://example.com"><b>Bold</b> and <i>Italic</i> Title</A>"""
        val bookmarks = BookmarkParser.parse(html)

        assertEquals(1, bookmarks.size)
        assertEquals("Bold and Italic Title", bookmarks[0].title)
    }

    @Test
    fun `skips javascript and place urls`() {
        val html = """
            <DT><A HREF="javascript:alert(1)">Bookmarklet</A>
            <DT><A HREF="place:sort=8">History</A>
            <DT><A HREF="https://example.com/valid" ADD_DATE="1700000000">Valid Link</A>
        """.trimIndent()

        val bookmarks = BookmarkParser.parse(html)
        assertEquals(1, bookmarks.size)
        assertEquals("https://example.com/valid", bookmarks[0].url)
    }

    @Test
    fun `handles empty or malformed html without crashing`() {
        assertTrue(BookmarkParser.parse("").isEmpty())
        assertTrue(BookmarkParser.parse("<html><body>Just text no links</body></html>").isEmpty())
        assertTrue(BookmarkParser.parse("<A>No href attribute</A>").isEmpty())
    }
}
