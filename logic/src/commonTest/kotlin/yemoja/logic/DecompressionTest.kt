package yemoja.logic

import yemoja.data.Gas
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Bühlmann ZH-L16C. See ../../../../../doc.md — the layer's own document is logic/doc.md,
 * `LOGIC-3`.
 *
 * The arithmetic is checked against what can be worked out by hand: a half-time closes half the
 * gap, the Schreiner equation composes along one line, and a ceiling is its compartment's two
 * coefficients. Where a figure is a published one it says so, and it is checked within a window
 * rather than exactly, a table's rounding and its ascent not being ours.
 */

/** An atmosphere of a round bar, so that a test's arithmetic can be followed. */
private const val SURFACE = 1.0

/** Thirty metres of sea water, near enough, which is where the limits are worth knowing. */
private const val DEEP = 4.0

/** The pressure in an aircraft's cabin, which is what a diver waits for before flying. */
private const val CABIN = 0.7565

private const val WATER_VAPOUR = 0.0627

private const val MINUTE = 60.0

/** The nitrogen of air in the lungs at [ambient] bar, which is what a compartment fills towards. */
private fun inspiredN2(ambient: Double): Double = (ambient - WATER_VAPOUR) * Gas.AIR.fractionN2

/** A mix of four fifths helium, for telling the two gases' speeds apart. */
private val HELIOX = Gas(21, 79)

private fun near(expected: Double, actual: Double, what: String = "") {
    assertEquals(expected, actual, 1e-9, what)
}

class DecompressionTest {

    @Test
    fun `tissues start settled at the pressure they have been breathing`() {
        val settled = Tissues.saturated(SURFACE)
        for (number in 1..Tissues.COMPARTMENTS) {
            near(inspiredN2(SURFACE), settled.nitrogenIn(number), "compartment $number")
            near(0.0, settled.heliumIn(number), "compartment $number")
        }
    }

    @Test
    fun `one half-time closes half the gap, and two of them three quarters`() {
        val settled = Tissues.saturated(SURFACE)
        val started = settled.nitrogenIn(1)
        val towards = inspiredN2(DEEP)

        val once = settled.breathing(Gas.AIR, DEEP, DEEP, 4 * MINUTE)
        near(started + (towards - started) / 2, once.nitrogenIn(1))

        val twice = settled.breathing(Gas.AIR, DEEP, DEEP, 8 * MINUTE)
        near(started + (towards - started) * 3 / 4, twice.nitrogenIn(1))
    }

    @Test
    fun `the slowest compartment has barely moved while the fastest has filled`() {
        val after = Tissues.saturated(SURFACE).breathing(Gas.AIR, DEEP, DEEP, 20 * MINUTE)
        val gap = inspiredN2(DEEP) - inspiredN2(SURFACE)
        val fastest = (after.nitrogenIn(1) - inspiredN2(SURFACE)) / gap
        val slowest = (after.nitrogenIn(Tissues.COMPARTMENTS) - inspiredN2(SURFACE)) / gap

        assertTrue(fastest > 0.95, "the four-minute compartment should be full, but was $fastest")
        assertTrue(slowest < 0.05, "the ten-hour compartment should be untouched, but was $slowest")
    }

    @Test
    fun `helium moves about two and a half times faster than nitrogen`() {
        val settled = Tissues.saturated(SURFACE)
        val helium = settled.breathing(HELIOX, DEEP, DEEP, 1.51 * MINUTE).heliumIn(1)
        near((DEEP - WATER_VAPOUR) * HELIOX.fractionHe / 2, helium)

        val nitrogen = settled.breathing(Gas.AIR, DEEP, DEEP, 1.51 * MINUTE).nitrogenIn(1)
        val filled = (nitrogen - settled.nitrogenIn(1)) / (inspiredN2(DEEP) - settled.nitrogenIn(1))
        near(1 - 2.0.pow(-1.51 / 4), filled)
    }

