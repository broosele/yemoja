package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SeriesTest {

    private fun values(vararg depths: Double): List<Element<Any>> =
        depths.map { Element.Usable(it) }

    private val dive = Series(intArrayOf(0, 30, 60), values(0.0, 8.4, 12.1))

    @Test
    fun `a series is as long as the samples it was given`() {
        assertEquals(3, dive.size)
        assertEquals(0, Series(intArrayOf(), emptyList()).size)
    }

    @Test
    fun `each half is reachable without building the other`() {
        assertEquals(30, dive.secondAt(1))
        assertEquals(Element.Usable(8.4), dive.valueAt(1))
    }

    @Test
    fun `a sample is the two halves together`() {
        assertEquals(Sample(30, Element.Usable(8.4)), dive[1])
        assertEquals(Sample(0, Element.Usable(0.0)), dive[0])
    }

    @Test
    fun `a series holds one value per time`() {
        assertFailsWith<IllegalArgumentException> {
            Series(intArrayOf(0, 30), values(0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            Series(intArrayOf(0), values(0.0, 8.4))
        }
    }

    @Test
    fun `a series runs forwards`() {
        assertFailsWith<IllegalArgumentException> {
            Series(intArrayOf(30, 0), values(8.4, 0.0))
        }
        assertFailsWith<IllegalArgumentException> {
            Series(intArrayOf(30, 30), values(8.4, 0.0))
        }
    }

    @Test
    fun `a sample may precede the start`() {
        // Nothing says it cannot, so nothing forbids it.
        assertEquals(-5, Series(intArrayOf(-5, 0), values(0.0, 1.0)).secondAt(0))
    }

    @Test
    fun `one unreadable sample leaves the rest alone`() {
        val mixed = Series(
            intArrayOf(0, 30, 60),
            listOf(
                Element.Usable(0.0),
                Element.Unusable("deep", "depth should be a number"),
                Element.Usable(12.1),
            ),
        )
        assertEquals(Element.Usable(0.0), mixed.valueAt(0))
        assertEquals(Element.Unusable("deep", "depth should be a number"), mixed.valueAt(1))
        assertEquals(Element.Usable(12.1), mixed.valueAt(2))
    }

    @Test
    fun `a series carries whatever a value may be`() {
        val alarms = Series(intArrayOf(600), listOf(Element.Usable("deco")))
        assertEquals(Element.Usable("deco"), alarms.valueAt(0))

        val switches = Series(intArrayOf(1260), listOf(Element.Usable(KeyReference("g2"))))
        assertEquals(Element.Usable(KeyReference("g2")), switches.valueAt(0))
    }

    @Test
    fun `two series holding the same samples are the same series`() {
        assertEquals(dive, Series(intArrayOf(0, 30, 60), values(0.0, 8.4, 12.1)))
        val same = Series(intArrayOf(0, 30, 60), values(0.0, 8.4, 12.1))
        assertEquals(dive.hashCode(), same.hashCode())
        assertNotEquals(dive, Series(intArrayOf(0, 30, 61), values(0.0, 8.4, 12.1)))
        assertNotEquals(dive, Series(intArrayOf(0, 30, 60), values(0.0, 8.4, 12.2)))
        assertNotEquals<Any?>(dive, null)
    }

    @Test
    fun `a series keeps its own copy of what it was given`() {
        val seconds = intArrayOf(0, 30)
        val depths = mutableListOf<Element<Any>>(Element.Usable(0.0), Element.Usable(8.4))
        val series = Series(seconds, depths)

        seconds[1] = 99
        depths[1] = Element.Usable(99.9)

        assertEquals(30, series.secondAt(1))
        assertEquals(Element.Usable(8.4), series.valueAt(1))
    }

    @Test
    fun `a series says how much of it there is rather than printing it`() {
        assertEquals("3 samples, 0 to 60 seconds", dive.toString())
        assertEquals("an empty series", Series(intArrayOf(), emptyList()).toString())
    }
}
