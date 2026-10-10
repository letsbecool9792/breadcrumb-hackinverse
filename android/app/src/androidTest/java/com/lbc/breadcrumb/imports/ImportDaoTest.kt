package com.lbc.breadcrumb.imports

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lbc.breadcrumb.data.BreadcrumbDatabase
import com.lbc.breadcrumb.data.ImportDao
import com.lbc.breadcrumb.data.ImportedSource
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImportDaoTest {

    private lateinit var db: BreadcrumbDatabase
    private lateinit var importDao: ImportDao

    @Before
    fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BreadcrumbDatabase::class.java).build()
        importDao = db.importDao()
    }

    @After
    fun close() = db.close()

    @Test
    fun recordingAndCheckingExists() = runBlocking {
        assertFalse(importDao.exists("file:photo.jpg:1024"))

        importDao.record(ImportedSource("file:photo.jpg:1024", 1_000L))
        assertTrue(importDao.exists("file:photo.jpg:1024"))
        assertEquals(1, importDao.count())
    }

    @Test
    fun recordingDuplicateKeyReplacesWithoutDuplication() = runBlocking {
        importDao.record(ImportedSource("file:photo.jpg:1024", 1_000L))
        importDao.record(ImportedSource("file:photo.jpg:1024", 2_000L))

        assertEquals(1, importDao.count())
    }

    @Test
    fun batchRecordAndFindExistingKeys() = runBlocking {
        val sources = listOf(
            ImportedSource("file:a.png:100", 1_000L),
            ImportedSource("file:b.png:200", 1_000L),
        )
        importDao.recordAll(sources)

        val found = importDao.findExistingKeys(listOf("file:a.png:100", "file:b.png:200", "file:c.png:300"))
        assertEquals(2, found.size)
        assertTrue(found.contains("file:a.png:100"))
        assertTrue(found.contains("file:b.png:200"))
        assertFalse(found.contains("file:c.png:300"))
    }

    @Test
    fun clearRemovesAllImportedSources() = runBlocking {
        importDao.record(ImportedSource("key1", 1_000L))
        importDao.record(ImportedSource("key2", 1_000L))
        assertEquals(2, importDao.count())

        importDao.clear()
        assertEquals(0, importDao.count())
    }
}