    @Test
    fun `a descent loads the same whether it is taken whole or in pieces`() {
        val settled = Tissues.saturated(SURFACE)
        val whole = settled.breathing(Gas.AIR, SURFACE, DEEP, 2 * MINUTE)

        var pieces = settled
        val steps = 240
        for (step in 1..steps) {
            val from = SURFACE + (DEEP - SURFACE) * (step - 1) / steps
            val to = SURFACE + (DEEP - SURFACE) * step.toDouble() / steps
            pieces = pieces.breathing(Gas.AIR, from, to, 2 * MINUTE / steps)
        }

        for (number in 1..Tissues.COMPARTMENTS) {
            near(whole.nitrogenIn(number), pieces.nitrogenIn(number), "compartment $number")
        }
    }

    @Test
    fun `breathing for no time at all changes nothing`() {
        val settled = Tissues.saturated(SURFACE)
        val after = settled.breathing(Gas.AIR, SURFACE, DEEP, 0.0)
        for (number in 1..Tissues.COMPARTMENTS) {
            near(settled.nitrogenIn(number), after.nitrogenIn(number), "compartment $number")
        }
    }

    @Test
    fun `the ceiling of settled tissues is the slowest compartment's, and it is below the surface`() {
        val settled = Tissues.saturated(SURFACE)
        val slowest = (settled.nitrogenIn(Tissues.COMPARTMENTS) - 0.2327) * 0.9653

        assertEquals(slowest, settled.ceiling(1.0), 1e-9)
        assertTrue(settled.ceiling(1.0) < SURFACE, "settled tissues should need no stop")
    }

    @Test
    fun `a lower gradient factor holds the ascent deeper`() {
        val loaded = Tissues.saturated(SURFACE).breathing(Gas.AIR, DEEP, DEEP, 40 * MINUTE)
        val bare = loaded.ceiling(1.0)
        val cautious = loaded.ceiling(0.3)

        assertTrue(cautious > bare, "0.3 gave $cautious against the bare limit's $bare")
        assertTrue(bare > SURFACE, "forty minutes at $DEEP bar should require a stop")
    }

    @Test
    fun `the ceiling never rises while the diver is going down`() {
        var tissues = Tissues.saturated(SURFACE)
        var deepest = tissues.ceiling(0.85)
        for (step in 1..30) {
            tissues = tissues.breathing(Gas.AIR, SURFACE + step * 0.1, SURFACE + step * 0.1, MINUTE)
            val ceiling = tissues.ceiling(0.85)
            assertTrue(ceiling >= deepest, "the ceiling rose to $ceiling at step $step")
            deepest = ceiling
        }
    }

    @Test
    fun `the no-decompression limit at thirty metres is what the tables say it is`() {
        val limit = Tissues.saturated(SURFACE)
            .noDecompressionSeconds(Gas.AIR, DEEP, SURFACE, 1.0)

        assertNotNull(limit, "thirty metres on air has a limit")
        val minutes = limit / MINUTE
        assertTrue(minutes in 14.0..19.0, "published tables put it near 17 minutes, not $minutes")
    }

    @Test
    fun `the limit shortens with depth, and a shallow enough dive has none`() {
        val settled = Tissues.saturated(SURFACE)
        val shallower = settled.noDecompressionSeconds(Gas.AIR, 3.0, SURFACE, 1.0)
        val deeper = settled.noDecompressionSeconds(Gas.AIR, 5.0, SURFACE, 1.0)

        assertNotNull(shallower)
        assertNotNull(deeper)
        assertTrue(deeper < shallower, "$deeper at 5 bar should be shorter than $shallower at 3")
        assertNull(
            settled.noDecompressionSeconds(Gas.AIR, 1.5, SURFACE, 1.0),
            "five metres settles below the surface's own limit",
        )
    }

