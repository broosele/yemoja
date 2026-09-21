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
 * The gas a plan keeps back for a way up when something is lost. See ../../../../../doc.md —
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

/** Air on the bottom and EAN50 to decompress on, which is what the rule is written for. */
private val BOTTOM_AND_DECO = mapOf("g1" to cylinder("AIR"), "g2" to cylinder("EAN50", volume = 7.0))

private fun reserve(run: Run, factor: Double = 4.0, lost: Set<String> = setOf("g2")): Reserve.Done =
    assertIs<Reserve.Done>(gasReserve(run, factor, lost, 9.0, 3.0))

class ReserveTest {

    @Test
    fun `losing the deco gas is worst at the end of the bottom`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val done = reserve(run)

        assertEquals(1500, done.worst, "the last moment on the bottom, most loaded and deepest")
        assertEquals(40.0, done.worstMetres)
    }

    @Test
    fun `with the deco gas lost the reserve is bottom gas alone`() {
        val done = reserve(whole(40.0, 25, BOTTOM_AND_DECO))

        assertEquals(setOf("g1"), done.needed.keys)
        assertEquals(done.needed.getValue("g1") / 12.0, done.reserve.getValue("g1"), 1e-9)
    }

    @Test
    fun `the panic factor scales the gas and not the way up`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val four = reserve(run, factor = 4.0)
        val two = reserve(run, factor = 2.0)

        assertEquals(four.worst, two.worst)
        assertEquals(two.needed.getValue("g1") * 2, four.needed.getValue("g1"), 1e-6)
    }

    @Test
    fun `a deco gas kept is breathed on the way up, and saves bottom gas`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val lost = reserve(run, lost = setOf("g2"))
        val kept = reserve(run, lost = emptySet())

        assertTrue((kept.needed["g2"] ?: 0.0) > 0, "${kept.needed}")
        assertTrue(kept.needed.getValue("g1") < lost.needed.getValue("g1"))
    }

    @Test
    fun `a bailout is open to the way up a reserve is costed on`() {
        val sources = mapOf(
            "g1" to cylinder("AIR"),
            "g2" to Source(Gas.parse("EAN50"), sac = 20.0, volume = 7.0, fill = 200.0, ascentMayChoose = false),
        )
        val run = whole(40.0, 25, sources)
        val done = reserve(run, lost = emptySet())

        assertTrue(run.switches.none { it.second == "g2" }, "the plan itself never breathes it")
        assertTrue((done.needed["g2"] ?: 0.0) > 0, "but the way up in trouble does: ${done.needed}")
    }

    @Test
    fun `losing the gas being breathed starts the way up on what is left`() {
        val sources = mapOf("g1" to cylinder("EAN32"), "g2" to cylinder("AIR"))
        val done = reserve(whole(30.0, 30, sources), lost = setOf("g1"))

        assertEquals(setOf("g2"), done.needed.keys)
    }

    @Test
    fun `a plan with enough gas has no shortfall`() {
        val done = reserve(whole(18.0, 20, mapOf("g1" to cylinder("AIR"))), lost = emptySet())

        assertNull(done.shortfall, "${done.shortfall?.left} against ${done.shortfall?.needed}")
        assertTrue(done.judged)
    }

    @Test
    fun `a plan short of its reserve says where and which cylinder`() {
        val done = reserve(whole(40.0, 25, BOTTOM_AND_DECO))
        val short = assertNotNull(done.shortfall, "twelve litres cannot bring a stressed pair up from forty")

        assertEquals("g1", short.source)
        assertTrue(short.left < short.needed, "${short.left} against ${short.needed}")
        assertTrue(short.second <= done.worst, "it runs short no later than the worst moment")
    }

    @Test
    fun `the way up in trouble holds the safety stop too`() {
        val sources = mapOf("g1" to cylinder("AIR"))
        val without = reserve(whole(18.0, 20, sources), lost = emptySet())
        val with = reserve(whole(18.0, 20, sources, SafetyStop(6.0, 180)), lost = emptySet())

        assertTrue(with.needed.getValue("g1") > without.needed.getValue("g1"))
    }

    @Test
    fun `a cylinder with no size is counted and not judged`() {
        val done = reserve(
            whole(18.0, 20, mapOf("g1" to cylinder("AIR", volume = null))),
            lost = emptySet(),
        )

        assertTrue(done.needed.getValue("g1") > 0)
        assertTrue("g1" !in done.reserve)
        assertTrue(!done.judged)
        assertNull(done.shortfall)
    }

    @Test
    fun `a cylinder nobody gave a rate is refused by name`() {
        val refused = assertIs<Reserve.Refused>(
            gasReserve(whole(18.0, 20, mapOf("g1" to cylinder("AIR", sac = null))), 4.0, emptySet(), 9.0, 3.0),
        )

        assertEquals("g1", refused.source)
        assertTrue("how fast" in refused.reason, refused.reason)
    }

    @Test
    fun `losing every cylinder leaves nothing to count`() {
        val refused = assertIs<Reserve.Refused>(
            gasReserve(whole(18.0, 20, mapOf("g1" to cylinder("AIR"))), 4.0, setOf("g1"), 9.0, 3.0),
        )

        assertTrue("nothing is left" in refused.reason, refused.reason)
    }

    @Test
    fun `the reserve at a moment is what the way up from there costs`() {
        // From the worst moment, the ascent the plan itself would make on bottom gas alone, costed
        // by hand at four times the rate, is the reserve.
        val sources = mapOf("g1" to cylinder("AIR"))
        val run = whole(40.0, 25, sources)
        val done = reserve(run, lost = emptySet())
        val upTo = Run(
            depth = run.depth.takeWhile { it.first <= done.worst },
            sources = sources,
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
            litres += 20.0 * 4.0 * (point.first - previous.first) / 60.0 * mean
            previous = point
        }

        assertTrue(abs(litres - done.needed.getValue("g1")) < 1e-6, "$litres against ${done.needed}")
    }
}
