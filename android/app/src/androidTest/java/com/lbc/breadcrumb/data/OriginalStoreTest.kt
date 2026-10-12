package com.lbc.breadcrumb.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * A memory's original is found by its file's name in the store, wherever its
 * localUri says it was: a restore into another package keeps every original.
 * The store here is a folder of the test's own, never the real originals.
 */
@RunWith(AndroidJUnit4::class)
class OriginalStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "original-store-test-${System.nanoTime()}").apply { mkdirs() }
    private val store = OriginalStore(context, root)

    @After
    fun tearDown() {
        root.parentFile?.let { File(it, "${root.name}-outside.txt").delete() }
        root.deleteRecursively()
    }

    private fun at(localUri: String?) = Memory(id = "m1", type = MemoryType.IMAGE, localUri = localUri)

    @Test
    fun aLocalUriNamingAnotherPackagesOriginalsResolvesToThisStoresFileOfThatName() {
        val kept = File(root, "m1.jpg").apply { writeText("picture") }
        val elsewhere = at("file:///data/user/0/com.lbc.breadcrumb.hackinverse/files/originals/m1.jpg")

        assertEquals(kept.canonicalFile, store.fileFor(elsewhere)?.canonicalFile)
    }

    @Test
    fun aTraversalNameResolvesToNothing() {
        File(root.parentFile, "${root.name}-outside.txt").writeText("not ours")

        assertNull(store.fileFor(at("file:///data/user/0/x/files/originals/..")))
        assertNull(store.fileFor(at("file:///data/user/0/x/files/originals/%2E%2E")))
        assertNull(store.fileFor(at("file://${root.path}/../${root.name}-outside.txt")))
    }

    @Test
    fun noFileOrNotAFileUriResolvesToNothing() {
        assertNull(store.fileFor(at(null)))
        assertNull(store.fileFor(at("file:///data/user/0/x/files/originals/missing.jpg")))
        assertNull(store.fileFor(at("content://somewhere/m1.jpg")))
    }
}
