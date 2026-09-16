package yemoja.logic

import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/*
 * The oxygen clocks. See ../../../../../doc.md — the layer's own document is logic/doc.md,
 * `LOGIC-38`.
 *
 * The published figures are what these check against: 45 minutes at 1.6 bar and 300 at 1.0 are the
 * whole of the central nervous system's single exposure, and a minute at 1.0 bar is one unit.
 */

private const val MINUTE = 60.0

/** A steady exposure, which is what every figure in the tables describes. */
private fun steady(partialPressure: Double, minutes: Double): OxygenClock =
    OxygenClock.CLEAR.breathing(partialPressure, partialPressure, minutes * MINUTE)

private fun near(expected: Double, actual: Double, what: String = "") {
    assertEquals(expected, actual, 1e-9, what)
}

class OxygenTest {

    @Test
    fun `a published limit breathed to the end is the whole clock`() {
        near(100.0, steady(1.6, 45.0).percentCns, "45 minutes at 1.6")
        near(100.0, steady(1.0, 300.0).percentCns, "300 minutes at 1.0")
        near(100.0, steady(0.6, 720.0).percentCns, "720 minutes at 0.6")
    }

    @Test
    fun `between two published pressures the limit is read straight across`() {
        // 1.0 bar allows 300 minutes and 1.1 allows 240, so 1.05 allows 270.
        near(10.0, steady(1.05, 27.0).percentCns)
    }

    @Test
    fun `past the richest the table says, the rate stops falling rather than being invented`() {
        near(steady(1.6, 45.0).percentCns, steady(1.8, 45.0).percentCns)
    }

    @Test
    fun `little enough oxygen spends nothing, and gives back what was spent`() {
        near(0.0, steady(0.5, 600.0).percentCns)
        val spent = steady(1.4, 150.0)
        near(100.0, spent.percentCns)

        val recovered = spent.breathing(0.21, 0.21, 90 * MINUTE)
        near(50.0, recovered.percentCns, "ninety minutes gives back half")
        near(25.0, recovered.breathing(0.21, 0.21, 90 * MINUTE).percentCns)
    }

    @Test
    fun `a minute at one bar is a unit, and half a bar is none`() {
        near(60.0, steady(1.0, 60.0).otu)
        near(0.0, steady(0.5, 600.0).otu)
        near(30 * 2.0.pow(5.0 / 6.0), steady(1.5, 30.0).otu)
    }

    @Test
    fun `a clock at rest is a clock at rest`() {
        val spent = steady(1.4, 30.0)
        val again = spent.breathing(1.4, 1.4, 0.0)

        near(spent.percentCns, again.percentCns)
        near(spent.otu, again.otu)
    }

    @Test
    fun `a changing pressure is taken at its average`() {
        val across = OxygenClock.CLEAR.breathing(0.6, 1.4, 10 * MINUTE)
        val middle = steady(1.0, 10.0)

        near(middle.percentCns, across.percentCns)
        near(middle.otu, across.otu)
    }

    @Test
    fun `time that runs backwards says what was expected`() {
        assertEquals(
            "seconds should be 0 or more, but was -1.0",
            assertFailsWith<IllegalArgumentException> {
                OxygenClock.CLEAR.breathing(1.0, 1.0, -1.0)
            }.message,
        )
    }

    @Test
    fun `what it holds is what it says it holds`() {
        assertTrue("CNS 100%" in steady(1.6, 45.0).toString(), steady(1.6, 45.0).toString())
        assertTrue("OTU 60" in steady(1.0, 60.0).toString(), steady(1.0, 60.0).toString())
    }
}
