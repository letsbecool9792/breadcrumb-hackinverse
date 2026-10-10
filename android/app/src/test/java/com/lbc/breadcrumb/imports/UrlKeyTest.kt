package com.lbc.breadcrumb.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UrlKeyTest {

    @Test
    fun `the scheme, www, a trailing slash and the fragment are ignored, and the host lowercased`() {
        val key = UrlKey.of("https://www.Example.com/page/")
        assertEquals(key, UrlKey.of("http://example.com/page"))
        assertEquals(key, UrlKey.of("example.com/page#reviews"))
        assertEquals(key, UrlKey.of("WWW.EXAMPLE.COM/page/#top"))
        assertEquals("example.com/page", key)
    }

    @Test
    fun `a path's case and the query still tell pages apart`() {
        assertNotEquals(UrlKey.of("https://example.com/Page"), UrlKey.of("https://example.com/page"))
        assertNotEquals(UrlKey.of("https://youtube.com/watch?v=a"), UrlKey.of("https://youtube.com/watch?v=b"))
        assertEquals("youtube.com/watch?v=a", UrlKey.of("https://www.youtube.com/watch/?v=a#t=10"))
    }

    @Test
    fun `a site's root is its host`() {
        assertEquals("example.com", UrlKey.of("https://example.com/"))
        assertEquals("example.com?ref=x", UrlKey.of("https://example.com/?ref=x"))
    }

    @Test
    fun `an address with no host has no key`() {
        assertNull(UrlKey.of("https://"))
        assertNull(UrlKey.of("   "))
    }

    @Test
    fun `every address in shared text is found, without a sentence's punctuation`() {
        assertEquals(
            setOf("example.com/a", "news.site.org/b?id=4"),
            UrlKey.allIn("Read this (https://www.example.com/a/). Also http://news.site.org/b?id=4, thanks"),
        )
        assertEquals(emptySet<String>(), UrlKey.allIn("no links here"))
    }
}
