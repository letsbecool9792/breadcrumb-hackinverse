package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.security.MessageDigest

/**
 * What the phone embeds for a memory, and how a search phrase is put to the
 * model. Pure. The prompts are EmbeddingGemma's: with them, the spike ranked
 * every one of its twelve vague searches first.
 */
object EmbedText {

    /** The model every vector comes from; a vector from any other is embedded again. */
    const val MODEL = "embeddinggemma-2-text-vision-440m"

    /** The model reads at most 1,024 tokens; 2,000 characters stays under that. */
    const val MAX_CHARS = 2_000

    /** A picture is embedded as a picture only when OCR read fewer characters than this. */
    const val MIN_OCR_CHARS = 80

    /** What the phone wrote or read itself: never the server's summary, so it is the same with the server off. */
    fun text(memory: Memory): String =
        listOfNotNull(memory.title, memory.rawText, memory.extractedText, memory.note)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("\n")
            .take(MAX_CHARS)

    fun document(text: String): String = "title: none | text: $text"

    fun query(phrase: String): String = "task: search result | query: ${phrase.trim()}"

    sealed interface Want {
        /** Changes with the model, the way it is embedded and every word of the prompt. */
        val key: String
    }

    data class AsText(val prompt: String, override val key: String) : Want

    /**
     * A picture OCR could barely read, embedded as a picture with its few words
     * beside it. Slow and memory-hungry on the CPU (about 5 s and 1 GB on the
     * demo phone), so it waits for the charger; meanwhile [meanwhile] stands in.
     */
    data class AsPicture(val prompt: String?, override val key: String, val meanwhile: AsText?) : Want

    /**
     * What to embed for [memory], or null when there is nothing to embed yet: a
     * picture or PDF still being read, a voice note not heard yet, or a memory
     * with no words and no picture.
     */
    fun want(memory: Memory, hasPicture: Boolean): Want? {
        val beingRead = memory.extractedText == null &&
            (memory.type == MemoryType.IMAGE || memory.type == MemoryType.PDF || memory.type == MemoryType.AUDIO)
        if (beingRead) return null

        val text = text(memory)
        val asText = text.takeIf { it.isNotEmpty() }?.let { AsText(document(it), key("text", document(it))) }
        val barelyRead = (memory.extractedText?.trim()?.length ?: 0) < MIN_OCR_CHARS
        if (memory.type == MemoryType.IMAGE && hasPicture && barelyRead) {
            val prompt = asText?.prompt
            return AsPicture(prompt, key("picture", prompt.orEmpty()), meanwhile = asText)
        }
        return asText
    }

    private fun key(how: String, prompt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(prompt.toByteArray())
        return "$MODEL|$how|" + digest.take(12).joinToString("") { "%02x".format(it) }
    }
}
