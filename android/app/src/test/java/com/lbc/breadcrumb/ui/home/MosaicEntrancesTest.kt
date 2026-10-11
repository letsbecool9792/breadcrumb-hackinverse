package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class MosaicEntrancesTest {

    private var clock = 0L
    private val entrances = MosaicEntrances { clock }

    @Test
    fun `the list the app opened on rises, one tile after another`() {
        entrances.submit(listOf("a", "b", "c"))

        assertEquals(Entrance.Rise(0), entrances.entranceFor("a", 0))
        assertEquals(Entrance.Rise(45), entrances.entranceFor("b", 1))
        assertEquals(Entrance.Rise(90), entrances.entranceFor("c", 2))
    }

    @Test
    fun `the wait stops growing after ten tiles`() {
        entrances.submit((0..19).map { "m$it" })

        assertEquals(Entrance.Rise(450), entrances.entranceFor("m10", 10))
        assertEquals(Entrance.Rise(450), entrances.entranceFor("m19", 19))
    }

    @Test
    fun `a tile a scroll reaches after the opening rises with no wait`() {
        entrances.submit((0..29).map { "m$it" })
        clock = 1_600

        assertEquals(Entrance.Rise(0), entrances.entranceFor("m25", 25))
    }

    @Test
    fun `a memory that arrives while the mosaic is up drops`() {
        entrances.submit(listOf("a", "b"))
        entrances.entranceFor("a", 0)
        entrances.entranceFor("b", 1)

        entrances.submit(listOf("new", "a", "b"))

        assertEquals(Entrance.Drop, entrances.entranceFor("new", 0))
    }

    @Test
    fun `a memory that arrives during the opening window still drops, not rises`() {
        entrances.submit(listOf("a"))
        clock = 300

        entrances.submit(listOf("new", "a"))

        assertEquals(Entrance.Drop, entrances.entranceFor("new", 0))
    }

    @Test
    fun `a tile already shown does nothing`() {
        entrances.submit(listOf("a", "b"))
        entrances.entranceFor("a", 0)

        assertEquals(Entrance.None, entrances.entranceFor("a", 0))

        entrances.submit(listOf("new", "a", "b"))
        assertEquals(Entrance.None, entrances.entranceFor("a", 1))
    }

    @Test
    fun `an unchanged list replays nothing`() {
        entrances.submit(listOf("a", "b"))
        entrances.entranceFor("a", 0)
        entrances.entranceFor("b", 1)

        entrances.submit(listOf("a", "b"))

        assertEquals(Entrance.None, entrances.entranceFor("a", 0))
        assertEquals(Entrance.None, entrances.entranceFor("b", 1))
    }

    @Test
    fun `an id that leaves and returns drops again`() {
        entrances.submit(listOf("a", "b"))
        entrances.entranceFor("a", 0)
        entrances.entranceFor("b", 1)

        entrances.submit(listOf("b"))
        entrances.submit(listOf("a", "b"))

        assertEquals(Entrance.Drop, entrances.entranceFor("a", 0))
        assertEquals(Entrance.None, entrances.entranceFor("b", 1))
    }

    @Test
    fun `a memory that leaves before it was ever drawn is forgotten too`() {
        entrances.submit(listOf("a", "b"))

        entrances.submit(listOf("b"))
        entrances.submit(listOf("a", "b"))

        assertEquals(Entrance.Drop, entrances.entranceFor("a", 0))
    }

    @Test
    fun `an empty first list is the opening, so what follows drops`() {
        entrances.submit(emptyList())

        entrances.submit(listOf("first"))

        assertEquals(Entrance.Drop, entrances.entranceFor("first", 0))
    }
}
