package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SitesTest {

    @Test
    fun `sites are named as the server names them`() {
        assertEquals("YouTube", Sites.name("youtu.be"))
        assertEquals("LinkedIn", Sites.name("www.linkedin.com"))
        assertEquals("Krew3", Sites.name("www.krew3.site"))
        assertEquals("Irctc", Sites.name("www.irctc.co.in"))
        assertNull(Sites.name("192.168.1.10"))
        assertNull(Sites.name("localhost"))
    }

    @Test
    fun `every link in a text has its site`() {
        assertEquals(listOf("YouTube", "GitHub"), Sites.of("watch https://youtu.be/x and www.github.com/y"))
        assertEquals(emptyList<String>(), Sites.of("no links here"))
    }

    @Test
    fun `the sources offered to the parse are the apps shared from and the sites saved`() {
        val memories = listOf(
            Memory(type = MemoryType.TEXT, rawText = "burger", sourceAppLabel = "WhatsApp"),
            Memory(type = MemoryType.LINK, rawText = "https://www.linkedin.com/jobs/view/1"),
            Memory(type = MemoryType.LINK, rawText = "https://youtu.be/a", sourceAppLabel = "Chrome"),
            Memory(type = MemoryType.LINK, rawText = "https://wa.me/123", sourceAppLabel = "WhatsApp"),
        )
        assertEquals(listOf("Chrome", "LinkedIn", "WhatsApp", "YouTube"), Sites.sourcesOf(memories))
    }
}
