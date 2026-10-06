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

/** What [this] keeps of [key] at the end, in litres, and nought where it keeps nothing. */
private fun Reserve.Done.litres(key: String): Double = kept[key]?.litres ?: 0.0

private fun lost(run: Run, lost: Set<String> = setOf("g2")): Reserve.Done =
    assertIs<Reserve.Done>(lostGasReserve(run, lost, 9.0, 3.0))

private fun shared(run: Run, deco: Set<String> = setOf("g2"), stress: Double = 2.0): Reserve.Done =
    assertIs<Reserve.Done>(sharedGasReserve(run, deco, stress, 9.0, 3.0))

class LostGasReserveTest {

    @Test
    fun `losing the deco gas is worst where the plan would have switched to it`() {
        // From there the plan breathes no more air, and the way up without the deco gas breathes
        // nothing else, so every litre of it is extra.
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val air = lost(run).kept.getValue("g1")

        assertEquals(run.switches.single { it.second == "g2" }.first, air.second)
        assertEquals(0.0, air.upTo, "the way up is to the surface")
        assertEquals(air.needed, air.litres, 1e-6, "all of it kept")
    }

    @Test
    fun `the way up the reserve is costed on is kept, from its worst moment to the surface`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val done = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 120))
        val air = done.kept.getValue("g1")

        assertEquals(air.second to air.metres, done.escape.first(), "it starts where the reserve is worst")
        assertEquals(air.second + 120 to air.metres, done.escape[1], "and holds there first")
        assertEquals(0.0, done.escape.last().second, "and surfaces")
        assertTrue(done.escape.last().first > run.depth.last().first, "later than the plan, on bottom gas alone")
        assertTrue(lost(whole(18.0, 30, AIR_ONLY), lost = emptySet()).escape.isEmpty(), "nothing kept, nothing drawn")
    }

    @Test
    fun `a lost gas leaving nothing breathable at depth is said, on the leanest gas left`() {
        // At fifty metres EAN32 is 1.92 bar against 1.6, and losing the trimix leaves only it.
        val sources = mapOf("g1" to cylinder("TMX18/45"), "g2" to cylinder("EAN32"))
        val run = whole(50.0, 15, sources)
        val beyond = assertNotNull(lost(run, lost = setOf("g1")).beyond)

        assertEquals("g2", beyond.source)
        assertEquals(50.0, beyond.metres)
        assertTrue(beyond.oxygen > beyond.most)
        assertNull(lost(run, lost = setOf("g2")).beyond, "the trimix is breathable there")
    }

    @Test
    fun `a reserve with no rate to cost still gives the way up, the longest of them`() {
        // The way up needs no SAC: only its gas does.
        val sources = mapOf("g1" to cylinder("AIR", sac = null), "g2" to cylinder("EAN50", volume = 7.0, sac = null))
        val run = whole(40.0, 25, sources)
        val costed = whole(40.0, 25, BOTTOM_AND_DECO)
        val refused = assertIs<Reserve.Refused>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 120))
        val done = assertIs<Reserve.Done>(lostGasReserve(costed, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 120))

        assertEquals("g1", refused.source)
        assertEquals(done.escape, refused.escape, "the same way up the costed reserve draws, here the longest too")
    }

    @Test
    fun `with the deco gas lost the reserve is bottom gas alone`() {
        val done = lost(whole(40.0, 25, BOTTOM_AND_DECO))

        assertEquals(setOf("g1"), done.kept.keys)
        val air = done.kept.getValue("g1")
        assertEquals(air.litres / 12.0, assertNotNull(air.bar), 1e-9)
    }

    @Test
    fun `a deco gas kept is breathed on the way up, and saves bottom gas`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val gone = lost(run, lost = setOf("g2"))
        val kept = lost(run, lost = emptySet())

        assertTrue(kept.litres("g1") < gone.litres("g1"), "${kept.litres("g1")} against ${gone.litres("g1")}")
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
        assertTrue(done.litres("g2") > 0, "but the way up in trouble does, so all of it is kept: ${done.kept.keys}")
    }

    @Test
    fun `losing the gas being breathed starts the way up on what is left`() {
        val sources = mapOf("g1" to cylinder("EAN32"), "g2" to cylinder("AIR"))
        val done = lost(whole(30.0, 30, sources), lost = setOf("g1"))

        assertEquals(setOf("g2"), done.kept.keys)
    }

    @Test
    fun `a plan with enough gas is not short`() {
        val done = lost(whole(18.0, 20, AIR_ONLY), lost = emptySet())

        assertTrue(done.kept.values.none { it.short }, "${done.kept.values.map { it.end to it.bar }}")
        assertTrue(done.judged)
    }

    @Test
    fun `a plan short of its reserve ends with less than it keeps`() {
        val air = lost(whole(40.0, 25, BOTTOM_AND_DECO)).kept.getValue("g1")

        assertTrue(air.short, "twelve litres cannot do a forty-metre dive's stops")
        assertTrue(assertNotNull(air.end) < assertNotNull(air.bar), "${air.end} against ${air.bar}")
    }

    @Test
    fun `what is kept is the way up's cost less what the plan breathes after that moment`() {
        // Two divers sharing to the surface, so the way up costs more than the plan's own.
        val run = whole(40.0, 25, AIR_ONLY)
        val air = shared(run, deco = emptySet()).kept.getValue("g1")
        val before = Run(
            depth = run.depth.takeWhile { it.first <= air.second },
            sources = AIR_ONLY,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
        )
        fun used(of: Run): Double = assertIs<Evaluated.Done>(evaluate(of)).gasUsed.getValue("g1")
        val after = used(run) - used(before)

        assertEquals(air.needed - after, air.litres, 1e-6)
    }

    @Test
    fun `a cylinder ending with what it keeps held enough at the worst moment`() {
        // The gauge at any moment is the end pressure plus what the plan breathes after it, so a
        // cylinder ending on exactly its reserve met the worst moment's need there to the litre.
        val run = whole(40.0, 25, AIR_ONLY)
        val air = shared(run, deco = emptySet()).kept.getValue("g1")
        val gauge = assertIs<Evaluated.Done>(evaluate(run)).pressures.getValue("g1")
        val at = run.depth.indexOfFirst { it.first == air.second }
        val then = (gauge.valueAt(at) as yemoja.data.Element.Usable).value as Double

        assertEquals(air.needed / 12.0, then - assertNotNull(air.end) + assertNotNull(air.bar), 1e-6)
    }

    @Test
    fun `the way up in trouble holds the safety stop too`() {
        val without = shared(whole(18.0, 20, AIR_ONLY), deco = emptySet())
        val with = shared(whole(18.0, 20, AIR_ONLY, SafetyStop(6.0, 180)), deco = emptySet())

        assertTrue(with.kept.getValue("g1").needed > without.kept.getValue("g1").needed)
    }

    @Test
    fun `a cylinder with no size is counted and not judged`() {
        val done = shared(whole(18.0, 20, mapOf("g1" to cylinder("AIR", volume = null))), deco = emptySet())

        val air = done.kept.getValue("g1")
        assertTrue(air.litres > 0)
        assertNull(air.bar)
        assertTrue(!done.judged)
        assertTrue(!air.short)
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
    fun `the way up at a moment costs two divers' usual rate, sharing`() {
        val run = whole(40.0, 25, AIR_ONLY)
        val air = shared(run, deco = emptySet(), stress = 1.0).kept.getValue("g1")
        val upTo = Run(
            depth = run.depth.takeWhile { it.first <= air.second },
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
            litres += 2 * 20.0 * (point.first - previous.first) / 60.0 * mean
            previous = point
        }

        assertTrue(abs(litres - air.needed) < 1e-6, "$litres against ${air.needed}")
    }
}

