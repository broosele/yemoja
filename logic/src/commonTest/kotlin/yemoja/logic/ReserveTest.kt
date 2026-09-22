package yemoja.logic

import yemoja.data.Gas
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * The gas a plan keeps back for a way up when something goes wrong. See ../../../../../doc.md —
 * `LOGIC-40`.
 *
 * Sea water at sea level, as a table assumes.
 */

/** A dive to [metres], leaving the bottom at [minutes], on [sources], with its way up added. */
private fun whole(
    metres: Double,
    minutes: Int,
    sources: Map<String, Source>,
    safetyStop: SafetyStop? = null,
): Run {
    val bottom = Run(
        depth = listOf(0 to 0.0, (metres / 18.0 * 60).toInt() + 1 to metres, minutes * 60 to metres),
        sources = sources,
        gradientFactorLow = 0.3,
        gradientFactorHigh = 0.7,
        switches = listOf(0 to "g1"),
        safetyStop = safetyStop,
    )
    val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
    return Run(
        depth = bottom.depth + ascent.depth,
        sources = sources,
        gradientFactorLow = bottom.gradientFactorLow,
        gradientFactorHigh = bottom.gradientFactorHigh,
        switches = bottom.switches + ascent.switches,
        safetyStop = safetyStop,
    )
}

private fun cylinder(gas: String, volume: Double? = 12.0, sac: Double? = 20.0, fill: Double? = 200.0) =
    Source(Gas.parse(gas), sac = sac, volume = volume, fill = fill)

/** Air on the bottom and EAN50 to decompress on. */
private val BOTTOM_AND_DECO = mapOf("g1" to cylinder("AIR"), "g2" to cylinder("EAN50", volume = 7.0))

private val AIR_ONLY = mapOf("g1" to cylinder("AIR"))

private fun lost(run: Run, lost: Set<String> = setOf("g2")): Reserve.Done =
    assertIs<Reserve.Done>(lostGasReserve(run, lost, 9.0, 3.0))

private fun shared(run: Run, deco: Set<String> = setOf("g2"), stress: Double = 2.0): Reserve.Done =
    assertIs<Reserve.Done>(sharedGasReserve(run, deco, stress, 9.0, 3.0))

class LostGasReserveTest {

    @Test
    fun `losing the deco gas is worst at the end of the bottom`() {
        val done = lost(whole(40.0, 25, BOTTOM_AND_DECO))

        assertEquals(1500, done.worst, "the last moment on the bottom, most loaded and deepest")
        assertEquals(40.0, done.worstMetres)
        assertEquals(0.0, done.upTo, "the way up is to the surface")
    }

    @Test
    fun `with the deco gas lost the reserve is bottom gas alone`() {
        val done = lost(whole(40.0, 25, BOTTOM_AND_DECO))

        assertEquals(setOf("g1"), done.needed.keys)
        assertEquals(done.needed.getValue("g1") / 12.0, done.reserve.getValue("g1"), 1e-9)
    }

    @Test
    fun `a deco gas kept is breathed on the way up, and saves bottom gas`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val gone = lost(run, lost = setOf("g2"))
        val kept = lost(run, lost = emptySet())

