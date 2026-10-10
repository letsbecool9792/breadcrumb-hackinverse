package com.lbc.breadcrumb.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkRulesTest {

    @Test
    fun normalizeUrlUpgradesHttpToHttps() {
        assertEquals("https://example.com/path", LinkRules.normalizeUrl("http://example.com/path"))
        assertEquals("https://example.com/", LinkRules.normalizeUrl("http://example.com/"))
    }

    @Test
    fun normalizeUrlPreservesHttps() {
        assertEquals("https://example.com/path", LinkRules.normalizeUrl("https://example.com/path"))
    }

    @Test
    fun normalizeUrlPrependsHttpsToWwwAndBareDomains() {
        assertEquals("https://www.example.com/foo", LinkRules.normalizeUrl("www.example.com/foo"))
        assertEquals("https://example.com/foo", LinkRules.normalizeUrl("example.com/foo"))
    }

    @Test
    fun normalizeUrlRejectsInvalidOrNonHttpSchemes() {
        assertNull(LinkRules.normalizeUrl(""))
        assertNull(LinkRules.normalizeUrl("   "))
        assertNull(LinkRules.normalizeUrl("ftp://example.com"))
        assertNull(LinkRules.normalizeUrl("javascript:alert(1)"))
        assertNull(LinkRules.normalizeUrl("file:///android_asset/foo"))
    }

    @Test
    fun isAllowedUrlRejectsLocalhost() {
        assertFalse(LinkRules.isAllowedUrl("https://localhost"))
        assertFalse(LinkRules.isAllowedUrl("https://localhost:8080/test"))
        assertFalse(LinkRules.isAllowedUrl("https://app.localhost"))
        assertFalse(LinkRules.isAllowedUrl("https://localhost.localdomain"))
        assertFalse(LinkRules.isAllowedUrl("https://myhost.local"))
        assertFalse(LinkRules.isAllowedUrl("https://myhost.internal"))
        assertFalse(LinkRules.isAllowedUrl("https://myhost.lan"))
        assertFalse(LinkRules.isAllowedUrl("https://127.0.0.1:3000"))
        assertFalse(LinkRules.isAllowedUrl("https://127.0.0.2"))
    }

    @Test
    fun isAllowedUrlRejectsBareIpv4AndIpv6Addresses() {
        // Private / local IPv4
        assertFalse(LinkRules.isAllowedUrl("https://192.168.1.1/admin"))
        assertFalse(LinkRules.isAllowedUrl("https://10.0.0.1/"))
        assertFalse(LinkRules.isAllowedUrl("https://172.16.0.1/"))
        assertFalse(LinkRules.isAllowedUrl("https://169.254.1.1/"))

        // Public bare IPv4
        assertFalse(LinkRules.isAllowedUrl("https://8.8.8.8"))
        assertFalse(LinkRules.isAllowedUrl("https://1.1.1.1:8080"))

        // Bare IPv6
        assertFalse(LinkRules.isAllowedUrl("https://[::1]"))
        assertFalse(LinkRules.isAllowedUrl("https://[2001:db8::1]"))
    }

    @Test
    fun isAllowedUrlAllowsStandardDomainNames() {
        assertTrue(LinkRules.isAllowedUrl("https://example.com"))
        assertTrue(LinkRules.isAllowedUrl("https://www.example.com/path?query=1"))
        assertTrue(LinkRules.isAllowedUrl("https://sub.domain.co.uk/article"))
        assertTrue(LinkRules.isAllowedUrl("https://github.com/letsbecool9792"))
    }

    @Test
    fun extractTitleExtractsAndDecodesTitle() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <title>
                    Breadcrumb &amp; &quot;Hackinverse&quot; &#8212; 2026
                </title>
            </head>
            <body></body>
            </html>
        """.trimIndent()

        val title = LinkRules.extractTitle(html)
        assertEquals("Breadcrumb & \"Hackinverse\" — 2026", title)
    }

    @Test
    fun extractTitleStripsNestedTags() {
        val html = "<head><title><b>Breadcrumb</b> <i>Home</i></title></head>"
        assertEquals("Breadcrumb Home", LinkRules.extractTitle(html))
    }

    @Test
    fun extractTitleReturnsNullWhenMissingOrEmpty() {
        assertNull(LinkRules.extractTitle("<head><meta name=\"foo\" content=\"bar\"></head>"))
        assertNull(LinkRules.extractTitle("<head><title></title></head>"))
        assertNull(LinkRules.extractTitle("<head><title>   \n  </title></head>"))
    }

    @Test
    fun extractDescriptionExtractsStandardDescriptionMeta() {
        val html = """
            <head>
                <meta charset="utf-8">
                <meta name="description" content="Personal memory retrieval. Save anything in one tap.">
                <title>Breadcrumb</title>
            </head>
        """.trimIndent()

        assertEquals(
            "Personal memory retrieval. Save anything in one tap.",
            LinkRules.extractDescription(html),
        )
    }

    @Test
    fun extractDescriptionExtractsOgDescription() {
        val html = """
            <head>
                <meta property="og:description" content="OpenGraph description &amp; preview.">
            </head>
        """.trimIndent()

        assertEquals("OpenGraph description & preview.", LinkRules.extractDescription(html))
    }

    @Test
    fun extractDescriptionExtractsTwitterDescription() {
        val html = """
            <head>
                <meta name="twitter:description" content="Twitter card description.">
            </head>
        """.trimIndent()

        assertEquals("Twitter card description.", LinkRules.extractDescription(html))
    }

    @Test
    fun extractDescriptionHandlesReversedAttributesAndQuotes() {
        val html = """
            <head>
                <meta content='Single quotes and content first' name='description'>
            </head>
        """.trimIndent()

        assertEquals("Single quotes and content first", LinkRules.extractDescription(html))
    }

    @Test
    fun extractDescriptionReturnsEmptyWhenMissingOrBlank() {
        assertEquals("", LinkRules.extractDescription("<head><title>No Meta</title></head>"))
        assertEquals("", LinkRules.extractDescription("<head><meta name=\"description\" content=\"  \"></head>"))
    }

    @Test
    fun extractHeadContentStopsAtClosingHeadTag() {
        val html = "<head><title>Test</title></head><body><h1>Body content that should be excluded</h1></body>"
        val head = LinkRules.extractHeadContent(html)
        assertEquals("<head><title>Test</title></head>", head)
    }

    @Test
    fun decodeHtmlEntitiesDecodesNamedAndNumericEntities() {
        val encoded = "&lt;hello&gt; &amp; &quot;world&quot; &#39;test&#39; &nbsp; &mdash; &#8212; &#x2014;"
        assertEquals("<hello> & \"world\" 'test'   — — —", LinkRules.decodeHtmlEntities(encoded))
    }
}