class SharedGasReserveTest {

    @Test
    fun `two divers share as far as the depth the deco gas may be breathed from`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)
        val done = shared(run)
        val deepest = assertNotNull(maximumOperatingDepth(Gas.parse("EAN50"), 1.6, run.density, run.surface))

        assertEquals(setOf("g1"), done.kept.keys, "only the gas being shared is costed")
        val air = done.kept.getValue("g1")
        assertEquals(deepest, air.upTo, 1e-9)
        assertEquals(1500, air.second, "the end of the bottom, deepest and most loaded")
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

        val upTo = shared(early).kept.getValue("g1").upTo
        assertTrue(upTo > 20, "$upTo")
    }

    @Test
    fun `with no deco gas the two share to the surface`() {
        assertEquals(0.0, shared(whole(30.0, 20, AIR_ONLY), deco = emptySet()).kept.getValue("g1").upTo)
    }

    @Test
    fun `sharing costs in proportion to the stress, and alone the way up is the plan's`() {
        // With nothing to switch to, the shared way up is the one a diver alone would make, which
        // is the plan's own: alone, nothing beyond it is kept.
        val run = whole(30.0, 20, AIR_ONLY, SafetyStop(6.0, 180))
        val calm = shared(run, deco = emptySet(), stress = 1.0).kept.getValue("g1")
        val stressed = shared(run, deco = emptySet(), stress = 2.0).kept.getValue("g1")

        val alone = lost(run, lost = emptySet()).kept
        assertTrue(alone.isEmpty(), "alone, the way up is the plan's: ${alone.mapValues { it.value.litres }}")
        assertEquals(calm.second, stressed.second, "the same moment is the worst")
        assertEquals(calm.needed * 2, stressed.needed, 1e-6)
    }

    @Test
    fun `a dive within reach of its deco gas owes nothing to share`() {
        // EAN50 may be breathed from about twenty-two metres, so a buddy at eighteen switches at once.
        val done = shared(whole(18.0, 30, BOTTOM_AND_DECO))

        assertTrue(done.kept.isEmpty(), "${done.kept.keys}")
    }

    @Test
    fun `sharing to a deco gas asks far less than losing it`() {
        val run = whole(40.0, 25, BOTTOM_AND_DECO)

        assertTrue(shared(run).litres("g1") < lost(run).litres("g1"))
    }

    @Test
    fun `a shortfall in sharing says how far the sharing went`() {
        val sources = mapOf("g1" to cylinder("AIR", volume = 3.0), "g2" to cylinder("EAN50", volume = 7.0))
        val air = shared(whole(40.0, 25, sources)).kept.getValue("g1")

        assertTrue(air.short)
        assertTrue(air.upTo > 0, "the sharing ends at the deco gas, not the surface")
    }

    @Test
    fun `a deco gas the run does not carry is passed over`() {
        assertEquals(0.0, shared(whole(30.0, 20, AIR_ONLY), deco = setOf("g9")).kept.getValue("g1").upTo)
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
            held.litres("g1") >= prompt.litres("g1") + minute - 1e-6,
            "${held.litres("g1")} against ${prompt.litres("g1")} and a minute of $minute",
        )
    }

    @Test
    fun `a minute at depth with the deco gas lost owes stops as well as gas`() {
        val prompt = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0))
        val held = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 60))
        val minute = 20.0 * atForty

        assertTrue(
            held.litres("g1") > prompt.litres("g1") + minute,
            "a minute more on the bottom loads the tissues: ${held.litres("g1")} against ${prompt.litres("g1")}",
        )
    }

    @Test
    fun `nothing is held where nothing is shared`() {
        val shallow = whole(18.0, 30, BOTTOM_AND_DECO)
        val held = assertIs<Reserve.Done>(sharedGasReserve(shallow, setOf("g2"), 2.0, 9.0, 3.0, problemSolvingSeconds = 120))

        assertTrue(held.kept.isEmpty(), "the buddy goes on to their own deco gas at once")
    }

    @Test
    fun `no problem-solving time is the way up as it was`() {
        val prompt = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0))
        val none = assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, problemSolvingSeconds = 0))

        assertEquals(prompt.litres("g1"), none.litres("g1"))
    }
}