        assertTrue((kept.needed["g2"] ?: 0.0) > 0, "${kept.needed}")
        assertTrue(kept.needed.getValue("g1") < gone.needed.getValue("g1"))
    }

    @Test
    fun `a bailout is open to the way up a reserve is costed on`() {
        val sources = mapOf(
            "g1" to cylinder("AIR"),
            "g2" to Source(Gas.parse("EAN50"), sac = 20.0, volume = 7.0, fill = 200.0, ascentMayChoose = false),
        )
        val run = whole(40.0, 25, sources)
        val done = lost(run, lost = emptySet())

        assertTrue(run.switches.none { it.second == "g2" }, "the plan itself never breathes it")
        assertTrue((done.needed["g2"] ?: 0.0) > 0, "but the way up in trouble does: ${done.needed}")
    }

    @Test
    fun `losing the gas being breathed starts the way up on what is left`() {
        val sources = mapOf("g1" to cylinder("EAN32"), "g2" to cylinder("AIR"))
        val done = lost(whole(30.0, 30, sources), lost = setOf("g1"))

        assertEquals(setOf("g2"), done.needed.keys)
    }

    @Test
    fun `a plan with enough gas has no shortfall`() {
        val done = lost(whole(18.0, 20, AIR_ONLY), lost = emptySet())

        assertNull(done.shortfall, "${done.shortfall?.left} against ${done.shortfall?.needed}")
        assertTrue(done.judged)
    }

    @Test
    fun `a plan short of its reserve says where and which cylinder`() {
        val done = lost(whole(40.0, 25, BOTTOM_AND_DECO))
        val short = assertNotNull(done.shortfall, "twelve litres cannot do a forty-metre dive's stops")

        assertEquals("g1", short.source)
        assertTrue(short.left < short.needed, "${short.left} against ${short.needed}")
        assertTrue(short.second <= done.worst, "it runs short no later than the worst moment")
    }

    @Test
    fun `the way up in trouble holds the safety stop too`() {
        val without = lost(whole(18.0, 20, AIR_ONLY), lost = emptySet())
        val with = lost(whole(18.0, 20, AIR_ONLY, SafetyStop(6.0, 180)), lost = emptySet())

        assertTrue(with.needed.getValue("g1") > without.needed.getValue("g1"))
    }

    @Test
    fun `a cylinder with no size is counted and not judged`() {
        val done = lost(whole(18.0, 20, mapOf("g1" to cylinder("AIR", volume = null))), lost = emptySet())

        assertTrue(done.needed.getValue("g1") > 0)
        assertTrue("g1" !in done.reserve)
        assertTrue(!done.judged)
        assertNull(done.shortfall)
    }

    @Test
    fun `a cylinder nobody gave a rate is refused by name`() {
        val refused = assertIs<Reserve.Refused>(
            lostGasReserve(whole(18.0, 20, mapOf("g1" to cylinder("AIR", sac = null))), emptySet(), 9.0, 3.0),
        )

        assertEquals("g1", refused.source)
        assertTrue("SAC missing" in refused.reason, refused.reason)
    }

    @Test
    fun `losing every cylinder leaves nothing to count`() {
        val refused = assertIs<Reserve.Refused>(
            lostGasReserve(whole(18.0, 20, AIR_ONLY), setOf("g1"), 9.0, 3.0),
        )

        assertTrue("At least one gas should remain" in refused.reason, refused.reason)
    }

    @Test
    fun `the reserve at a moment is what the way up from there costs at the usual rate`() {
        val run = whole(40.0, 25, AIR_ONLY)
        val done = lost(run, lost = emptySet())
        val upTo = Run(
            depth = run.depth.takeWhile { it.first <= done.worst },
            sources = AIR_ONLY,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(upTo, 9.0, 3.0))
        var litres = 0.0
        var previous = upTo.depth.last()
        for (point in ascent.depth) {
            val mean = (ambientAt(previous.second, upTo.density, upTo.surface) +
                ambientAt(point.second, upTo.density, upTo.surface)) / 2
            litres += 20.0 * (point.first - previous.first) / 60.0 * mean
            previous = point
        }

        assertTrue(abs(litres - done.needed.getValue("g1")) < 1e-6, "$litres against ${done.needed}")
    }
}

class SharedGasReserveTest {

    @Test
    fun `two divers share as far as the depth the deco gas may be breathed from`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val done = shared(run)
        val deepest = assertNotNull(maximumOperatingDepth(Gas.parse("EAN50"), 1.6, run.density, run.surface))

