package com.lbc.breadcrumb.ui.home

import com.lbc.breadcrumb.data.Memory
import com.lbc.breadcrumb.data.MemoryType
import java.time.ZoneId
import java.util.Locale

/**
 * The words of a memory's detail (design §11), decided apart from drawing them
 * so the JVM can test them.
 */
internal object DetailText {

    /** Text found is shown whole up to this many characters, cut at a word; all of it is searched. */
    const val FOUND_TEXT_LIMIT = 1_600

    /** The heading under a picture or a page: its title, what was shared with it, what the model made of it. */
    data class Heading(val title: String?, val caption: String?, val summary: String?) {
        val isEmpty: Boolean get() = title == null && caption == null && summary == null
    }

    /**
     * For a picture or a PDF: its title, then the caption (the text shared with
     * it, when it differs from the title), then the summary (when it differs
     * from the title). For a link or a note, only the summary: the artifact
     * above is already its words.
     */
    fun heading(memory: Memory): Heading {
        val summary = memory.summary?.takeIf { it.isNotBlank() }
        return when (memory.type) {
            MemoryType.IMAGE, MemoryType.PDF -> {
                val title = ResultText.title(memory, summary)
                Heading(
                    title = title,
                    caption = memory.rawText?.trim()?.takeIf { it.isNotEmpty() && it != title },
                    summary = summary?.takeIf { it != title },
                )
            }
            else -> Heading(title = null, caption = null, summary = summary)
        }
    }

    enum class Provenance { SAVED, FROM, TAKEN, STATUS }

    /**
     * The provenance rows, in order: when it was saved, the app it came from
     * (lowercase, when there is one), the day a picture was taken (when known),
     * and the status while there is one.
     */
    fun provenance(
        memory: Memory,
        now: Long,
        cloudReads: Boolean = true,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Pair<Provenance, String>> = buildList {
        add(Provenance.SAVED to ResultText.saved(memory.capturedAt, now, zone))
        memory.sourceAppLabel?.takeIf { it.isNotBlank() }?.let { add(Provenance.FROM to it.lowercase(Locale.ROOT)) }
        memory.contentCreatedAt?.let { add(Provenance.TAKEN to ResultText.taken(it, zone)) }
        ResultText.status(memory, cloudReads)?.let { add(Provenance.STATUS to it) }
    }

    enum class Found { TEXT_FOUND, FROM_THE_PAGE, WHAT_IT_SHOWS }

    /**
     * The text found, by section: what the phone read ("from the page" for a
     * link), and what the model saw in a picture. Long text shows its first
     * 1,600 characters, cut at a word.
     */
    fun foundText(memory: Memory): List<Pair<Found, String>> = buildList {
        memory.extractedText?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add((if (memory.type == MemoryType.LINK) Found.FROM_THE_PAGE else Found.TEXT_FOUND) to ResultText.opening(it, FOUND_TEXT_LIMIT))
        }
        memory.readText?.trim()?.takeIf { it.isNotEmpty() }?.let {
            add(Found.WHAT_IT_SHOWS to ResultText.opening(it, FOUND_TEXT_LIMIT))
        }
    }

    /** What to do with a note that was typed. */
    sealed interface NoteEdit {
        /** The note is as it was: write nothing, upload nothing. */
        data object Unchanged : NoteEdit

        /** Write [note]; null clears it. */
        data class Write(val note: String?) : NoteEdit
    }

    /** Blank clears the note; the same words as before change nothing. */
    fun noteEdit(typed: String, current: String?): NoteEdit {
        val next = typed.trim().takeIf { it.isNotEmpty() }
        val was = current?.trim()?.takeIf { it.isNotEmpty() }
        return if (next == was) NoteEdit.Unchanged else NoteEdit.Write(next)
    }
}
