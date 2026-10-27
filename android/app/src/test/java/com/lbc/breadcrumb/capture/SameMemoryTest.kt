package com.lbc.breadcrumb.capture

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SameMemoryTest {

    private fun key(url: String) = SameMemory.linkKey(url)

    @Test
    fun `a link is the same whatever tracking a share added`() {
        assertEquals(
            key("https://instagram.com/reel/De2SEI7JP8r"),
            key("https://www.instagram.com/reel/De2SEI7JP8r/?obrf=MW5lNzF1NGk1cWFrMg=="),
        )
        assertEquals(
            key("https://instagram.com/p/C9Hb2b5S2kq/"),
            key("https://www.instagram.com/p/C9Hb2b5S2kq/?igsh=MTc4MmM1YmI2Ng%3D%3D&utm_source=qr"),
        )
        assertEquals(key("https://x.com/NASA/status/1846989090950668421"), key("https://x.com/NASA/status/1846989090950668421?s=20&t=abc"))
        assertEquals(key("https://example.com/a?b=2&a=1"), key("http://example.com/a?a=1&b=2#comments"))
    }

    @Test
    fun `a youtube video is one link however it is shared`() {
        val watch = key("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertEquals(watch, key("https://youtu.be/dQw4w9WgXcQ?si=Xy12ab"))
        assertEquals(watch, key("https://m.youtube.com/watch?v=dQw4w9WgXcQ&feature=shared"))
        // A start time is a different place in the video.
        assertNotEquals(watch, key("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30"))
    }

    @Test
    fun `different pages are different links`() {
        assertNotEquals(key("https://instagram.com/reel/A"), key("https://instagram.com/reel/B"))
        assertNotEquals(key("https://example.com/a?id=1"), key("https://example.com/a?id=2"))
        // s and t mean something outside X.
        assertNotEquals(key("https://example.com/search?s=shoes"), key("https://example.com/search"))
    }

    @Test
    fun `a shared link is kept already when a kept link opens the same page`() {
        val kept = Memory(type = MemoryType.LINK, rawText = "https://instagram.com/reel/De2SEI7JP8r", capturedAt = 1_000)
        val shared = Memory(type = MemoryType.LINK, rawText = "Look at this https://www.instagram.com/reel/De2SEI7JP8r/?igsh=x")

        assertEquals(kept, SameMemory.keptLike(shared, listOf(kept)))
    }

    @Test
    fun `text is kept already when the words are, case and spacing aside`() {
        val kept = Memory(type = MemoryType.TEXT, rawText = "Try the brewery   burger\nin Indiranagar")
        val again = Memory(type = MemoryType.TEXT, rawText = "  try the brewery burger in indiranagar ")

        assertEquals(kept, SameMemory.keptLike(again, listOf(kept)))
        assertNull(SameMemory.keptLike(Memory(type = MemoryType.TEXT, rawText = "Try the brewery"), listOf(kept)))
    }

    @Test
    fun `the newest of several is the one kept, never the memory itself or another kind`() {
        val older = Memory(type = MemoryType.TEXT, rawText = "buy milk", capturedAt = 1_000)
        val newer = Memory(type = MemoryType.TEXT, rawText = "Buy milk", capturedAt = 2_000)
        val shared = Memory(type = MemoryType.TEXT, rawText = "buy milk", capturedAt = 3_000)

        assertEquals(newer, SameMemory.keptLike(shared, listOf(older, newer)))
        assertNull(SameMemory.keptLike(shared, listOf(shared)))
        assertNull(SameMemory.keptLike(shared, listOf(Memory(type = MemoryType.LINK, rawText = "buy milk"))))
    }
}