class SwitchStopsTest {

    private val run = whole(40.0, 25, BOTTOM_AND_DECO)

    @Test
    fun `a way up that stops to switch takes a bailout deeper`() {
        // The plan never breathes its EAN36 bailout. With the EAN50 lost the way up may, from
        // thirty-three metres, where it stops for it only when told to.
        val bailout = Source(Gas.parse("EAN36"), sac = 20.0, volume = 7.0, fill = 200.0, ascentMayChoose = false)
        val three = whole(40.0, 25, BOTTOM_AND_DECO + ("g3" to bailout))
        val passing = assertIs<Reserve.Done>(lostGasReserve(three, setOf("g2"), 9.0, 3.0))
        val stopping = assertIs<Reserve.Done>(lostGasReserve(three, setOf("g2"), 9.0, 3.0, switchStops = true))

        assertTrue(
            stopping.litres("g3") > passing.litres("g3"),
            "switched to deeper, more of it is breathed: ${stopping.litres("g3")} against ${passing.litres("g3")}",
        )
    }

    @Test
    fun `with the deco gas lost there is nothing to stop for`() {
        assertEquals(
            assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0)).kept.mapValues { it.value.litres },
            assertIs<Reserve.Done>(lostGasReserve(run, setOf("g2"), 9.0, 3.0, switchStops = true)).kept.mapValues { it.value.litres },
        )
    }
}

