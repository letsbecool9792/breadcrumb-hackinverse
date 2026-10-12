package com.lbc.breadcrumb.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class SourcesTest {

    private val own = "com.lbc.breadcrumb"

    @Test
    fun `an app is a source, with its name`() {
        assertEquals("com.whatsapp" to "WhatsApp", Sources.of("com.whatsapp", "WhatsApp", own))
        assertEquals("com.android.chrome" to "Chrome", Sources.of("com.android.chrome", " Chrome ", own))
    }

    @Test
    fun `system surfaces are not sources`() {
        for (system in listOf("com.android.systemui", "com.android.intentresolver", "android", "com.android.shell")) {
            assertEquals(null to null, Sources.of(system, "System", own))
        }
    }

    @Test
    fun `breadcrumb itself is not a source`() {
        assertEquals(null to null, Sources.of(own, "Breadcrumb", own))
    }

    @Test
    fun `no package is no source`() {
        assertEquals(null to null, Sources.of(null, "WhatsApp", own))
        assertEquals(null to null, Sources.of("  ", "WhatsApp", own))
    }

    @Test
    fun `a name that is just the package is no name`() {
        assertEquals("com.example.notes" to null, Sources.of("com.example.notes", "com.example.notes", own))
        assertEquals("com.example.notes" to null, Sources.of("com.example.notes", null, own))
    }
}