        assertEquals(deepest, done.upTo, 1e-9)
        assertEquals(1500, done.worst, "the end of the bottom, deepest and most loaded")
        assertEquals(setOf("g1"), done.needed.keys, "only the gas being shared is costed")
    }

    @Test
    fun `a way up owing no stop still hands over at the deco gas, not the surface`() {
        // Early on the bottom nothing is owed, so the way up rises straight through the depth the
        // deco gas may be breathed from. The sharing ends there all the same.
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val early = Run(
            depth = run.depth.takeWhile { it.first <= 134 },
            sources = BOTTOM_AND_DECO,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
        )

        assertTrue(shared(early).upTo > 20, "${shared(early).upTo}")
    }

    @Test
    fun `with no deco gas the two share to the surface`() {
        assertEquals(0.0, shared(whole(30.0, 20, AIR_ONLY), deco = emptySet()).upTo)
    }

    @Test
    fun `sharing costs twice the way up one diver makes, times the stress`() {
        // With nothing to switch to, the shared way up is the one a diver alone would make.
        val run = whole(30.0, 20, AIR_ONLY, SafetyStop(6.0, 180))
        val alone = lost(run, lost = emptySet())
        val calm = shared(run, deco = emptySet(), stress = 1.0)
        val stressed = shared(run, deco = emptySet(), stress = 2.0)

        assertEquals(alone.needed.getValue("g1") * 2, calm.needed.getValue("g1"), 1e-6)
        assertEquals(calm.needed.getValue("g1") * 2, stressed.needed.getValue("g1"), 1e-6)
    }

    @Test
    fun `a dive within reach of its deco gas owes nothing to share`() {
        // EAN50 may be breathed from about twenty-two metres, so a buddy at eighteen switches at once.
        val done = shared(whole(18.0, 30, BOTTOM_AND_DECO))

        assertEquals(0.0, done.needed.values.sum())
        assertNull(done.shortfall)
    }

    @Test
    fun `sharing to a deco gas asks far less than losing it`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)

        assertTrue(shared(run).needed.getValue("g1") < lost(run).needed.getValue("g1"))
    }

    @Test
    fun `a shortfall in sharing says how far the sharing went`() {
        val sources = mapOf("g1" to cylinder("AIR", volume = 3.0), "g2" to cylinder("EAN50", volume = 7.0))
        val short = assertNotNull(shared(whole(40.0, 25, sources)).shortfall)

        assertEquals("g1", short.source)
        assertTrue(short.upTo > 0, "the sharing ends at the deco gas, not the surface")
    }

    @Test
    fun `a deco gas the run does not carry is passed over`() {
        assertEquals(0.0, shared(whole(30.0, 20, AIR_ONLY), deco = setOf("g9")).upTo)
    }
}

class ProblemSolvingTest {

    private val run = whole(40.0, 25, BOTTOM_AND_DECO)
    private val atForty = ambientAt(40.0, NOMINAL_DENSITY, SEA_LEVEL)

    @Test
    fun `a minute sharing at the bottom costs two divers' stressed rate there, at least`() {
        val prompt = assertIs<Reserve.Done>(sharedGasReserve(run, setOf("g2"), 2.0, 9.0, 3.0))
        val held = assertIs<Reserve.Done>(sharedGasReserve(run, setOf("g2"), 2.0, 9.0, 3.0, problemSolvingSeconds = 60))
        val minute = 20.0 * 2 * 2 * atForty

        assertTrue(
            held.needed.getValue("g1") >= prompt.needed.getValue("g1") + minute - 1e-6,
            "${held.needed} against ${prompt.needed} and a minute of $minute",
        )
    }

    @Test
    fun `a minute at depth with the deco gas lost owes stops as well as gas`() {
        val prompt = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0))
        val held = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 60))
        val minute = 20.0 * atForty

        assertTrue(
            held.needed.getValue("g1") > prompt.needed.getValue("g1") + minute,
            "a minute more on the bottom loads the tissues: ${held.needed} against ${prompt.needed}",
        )
    }

    @Test
    fun `nothing is held where nothing is shared`() {
        val shallow = whole(18.0, 30, BOTTOM_AND_DECO)
        val held = assertIs<Reserve.Done>(sharedGasReserve(shallow, setOf("g2"), 2.0, 9.0, 3.0, problemSolvingSeconds = 120))

        assertEquals(0.0, held.needed.values.sum(), "the buddy goes on to their own deco gas at once")
    }

    @Test
    fun `no problem-solving time is the way up as it was`() {
        val prompt = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0))
        val none = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 0))

        assertEquals(prompt.needed, none.needed)
    }
}

