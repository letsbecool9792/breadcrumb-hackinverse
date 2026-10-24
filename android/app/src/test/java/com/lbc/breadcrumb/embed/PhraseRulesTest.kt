package com.lbc.breadcrumb.embed

import com.lbc.breadcrumb.net.Interpretation
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PhraseRulesTest {

    // A Saturday.
    private val today = LocalDate.parse("2026-10-24")
    private val sources = listOf(
        "Amazon", "Chrome", "GitHub", "Gmail", "Google Photos", "Instagram", "LinkedIn", "Medium", "Photos",
        "Telegram", "WhatsApp", "X", "YouTube", "Zomato",
    )

    private fun parse(phrase: String) = PhraseRules.parse(phrase, today, sources)

    private fun days(phrase: String) = parse(phrase).let { it.from to it.to }

    @Test
    fun `the headline search comes apart into words, a kind and a month`() {
        assertEquals(
            Interpretation(query = "internship", types = listOf("IMAGE"), from = "2026-04-01", to = "2026-04-30"),
            parse("that internship screenshot from april"),
        )
    }

    @Test
    fun `a month is the latest one up to today`() {
        assertEquals("2025-12-01" to "2025-12-31", days("photos from december"))
        assertEquals("2026-10-01" to "2026-10-24", days("notes from october"))
        assertEquals("2025-03-01" to "2025-03-31", days("march 2025 bank statement"))
    }

    @Test
    fun `a day of a month, with or without its year`() {
        assertEquals("2026-05-03" to "2026-05-03", days("that may 3rd screenshot"))
        assertEquals("2026-09-12" to "2026-09-12", days("images from the 12th of september"))
        assertEquals("2025-12-25" to "2025-12-25", days("25 dec"))
        assertEquals("2026-10-05" to "2026-10-05", days("screenshot i took on the 5th"))
        assertEquals("2026-09-28" to "2026-09-28", days("the pdf from the 28th"))
    }

    @Test
    fun `may is the month only where it can't be the verb, and april fools is no date`() {
        assertEquals("2026-05-01" to "2026-05-31", days("rent receipt may"))
        assertEquals("2026-05-01" to "2026-05-31", days("pictures from may"))
        assertEquals(Interpretation(query = "help sleep", types = listOf("LINK")), parse("article that may help with sleep"))
        assertEquals(Interpretation(query = "april fools", types = listOf("IMAGE")), parse("april fools meme"))
    }

    @Test
    fun `days named relative to today`() {
        assertEquals("2026-10-24" to "2026-10-24", days("what did i save today"))
        assertEquals("2026-10-23" to "2026-10-23", days("notes from yesterday"))
        assertEquals("2026-10-19" to "2026-10-24", days("pdfs i saved this week"))
        assertEquals("2026-10-12" to "2026-10-18", days("screenshots from last week"))
        assertEquals("2026-10-17" to "2026-10-18", days("the meme from last weekend"))
        assertEquals("2026-09-01" to "2026-09-30", days("pdf from professor last month"))
        assertEquals("2026-01-01" to "2026-10-24", days("threads from this year"))
        assertEquals("2026-10-19" to "2026-10-19", days("lecture notes from monday"))
        assertEquals("2026-10-21" to "2026-10-24", days("jobs in the last 3 days"))
        assertEquals("2026-10-10" to "2026-10-24", days("links from the past 2 weeks"))
        assertEquals("2026-04-01" to "2026-10-24", days("everything since april"))
    }

    @Test
    fun `so long ago is that day, or that whole week or month`() {
        assertEquals("2026-10-21" to "2026-10-21", days("three days ago"))
        assertEquals("2026-10-05" to "2026-10-11", days("stuff from two weeks ago"))
        assertEquals("2026-07-01" to "2026-07-31", days("that cv template from 3 months ago"))
        assertEquals("2026-10-22" to "2026-10-22", days("a couple of days ago"))
    }

    @Test
    fun `a year is a filter after from or in, and words otherwise`() {
        assertEquals(Interpretation(types = listOf("PDF"), from = "2025-01-01", to = "2025-12-31"), parse("docs from 2025"))
        assertEquals(Interpretation(query = "2025 tax return"), parse("2025 tax return"))
    }

    @Test
    fun `a source is one of the names offered, by name or a short one`() {
        assertEquals(Interpretation(types = listOf("LINK"), sourceApp = "WhatsApp"), parse("link someone sent me on whatsapp"))
        assertEquals("Instagram", parse("recipe on insta").sourceApp)
        assertEquals("YouTube", parse("yt tutorial on docker").sourceApp)
        assertEquals("X", parse("tweet about llm benchmarks").sourceApp)
        assertEquals("X", parse("that thread on x").sourceApp)
        assertEquals("Google Photos", parse("the one in google photos").sourceApp)
    }

    @Test
    fun `an app named with an everyday word is a source only when it reads as one`() {
        assertEquals(Interpretation(query = "cat", types = listOf("IMAGE")), parse("photos of my cat"))
        assertEquals("Photos", parse("screenshot shared from photos").sourceApp)
        assertEquals("Photos", parse("pictures in the photos app").sourceApp)
    }

    @Test
    fun `a name that isn't offered stays in the words`() {
        assertEquals(Interpretation(query = "receipt swiggy"), parse("the receipt from swiggy"))
    }

    @Test
    fun `only the words that carry meaning are left to search`() {
        assertEquals("qualcomm hackathon", parse("the qualcomm guy from the hackathon").query)
        assertEquals("ramen", parse("ramen").query)
        assertEquals(Interpretation(), parse("what did i save"))
    }
}
