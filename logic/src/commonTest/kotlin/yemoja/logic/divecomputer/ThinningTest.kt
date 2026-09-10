package yemoja.logic.divecomputer

import kotlin.test.Test
import kotlin.test.assertEquals

/*
 * Dropping the points a recording does not need, and what a tolerance of zero means. `LOGIC-15`.
 *
 * See ../../../../../../doc.md — the mapping is logic/divecomputer.md.
 */

private fun at(vararg values: Pair<Int, Double>): List<Pair<Int, Double>> = values.toList()

class ThinningTest {

    @Test
    fun `at zero the middle of three equal readings goes, costing nothing`() {
        val kept = thinned(at(0 to 5.0, 10 to 5.0, 20 to 5.0), 0.0)
        assertEquals(at(0 to 5.0, 20 to 5.0), kept)
    }

    @Test
    fun `at zero a run keeps both ends, so the step after it is still a step`() {
        // Keeping only the first of the run would be read as a ramp from 5 to 9 across the whole
        // run, since a series is read as the line between the points kept.
        val kept = thinned(at(0 to 5.0, 10 to 5.0, 20 to 5.0, 30 to 9.0), 0.0)
        assertEquals(at(0 to 5.0, 20 to 5.0, 30 to 9.0), kept)
    }

    @Test
    fun `at zero a straight run of changing readings goes too`() {
        val kept = thinned(at(0 to 0.0, 10 to 1.0, 20 to 2.0, 30 to 3.0), 0.0)
        assertEquals(at(0 to 0.0, 30 to 3.0), kept)
    }

    @Test
    fun `at zero a reading off the line by anything at all survives`() {
        val kept = thinned(at(0 to 0.0, 10 to 0.1, 20 to 0.0), 0.0)
        assertEquals(3, kept.size)
    }

    @Test
    fun `below zero is the answer that means leave it alone`() {
        val held = at(0 to 5.0, 10 to 5.0, 20 to 5.0)
        assertEquals(held, thinned(held, -1.0))
    }

    @Test
    fun `two points are already as few as a series can have`() {
        val held = at(0 to 5.0, 10 to 5.0)
        assertEquals(held, thinned(held, 0.0))
    }
}