    @Test
    fun `a cautious factor gives less time than the bare limit`() {
        val settled = Tissues.saturated(SURFACE)
        val bare = assertNotNull(settled.noDecompressionSeconds(Gas.AIR, DEEP, SURFACE, 1.0))
        val cautious = assertNotNull(settled.noDecompressionSeconds(Gas.AIR, DEEP, SURFACE, 0.7))

        assertTrue(cautious < bare, "0.7 gave $cautious against the bare limit's $bare")
    }

    @Test
    fun `tissues already past their limit have no time left`() {
        val loaded = Tissues.saturated(SURFACE).breathing(Gas.AIR, DEEP, DEEP, 60 * MINUTE)
        near(0.0, assertNotNull(loaded.noDecompressionSeconds(Gas.AIR, DEEP, SURFACE, 1.0)))
    }

    @Test
    fun `settled tissues may fly at once, and loaded ones wait`() {
        val settled = Tissues.saturated(SURFACE)
        val loaded = settled.breathing(Gas.AIR, DEEP, DEEP, 30 * MINUTE)

        near(0.0, assertNotNull(settled.noFlightSeconds(SURFACE, CABIN, 0.85)))
        assertTrue(
            assertNotNull(loaded.noFlightSeconds(SURFACE, CABIN, 0.85)) > 0,
            "half an hour at thirty metres is not a wait of nothing",
        )
        assertTrue(
            assertNotNull(loaded.noFlightSeconds(SURFACE, CABIN, 0.85)) <
                assertNotNull(loaded.noFlightSeconds(SURFACE, CABIN, 0.5)),
            "a cautious factor waits longer",
        )
    }

    @Test
    fun `tissues come back to where they started, and say how long it takes`() {
        val settled = Tissues.saturated(SURFACE)
        val loaded = settled.breathing(Gas.AIR, DEEP, DEEP, 30 * MINUTE)
        val waited = assertNotNull(loaded.desaturationSeconds(SURFACE))

        near(0.0, assertNotNull(settled.desaturationSeconds(SURFACE)))
        assertTrue(waited > 0, "something was taken on and has to come off")
        val after = loaded.breathing(Gas.AIR, SURFACE, SURFACE, waited)
        for (number in 1..Tissues.COMPARTMENTS) {
            assertTrue(
                after.nitrogenIn(number) - settled.nitrogenIn(number) <= 0.01,
                "compartment $number is still ${after.nitrogenIn(number)}",
            )
        }
    }

    @Test
    fun `the factor slides from the first stop to the surface`() {
        near(0.3, gradientFactorAt(2.5, 2.5, SURFACE, 0.3, 0.8))
        near(0.8, gradientFactorAt(SURFACE, 2.5, SURFACE, 0.3, 0.8))
        near(0.55, gradientFactorAt(1.75, 2.5, SURFACE, 0.3, 0.8))
    }

    @Test
    fun `below the first stop the factor is the low one, and with no stop the high one`() {
        near(0.3, gradientFactorAt(4.0, 2.5, SURFACE, 0.3, 0.8))
        near(0.8, gradientFactorAt(2.0, SURFACE, SURFACE, 0.3, 0.8))
        near(0.8, gradientFactorAt(SURFACE, 0.5, SURFACE, 0.3, 0.8))
    }

    @Test
    fun `what cannot be asked for says what was expected`() {
        val settled = Tissues.saturated(SURFACE)

        assertEquals(
            "compartment should be 1 to 16, but was 0",
            assertFailsWith<IllegalArgumentException> { settled.nitrogenIn(0) }.message,
        )
        assertEquals(
            "compartment should be 1 to 16, but was 17",
            assertFailsWith<IllegalArgumentException> { settled.heliumIn(17) }.message,
        )
        assertEquals(
            "gradient factor should be 0 to 1, but was 1.2",
            assertFailsWith<IllegalArgumentException> { settled.ceiling(1.2) }.message,
        )
        assertEquals(
            "seconds should be 0 or more, but was -1.0",
            assertFailsWith<IllegalArgumentException> {
                settled.breathing(Gas.AIR, SURFACE, SURFACE, -1.0)
            }.message,
        )
    }
}