class BailoutReserveTest {

    /** Thirty minutes at forty metres on a loop of air at 0.7 and 1.3, with [sources] beside the diluent, its way up added. */
    private fun loop(sources: Map<String, Source>): Run {
        val all = mapOf("g1" to Source(Gas.AIR)) + sources
        val circuit = ClosedCircuit("g1", 0.7, 1.3, 6.0)
        val bottom = Run(
            depth = listOf(0 to 0.0, 134 to 40.0, 1800 to 40.0),
            sources = all,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
            closedCircuit = circuit,
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
        return Run(
            depth = bottom.depth + ascent.depth,
            sources = all,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = bottom.switches,
            closedCircuit = circuit,
        )
    }

    private fun bailout(gas: String, volume: Double = 11.0) =
        Source(Gas.parse(gas), sac = 20.0, volume = volume, fill = 200.0, ascentMayChoose = false)

    @Test
    fun `only the bailouts are breathed, never the diluent or a deco gas`() {
        val run = loop(mapOf("g2" to bailout("AIR"), "g3" to cylinder("EAN50", volume = 7.0)))
        val done = assertIs<Reserve.Done>(bailoutReserve(run, 9.0, 3.0))

        assertEquals(setOf("g2"), done.kept.keys)
        assertTrue(done.judged)
        assertEquals(200.0, done.kept.getValue("g2").end, "the loop breathes no bailout, so it ends as filled")
    }

    @Test
    fun `the way up switches among the bailouts, and a rich one saves the other`() {
        val alone = assertIs<Reserve.Done>(bailoutReserve(loop(mapOf("g2" to bailout("AIR"))), 9.0, 3.0))
        val both = assertIs<Reserve.Done>(
            bailoutReserve(loop(mapOf("g2" to bailout("AIR"), "g3" to bailout("EAN50", 7.0))), 9.0, 3.0),
        )

        assertTrue(both.kept.getValue("g3").litres > 0, "${both.kept.keys}")
        assertTrue(both.kept.getValue("g2").litres < alone.kept.getValue("g2").litres)
    }

    @Test
    fun `the worst moment is the end of the bottom`() {
        val prompt = assertIs<Reserve.Done>(bailoutReserve(loop(mapOf("g2" to bailout("AIR"))), 9.0, 3.0)).kept.getValue("g2")

        assertEquals(1800, prompt.second)
        assertEquals(40.0, prompt.metres)
    }

    @Test
    fun `a CO2 hit is breathed at its factor at depth, and the way up after it at the usual rate`() {
        // Ten minutes at forty metres at four times twenty litres a minute, and nothing else changed
        // but the stops those ten minutes owe.
        val run = loop(mapOf("g2" to bailout("AIR", volume = 24.0)))
        fun kept(seconds: Int, factor: Double): Kept =
            assertIs<Reserve.Done>(bailoutReserve(run, 9.0, 3.0, co2HitSeconds = seconds, co2HitFactor = factor)).kept.getValue("g2")
        val atForty = ambientAt(40.0, NOMINAL_DENSITY, SEA_LEVEL)
        val calm = kept(600, 1.0)
        val hit = kept(600, 4.0)

        assertEquals(3 * 20.0 * 10 * atForty, hit.needed - calm.needed, 1e-6, "only the time held costs the factor")
        assertTrue(calm.needed > kept(0, 1.0).needed + 20.0 * 10 * atForty, "the ten minutes load the tissues as well")
    }

    @Test
    fun `a bailout is tried where it becomes breathable on the way up, not only at the plan's points`() {
        // Thirty minutes at thirty metres rises straight past the depth EAN50 comes within 1.4 bar,
        // and failing just there is the dearest moment for it: from there it is breathed all the way.
        val ean50 = Source(Gas.parse("EAN50"), sac = 20.0, volume = 11.0, fill = 200.0, mostOxygen = 1.4, ascentMayChoose = false)
        val sources = mapOf(
            "g1" to Source(Gas.AIR),
            "g2" to Source(Gas.AIR, sac = 20.0, volume = 24.0, fill = 232.0, mostOxygen = 1.4, ascentMayChoose = false),
            "g3" to ean50,
        )
        val bottom = Run(
            depth = listOf(0 to 0.0, 100 to 30.0, 1900 to 30.0),
            sources = sources,
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
            closedCircuit = ClosedCircuit("g1", 0.7, 1.3, 6.0),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(bottom, 9.0, 3.0))
        val run = Run(bottom.depth + ascent.depth, sources, 0.3, 0.7, bottom.switches, closedCircuit = bottom.closedCircuit)
        val limit = assertNotNull(maximumOperatingDepth(ean50.gas, ean50.mostOxygen, run.density, run.surface))
        val worst = assertIs<Reserve.Done>(bailoutReserve(run, 9.0, 3.0)).kept.getValue("g3")

        assertTrue(run.depth.none { it.second == worst.metres }, "not one of the plan's own depths: ${worst.metres}")
        assertEquals(limit, worst.metres, 0.2, "the depth EAN50 becomes breathable")
        assertTrue(worst.metres <= limit, "on the side it may be breathed: ${worst.metres} against $limit")
    }

    @Test
    fun `the points added where a limit is crossed change nothing the plan breathes`() {
        val run = loop(mapOf("g2" to bailout("AIR", 24.0), "g3" to bailout("EAN50", 11.0)))
        val sampled = run.sampledAtLimits()
        fun done(of: Run): Evaluated.Done = assertIs<Evaluated.Done>(evaluate(of))

        assertTrue(sampled.depth.size > run.depth.size)
        for (number in 1..Tissues.COMPARTMENTS) {
            assertEquals(done(run).surfacing.nitrogenIn(number), done(sampled).surfacing.nitrogenIn(number), 1e-9)
        }
        assertEquals(done(run).oxygen.percentCns, done(sampled).oxygen.percentCns, 1e-9)
    }

    @Test
    fun `a way up with nothing breathable says where, on the leanest it is costed on`() {
        // At sixty metres TMX21/35 is 1.48 bar against 1.4, and EAN50 further still.
        val deep = Run(
            depth = listOf(0 to 0.0, 200 to 60.0, 1200 to 60.0),
            sources = mapOf(
                "g1" to Source(Gas.parse("TMX10/50")),
                "g2" to Source(Gas.parse("TMX21/35"), sac = 20.0, volume = 24.0, fill = 232.0, mostOxygen = 1.4, ascentMayChoose = false),
                "g3" to Source(Gas.parse("EAN50"), sac = 20.0, volume = 11.0, fill = 200.0, mostOxygen = 1.4, ascentMayChoose = false),
            ),
            gradientFactorLow = 0.3,
            gradientFactorHigh = 0.7,
            switches = listOf(0 to "g1"),
            closedCircuit = ClosedCircuit("g1", 0.7, 1.3, 6.0),
        )
        val ascent = assertIs<Ascended.Done>(completeAscent(deep, 9.0, 3.0))
        val whole = Run(deep.depth + ascent.depth, deep.sources, 0.3, 0.7, deep.switches, closedCircuit = deep.closedCircuit)
        val beyond = assertNotNull(assertIs<Reserve.Done>(bailoutReserve(whole, 9.0, 3.0)).beyond)

        assertEquals("g2", beyond.source, "the leanest")
        assertEquals(60.0, beyond.metres)
        assertTrue(beyond.oxygen > beyond.most, "${beyond.oxygen} against ${beyond.most}")
        assertNull(assertIs<Reserve.Done>(bailoutReserve(loop(mapOf("g2" to bailout("AIR"))), 9.0, 3.0)).beyond, "air at forty is within 1.6")
    }

    @Test
    fun `a bailout too small is short, and none at all or open circuit is refused`() {
        val small = assertIs<Reserve.Done>(bailoutReserve(loop(mapOf("g2" to bailout("AIR", volume = 3.0))), 9.0, 3.0))

        assertTrue(small.kept.getValue("g2").short)
        assertEquals(
            "No bailout: give a cylinder the bailout role",
            assertIs<Reserve.Refused>(bailoutReserve(loop(emptyMap()), 9.0, 3.0)).reason,
        )
        assertIs<Reserve.Refused>(bailoutReserve(whole(30.0, 20, AIR_ONLY), 9.0, 3.0))
    }
}
