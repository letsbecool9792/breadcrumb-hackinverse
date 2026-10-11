package com.lbc.breadcrumb.capture

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One picked file. The uri is kept as text, so the draft's codec needs nothing
 * from Android and the JVM can test it; [contentUri] is the Uri to use.
 */
data class DraftFile(
    val uri: String,
    val name: String?,
    val isPdf: Boolean,
) {
    val contentUri: Uri get() = Uri.parse(uri)
}

data class Draft(
    val text: String = "",
    val attachments: List<DraftFile> = emptyList(),
) {
    fun isEmpty(): Boolean = text.isBlank() && attachments.isEmpty()
    fun isNotEmpty(): Boolean = !isEmpty()
}

@Serializable
private data class DraftFileDto(
    val uri: String,
    val name: String? = null,
    val isPdf: Boolean = false,
)

@Serializable
private data class DraftDto(
    val text: String = "",
    val attachments: List<DraftFileDto> = emptyList(),
)

object DraftCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(draft: Draft): String {
        val dto = DraftDto(
            text = draft.text,
            attachments = draft.attachments.map { file ->
                DraftFileDto(
                    uri = file.uri,
                    name = file.name,
                    isPdf = file.isPdf,
                )
            },
        )
        return json.encodeToString(DraftDto.serializer(), dto)
    }

    fun decode(raw: String?): Draft {
        if (raw.isNullOrBlank()) return Draft()
        return try {
            val dto = json.decodeFromString(DraftDto.serializer(), raw)
            Draft(
                text = dto.text,
                attachments = dto.attachments.map { fileDto ->
                    DraftFile(uri = fileDto.uri, name = fileDto.name, isPdf = fileDto.isPdf)
                },
            )
        } catch (_: Exception) {
            Draft()
        }
    }
}

class DraftStore(
    private val context: Context,
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
) {

    suspend fun load(): Draft = withContext(Dispatchers.IO) {
        val raw = prefs.getString(KEY_DRAFT, null) ?: return@withContext Draft()
        val draft = DraftCodec.decode(raw)
        if (draft.isEmpty()) {
            prefs.edit().remove(KEY_DRAFT).apply()
            return@withContext Draft()
        }

        val validAttachments = draft.attachments.filter { file ->
            hasGrant(file.contentUri)
        }
        val filtered = draft.copy(attachments = validAttachments)
        if (filtered.attachments.size != draft.attachments.size) {
            if (filtered.isEmpty()) {
                prefs.edit().remove(KEY_DRAFT).apply()
            } else {
                prefs.edit().putString(KEY_DRAFT, DraftCodec.encode(filtered)).apply()
            }
        }
        filtered
    }

    fun save(draft: Draft) {
        if (draft.isEmpty()) {
            prefs.edit().remove(KEY_DRAFT).apply()
        } else {
            prefs.edit().putString(KEY_DRAFT, DraftCodec.encode(draft)).apply()
        }
    }

    fun clear() {
        val raw = prefs.getString(KEY_DRAFT, null)
        if (raw != null) {
            val draft = DraftCodec.decode(raw)
            for (file in draft.attachments) {
                releasePermission(file.contentUri)
            }
        }
        prefs.edit().remove(KEY_DRAFT).apply()
    }

    fun takePermission(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // some providers refuse; then it lasts the run
        }
    }

    fun releasePermission(uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            // ignore
        }
    }

    private fun hasGrant(uri: Uri): Boolean {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return false
        val persisted = try {
            context.contentResolver.persistedUriPermissions
        } catch (_: Exception) {
            emptyList()
        }
        if (persisted.any { it.uri == uri && it.isReadPermission }) {
            return true
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { true } ?: false
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val PREFS_NAME = "breadcrumb_draft"
        private const val KEY_DRAFT = "draft"
    }
}
