package com.lbc.breadcrumb.backup

import com.lbc.breadcrumb.data.Embedding
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupFormatTest {

    private val memory = Memory(
        id = "m1",
        type = MemoryType.IMAGE,
        hasLink = true,
        capturedAt = 1_000,
        contentCreatedAt = 900,
        sourceApp = "com.whatsapp",
        sourceAppLabel = "WhatsApp",
        rawText = "the flat https://example.com/flat",
        note = "Priya's flat",
        localUri = "file:///data/user/0/com.lbc.breadcrumb/files/originals/m1.jpg",
        extractedText = "2 BHK, balcony",
        title = "Flat",
        summary = "A flat listing",
        syncState = SyncState.SYNCED,
        updatedAt = 2_000,
    )

    @Test
    fun `a memory comes back as it went, its original wherever this phone keeps it`() {
        val manifest = BackupManifest(exportedAt = 5, memories = listOf(BackupFormat.memory(memory, "m1.jpg")))
        val back = BackupFormat.decode(BackupFormat.encode(manifest))!!

        assertEquals("m1.jpg", back.memories.single().original)
        val restored = BackupFormat.memory(back.memories.single(), "file:///elsewhere/originals/m1.jpg")
        assertEquals(memory.copy(localUri = "file:///elsewhere/originals/m1.jpg"), restored)
    }

    @Test
    fun `a vector comes back byte for byte`() {
        val vector = byteArrayOf(0, 1, -1, 127, -128, 42)
        val back = BackupFormat.embedding(BackupFormat.embedding(Embedding("m1", "key", vector, 7)))!!
        assertArrayEquals(vector, back.vector)
        assertEquals("key", back.key)
        assertEquals(7, back.embeddedAt)
    }

    @Test
    fun `junk, a newer format, or a kind this app doesn't know, is not read`() {
        assertNull(BackupFormat.decode("not json"))
        assertNull(BackupFormat.decode("""{"format":2,"exportedAt":1,"memories":[]}"""))
        val unknown = BackupFormat.memory(memory, null).copy(type = "HOLOGRAM")
        assertNull(BackupFormat.memory(unknown, null))
        // Fields a later format adds are passed over.
        assertEquals(0, BackupFormat.decode("""{"format":1,"exportedAt":1,"memories":[],"albums":[]}""")?.memories?.size)
    }

    @Test
    fun `the bar says how it goes`() {
        assertEquals("backing up · 3 of 30", BackupProgress.line(BackupProgress.Saving(3, 30)))
        assertEquals("backed up 30 · 13 MB", BackupProgress.line(BackupProgress.Saved(30, 13_200_000)))
        assertEquals("backed up 2 · 0.4 MB", BackupProgress.line(BackupProgress.Saved(2, 400_000)))
        assertEquals("restoring · 3 of 30", BackupProgress.line(BackupProgress.Restoring(3, 30)))
        assertEquals("restored 28 · 2 already here", BackupProgress.line(BackupProgress.Restored(28, 2)))
        assertEquals("restored 30", BackupProgress.line(BackupProgress.Restored(30, 0)))
        assertEquals("nothing new · 30 already here", BackupProgress.line(BackupProgress.Restored(0, 30)))
        assertEquals("restore stopped · that isn't a breadcrumb backup", BackupProgress.line(BackupProgress.Failed(true, "that isn't a breadcrumb backup")))
        assertNull(BackupProgress.line(null))
    }
}
