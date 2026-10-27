package com.lbc.breadcrumb.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.Embedding
import com.lbc.breadcrumb.data.ImportedSource
import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.OriginalStore
import com.lbc.breadcrumb.data.SpokenNote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A backup out of one phone and into another, on the phone: two in-memory databases and two
 * stores in the cache, never the app's own.
 */
@RunWith(AndroidJUnit4::class)
class BackupsTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val roots = listOf("backup-test-old", "backup-test-new").map { File(context.cacheDir, it) }
    private lateinit var old: Phone
    private lateinit var new: Phone

    private inner class Phone(val root: File) {
        val db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        val store = OriginalStore(context, root)
        val backups = Backups(context, db, store, CoroutineScope(Dispatchers.IO), scheduleWork = {})

        fun file(name: String, bytes: ByteArray): String {
            root.mkdirs()
            return store.uriFor(File(root, name).apply { writeBytes(bytes) })
        }
    }

    @Before
    fun open() {
        old = Phone(roots[0])
        new = Phone(roots[1])
    }

    @After
    fun close() {
        old.db.close()
        new.db.close()
        roots.forEach { it.deleteRecursively() }
    }

    private val picture = byteArrayOf(1, 2, 3, 4)
    private val said = byteArrayOf(9, 8, 7)
    private val vector = ByteArray(16) { it.toByte() }

    private fun fill() = runBlocking {
        old.db.memoryDao().upsertAll(
            listOf(
                Memory(id = "photo", type = MemoryType.IMAGE, localUri = old.file("photo.jpg", picture), note = "Priya's flat", extractedText = "balcony", capturedAt = 3),
                Memory(id = "link", type = MemoryType.LINK, rawText = "https://example.com", capturedAt = 2),
                Memory(id = "text", type = MemoryType.TEXT, rawText = "buy milk", capturedAt = 1),
            ),
        )
        old.db.embeddingDao().upsert(Embedding("photo", "key", vector, 5))
        old.file("photo" + OriginalStore.SPOKEN, said)
        old.db.spokenNoteDao().upsert(SpokenNote("photo", recordedAt = 4, heardAt = 6))
        old.db.importDao().record(ImportedSource("folder:photo.jpg", 7))
    }

    private fun backup(): ByteArray = runBlocking {
        val out = ByteArrayOutputStream()
        val (memories, bytes) = old.backups.save(out)
        assertEquals(3, memories)
        assertEquals(out.size().toLong(), bytes)
        out.toByteArray()
    }

    @Test
    fun everythingComesBackOnANewPhone() = runBlocking {
        fill()

        val (added, already) = new.backups.restore(ByteArrayInputStream(backup()))

        assertEquals(3 to 0, added to already)
        val photo = new.db.memoryDao().getById("photo")!!
        assertEquals("Priya's flat", photo.note)
        assertEquals("balcony", photo.extractedText)
        assertArrayEquals(picture, new.store.fileFor(photo)!!.readBytes())
        assertArrayEquals(vector, new.db.embeddingDao().getAll().single().vector)
        assertEquals(SpokenNote("photo", 4, 6), new.db.spokenNoteDao().get("photo"))
        assertArrayEquals(said, new.store.spokenFile("photo")!!.readBytes())
        assertEquals(listOf("folder:photo.jpg"), new.db.importDao().getAll().map { it.key })
        // And word search finds what came back.
        assertEquals(listOf("photo"), new.db.readingDao().search("balcony").map { it.id })
    }

    @Test
    fun whatThePhoneHasIsLeftAsItIs() = runBlocking {
        fill()
        val backup = backup()
        new.db.memoryDao().upsert(Memory(id = "photo", type = MemoryType.IMAGE, localUri = new.file("photo.jpg", byteArrayOf(5)), note = "mine", capturedAt = 3))

        val (added, already) = new.backups.restore(ByteArrayInputStream(backup))

        assertEquals(2 to 1, added to already)
        val photo = new.db.memoryDao().getById("photo")!!
        assertEquals("mine", photo.note)
        assertArrayEquals(byteArrayOf(5), new.store.fileFor(photo)!!.readBytes())
        assertNull(new.db.spokenNoteDao().get("photo"))
        // Restoring the same backup again adds nothing.
        assertEquals(0 to 3, new.backups.restore(ByteArrayInputStream(backup)))
    }

    @Test
    fun somethingElseAddsNothing() = runBlocking {
        val notABackup = ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                zip.putNextEntry(ZipEntry("originals/photo.jpg"))
                zip.write(picture)
                zip.closeEntry()
            }
        }.toByteArray()

        for (bytes in listOf(notABackup, "plain text".toByteArray())) {
            try {
                new.backups.restore(ByteArrayInputStream(bytes))
                fail("restored something that isn't a backup")
            } catch (_: Backups.NotABackup) {
            } catch (_: java.util.zip.ZipException) {
            }
        }
        assertEquals(0, new.db.memoryDao().count())
        assertTrue(new.store.files().isEmpty())
    }

    @Test
    fun aFileTheBackupDoesNotNameIsLeftOut() = runBlocking {
        fill()

        new.backups.restore(ByteArrayInputStream(withExtra(backup(), "originals/stranger.jpg", "elsewhere.jpg")))

        assertEquals(setOf("photo.jpg", "photo" + OriginalStore.SPOKEN), new.store.files().map { it.name }.toSet())
    }

    @Test
    fun aFileReachingOutOfTheStoreIsNeverWritten() = runBlocking {
        fill()
        val backup = withExtra(backup(), "originals/../escaped.jpg")

        try {
            new.backups.restore(ByteArrayInputStream(backup))
        } catch (_: java.util.zip.ZipException) {
            // Android 14 and later refuse such a zip outright: then nothing at all comes in.
            assertEquals(0, new.db.memoryDao().count())
            assertTrue(new.store.files().isEmpty())
        }
        assertTrue(!File(roots[1].parentFile, "escaped.jpg").exists())
    }

    /** [backup] with more entries after its own, each holding a picture. */
    private fun withExtra(backup: ByteArray, vararg names: String): ByteArray =
        ByteArrayOutputStream().also { bytes ->
            ZipOutputStream(bytes).use { zip ->
                ZipInputStreamEntries(backup).forEach { (name, data) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(data)
                    zip.closeEntry()
                }
                for (name in names) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(picture)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    /** A zip's entries, in order. */
    private fun ZipInputStreamEntries(bytes: ByteArray): List<Pair<String, ByteArray>> =
        java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.map { it.name to zip.readBytes() }.toList()
        }
}
