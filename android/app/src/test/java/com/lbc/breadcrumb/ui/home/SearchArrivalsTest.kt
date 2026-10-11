package com.lbc.breadcrumb.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchArrivalsTest {

    private val arrivals = SearchArrivals()

    @Test
    fun `rows arrive one after another`() {
        arrivals.begin("qualcomm")

        assertEquals(0L, arrivals.delayFor("a", 0))
        assertEquals(40L, arrivals.delayFor("b", 1))
        assertEquals(80L, arrivals.delayFor("c", 2))
    }

    @Test
    fun `the wait stops growing after eight places`() {
        arrivals.begin("qualcomm")

        assertEquals(320L, arrivals.delayFor("far", 8))
        assertEquals(320L, arrivals.delayFor("farther", 20))
    }

    @Test
    fun `a row that has arrived does not arrive again, re-ranked or scrolled back`() {
        arrivals.begin("qualcomm")
        arrivals.delayFor("a", 0)
        arrivals.delayFor("b", 1)

        assertNull(arrivals.delayFor("b", 0))
        assertNull(arrivals.delayFor("a", 5))
    }

    @Test
    fun `the same phrase begun again forgets nothing`() {
        arrivals.begin("qualcomm")
        arrivals.delayFor("a", 0)

        arrivals.begin("qualcomm")

        assertNull(arrivals.delayFor("a", 0))
    }

    @Test
    fun `a new phrase starts afresh`() {
        arrivals.begin("qualcomm")
        arrivals.delayFor("a", 0)

        arrivals.begin("hackathon")

        assertEquals(0L, arrivals.delayFor("a", 0))
    }
}
