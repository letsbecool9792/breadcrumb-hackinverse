package com.lbc.breadcrumb.capture

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DraftStoreTest {

    private lateinit var context: Context
    private lateinit var draftStore: DraftStore
    private var testUri: Uri? = null

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("test_draft_store", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        draftStore = DraftStore(context, prefs)

        // Create a real accessible content URI
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "draft_photo.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        testUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.also { uri ->
            context.contentResolver.openOutputStream(uri)?.use { it.write(byteArrayOf(1, 2, 3, 4)) }
        }
    }

    @After
    fun tearDown() {
        draftStore.clear()
        testUri?.let { uri ->
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (_: Exception) {}
        }
    }

    @Test
    fun fileWhoseGrantIsGoneIsDroppedOnLoad() = runBlocking {
        val validUri = testUri ?: Uri.parse("content://settings/system/ringtone")
        val revokedUri = Uri.parse("content://com.lbc.breadcrumb.test.revoked/document/9999")

        val draft = Draft(
            text = "Draft with mixed grants",
            attachments = listOf(
                DraftFile(uri = validUri.toString(), name = "valid.jpg", isPdf = false),
                DraftFile(uri = revokedUri.toString(), name = "revoked.jpg", isPdf = false),
            ),
        )

        draftStore.save(draft)

        val loaded = draftStore.load()

        assertEquals("Draft with mixed grants", loaded.text)
        assertEquals(1, loaded.attachments.size)
        assertEquals(validUri.toString(), loaded.attachments[0].uri)
    }

    @Test
    fun savingEmptyDraftRemovesItFromStore() = runBlocking {
        val draft = Draft(text = "temporary note")
        draftStore.save(draft)
        assertFalse(draftStore.load().isEmpty())

        draftStore.save(Draft())
        assertTrue(draftStore.load().isEmpty())
    }

    @Test
    fun roundTripPreservesDraft() = runBlocking {
        val validUri = testUri ?: Uri.parse("content://settings/system/ringtone")
        val draft = Draft(
            text = "Saved draft",
            attachments = listOf(
                DraftFile(uri = validUri.toString(), name = "valid.jpg", isPdf = false),
            ),
        )

        draftStore.save(draft)
        val loaded = draftStore.load()

        assertEquals(draft, loaded)
    }
}
