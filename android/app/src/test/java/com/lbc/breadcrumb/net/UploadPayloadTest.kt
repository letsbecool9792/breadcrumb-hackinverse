package com.lbc.breadcrumb.net

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import com.lbc.breadcrumb.data.SyncState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class UploadPayloadTest {

    private val memory = Memory(
        id = "m1",
        type = MemoryType.IMAGE,
        hasLink = true,
        capturedAt = 1_000,
        contentCreatedAt = 900,
        sourceApp = "com.whatsapp",
        sourceAppLabel = "WhatsApp",
        rawText = "https://example.com/job",
        note = "Priya says apply early",
        localUri = "file:///data/user/0/com.lbc.breadcrumb/files/originals/m1.jpg",
        extractedText = "Qualcomm is hiring",
        title = "Intern",
        summary = "A job posting",
        entitiesJson = "[]",
        kind = "job posting",
        readText = "read by the model",
        enrichedAt = 2_000,
        remoteId = "m1",
        imageSentAt = 3_000,
        syncState = SyncState.UPLOADING,
        updatedAt = 1_500,
    )

    private fun encoded(memories: List<UploadMemory>) = ServerClient.json.encodeToString(memories)

    @Test
    fun `the payload never carries localUri`() {
        val sent = encoded(listOf(memory.toUpload()!!))

        assertFalse(sent.contains("localUri"))
        assertFalse(sent.contains("file://"))
        assertFalse(sent.contains("originals/m1.jpg"))
    }

    @Test
    fun `the payload is the contract's fields and nothing the phone keeps for itself`() {
        val fields = Json.parseToJsonElement(encoded(listOf(memory.toUpload()!!))).jsonArray[0].jsonObject

        assertEquals(
            setOf(
                "id", "type", "hasLink", "capturedAt", "contentCreatedAt", "sourceApp", "sourceAppLabel",
                "title", "rawText", "extractedText", "note", "updatedAt",
            ),
            fields.keys,
        )
        assertEquals("IMAGE", fields.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `nulls are sent as nulls, not left out`() {
        val bare = Memory(id = "m2", type = MemoryType.TEXT, capturedAt = 1_000, updatedAt = 1_000)
        val fields = Json.parseToJsonElement(encoded(listOf(bare.toUpload()!!))).jsonArray[0].jsonObject

        assertEquals(12, fields.size)
        assertEquals(JsonNull, fields["note"])
    }
}
