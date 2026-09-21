package yemoja.ui.gui

import yemoja.logic.Evaluated
import yemoja.logic.maximumOperatingDepth
import yemoja.data.Gas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A dive planned in the Calculations tab. See ../../../../../../gui/doc.md — `GUI-43`.
 */

/** A plan with [segments] typed into it, starting from the defaults, at gradient factors 20/80. */
private fun planned(vararg segments: Segment, gases: List<Breathed> = listOf(Breathed())): Shaping {
    val shaping = Shaping()
    shaping.prefill(null)
    shaping.gradientLow = "20"
    shaping.gradientHigh = "80"
    shaping.segments.clear()
    shaping.segments.addAll(segments)
    shaping.gases.clear()
    shaping.gases.addAll(gases)
    return shaping
}

private fun ready(shaping: Shaping): Shaped.Ready = assertIs<Shaped.Ready>(shapedOf(shaping))

private fun done(shaping: Shaping): Worked.Done = assertIs<Worked.Done>(workedOf(ready(shaping)))

/** Twenty-five minutes on the bottom at forty metres, typed as a descent and a stay. */
private val FORTY = arrayOf(Segment("40"), Segment("40", duration = "22:46"))

class LaidOfTest {

    @Test
    fun `a line that changes depth and says nothing else travels at the rate in the settings`() {
        val leg = ready(planned(Segment("40"))).legs.single()
        assertEquals(Direction.DOWN, leg.direction)
        // Forty metres at eighteen a minute is 133 and a third seconds, taken as 134.
        assertEquals(134, leg.seconds)
        assertEquals(18.0, leg.rate, "the rate chosen, not one worked back from the counted-up seconds")
    }

    @Test
    fun `a duration times a change of depth and the rate is worked out from it`() {
        val leg = ready(planned(Segment("40", duration = "2:00"))).legs.single()
        assertEquals(120, leg.seconds)
        assertEquals(20.0, leg.rate)
    }

    @Test
    fun `a rate times a change of depth and the duration is worked out from it`() {
        assertEquals(120, ready(planned(Segment("40", rate = "20"))).legs.single().seconds)
    }

    @Test
    fun `a line at the depth before it stays, and has a duration and no rate`() {
        val shaped = ready(planned(*FORTY))
        val stay = shaped.legs.last()
        assertEquals(Direction.STAY, stay.direction)
        assertNull(stay.rate)
        assertEquals(listOf(0 to 0.0, 134 to 40.0, 1500 to 40.0), shaped.run.depth)
    }

    @Test
    fun `a stay without a duration says so`() {
        val wrong = assertIs<Shaped.Wrong>(shapedOf(planned(Segment("40"), Segment("40"))))
        assertEquals("line 2 stays at 40 m, so it needs a duration", wrong.reason)
        assertEquals(1, wrong.legs.size, "the lines above it are still read")
    }

    @Test
    fun `a duration is minutes and seconds or whole minutes`() {
        assertEquals(133, durationOf("2:13"))
        assertEquals(1500, durationOf("25"))
        assertNull(durationOf("0"))
        assertNull(durationOf("soon"))
        val wrong = assertIs<Shaped.Wrong>(shapedOf(planned(Segment("40", duration = "soon"))))
        assertTrue("as 2:13" in wrong.reason, wrong.reason)
    }

    @Test
    fun `a line timed but not placed asks for its depth`() {
        val wrong = assertIs<Shaped.Wrong>(shapedOf(planned(Segment(duration = "5"))))
        assertEquals("line 1 needs a depth", wrong.reason)
    }

    @Test
    fun `an empty plan waits, and an empty line among others is passed over`() {
        assertIs<Shaped.Waiting>(shapedOf(planned(Segment())))
        assertEquals(2, ready(planned(FORTY[0], Segment(), FORTY[1])).legs.size)
    }

    @Test
    fun `a line breathes what the line above breathes until it names another`() {
        val legs = ready(
            planned(
                Segment("40"),
                Segment("40", duration = "20", gas = 1),
                Segment("21"),
                gases = listOf(Breathed(), Breathed("EAN32")),
            ),
        ).legs
        assertEquals(listOf(0, 1, 1), legs.map { it.gas })
        assertEquals(listOf(true, false, true), legs.map { it.inherited })
    }

    @Test
    fun `the first switch is at nought and the next where a line changes cylinder`() {
        val run = ready(
            planned(
                Segment("40"),
                Segment("40", duration = "20", gas = 1),
                gases = listOf(Breathed(), Breathed("EAN32")),
            ),
        ).run
        assertEquals(listOf(0 to "g1", 134 to "g2"), run.switches)
    }

    @Test
    fun `the runtime shown is the whole minute a line ends in, counted up`() {
        val legs = ready(planned(*FORTY)).legs
        assertEquals(listOf("3:", "25:"), legs.map { runtimeSaid(it) })
    }
}

class ConditionsOfTest {

    @Test
    fun `a plan starts from the defaults in the settings`() {
        val shaping = Shaping()
        shaping.prefill(null)
        assertEquals("1.4", shaping.bottomOxygen)
        assertEquals("1.6", shaping.decoOxygen)
        assertEquals("6", shaping.safetyDepth)
        assertEquals("3", shaping.safetyMinutes)
        assertEquals("salt", shaping.water)
        assertEquals("", shaping.gradientLow, "no conservatism is chosen for anybody")
        val wrong = assertIs<Shaped.Wrong>(shapedOf(shaping.also { it.segments[0] = Segment("18") }))
        assertTrue("low gradient factor" in wrong.reason, wrong.reason)
    }

    @Test
    fun `the safety stop and the ascent rate go on the run`() {
        val run = ready(planned(*FORTY)).run
        assertEquals(6.0, run.safetyStop?.metres)
        assertEquals(180, run.safetyStop?.seconds)
        assertEquals(9.0, run.ascentRate)
    }

    @Test
    fun `a safety stop of nought minutes is none, and its depth is not asked for`() {
        val shaping = planned(*FORTY)
        shaping.safetyMinutes = "0"
        shaping.safetyDepth = ""
        assertNull(ready(shaping).run.safetyStop)
    }

    @Test
    fun `the water is weighed as a recording in it would be`() {
        val shaping = planned(*FORTY)
        assertEquals(1030.0, ready(shaping).run.density)
        shaping.water = "fresh"
        assertEquals(1000.0, ready(shaping).run.density)
    }

    @Test
    fun `a cylinder's role gives it its limit, and keeps a bailout from the ascent's choice`() {
        val run = ready(
            planned(
                *FORTY,
                gases = listOf(Breathed(), Breathed("EAN50", Role.DECO), Breathed("EAN32", Role.BAILOUT)),
            ),
        ).run
        assertEquals(1.4, run.sources.getValue("g1").mostOxygen)
        assertEquals(1.6, run.sources.getValue("g2").mostOxygen)
        assertEquals(1.4, run.sources.getValue("g3").mostOxygen, "a bailout is breathed at effort")
        assertEquals(listOf(true, true, false), run.sources.values.map { it.ascentMayChoose })
    }

    @Test
    fun `how deep a cylinder may go is the model's figure at its role's limit`() {
        val shaping = planned(*FORTY)
        val conditions = assertNotNull(conditionsOf(shaping).first)
        val deco = maximumOperatingDepth(Gas.parse("EAN50"), most = 1.6, density = 1030.0)!!
        assertEquals("${rateSaid(deco)} m", deepestSaid(Breathed("EAN50", Role.DECO), conditions))
        assertTrue(deepestSaid(Breathed("EAN50", Role.DECO), conditions).startsWith("21."))
        assertTrue(deepestSaid(Breathed("EAN50", Role.BOTTOM), conditions).startsWith("17."), "held to 1.4")
        assertEquals("", deepestSaid(Breathed("nonsense"), conditions))
    }
}

class GasListTest {

    @Test
    fun `a cylinder added is a deco cylinder, and the lines naming those after it follow them`() {
        val shaping = planned(Segment("40", gas = 1), gases = listOf(Breathed(), Breathed("EAN50")))
        shaping.addGas(0)
        assertEquals(Role.DECO, shaping.gases[1].role)
        assertEquals(2, shaping.segments[0].gas, "still EAN50")
        assertEquals("EAN50", shaping.gases[2].gas)
    }

    @Test
    fun `a cylinder a line breathes stays, whether the line names it or follows the line above`() {
        val shaping = planned(*FORTY, gases = listOf(Breathed(), Breathed("EAN50", Role.DECO)))
        assertNotNull(shaping.keptBecause(0), "breathed by following")
        shaping.removeGas(0)
        assertEquals(2, shaping.gases.size)
        assertNull(shaping.keptBecause(1), "only the ascent would breathe it")
        shaping.removeGas(1)
        assertEquals(1, shaping.gases.size)
        assertNotNull(shaping.keptBecause(0), "and the last always stays")
    }

    @Test
    fun `the first cylinder can go once the first line names another`() {
        val shaping = planned(
            Segment("18", gas = 1),
            Segment("18", duration = "30"),
            gases = listOf(Breathed(), Breathed("EAN32")),
        )
        assertNull(shaping.keptBecause(0))
        shaping.removeGas(0)
        assertEquals("EAN32", shaping.gases.single().gas)
        assertEquals(0, shaping.segments[0].gas, "renumbered with it")
    }

    @Test
    fun `an empty line naming a cylinder taken out follows the line above instead`() {
        val shaping = planned(Segment("18"), Segment(gas = 1), gases = listOf(Breathed(), Breathed("EAN32")))
        shaping.removeGas(1)
        assertNull(shaping.segments[1].gas)
    }

    @Test
    fun `a line is added below its own, and the last line stays`() {
        val shaping = planned(Segment("18"))
        shaping.addSegment(0)
        assertEquals(listOf("18", ""), shaping.segments.map { it.depth })
        shaping.removeSegment(0)
        shaping.removeSegment(0)
        assertEquals(1, shaping.segments.size)
    }
}

class WorkedOfTest {

    @Test
    fun `the way up is added from the last typed line to the surface`() {
        val done = done(planned(*FORTY))
        assertEquals(0.0, done.tail.last().to)
        assertTrue(done.tail.any { it.direction == Direction.STAY }, "forty metres for twenty-five minutes owes stops")
        assertEquals(done.tail.last().ends, done.whole.depth.last().first)
    }

    @Test
    fun `a stop is one line, however many minutes the model wrote it as`() {
        val tail = done(planned(*FORTY)).tail
        for ((before, after) in tail.zipWithNext()) {
            assertTrue(
                !(before.direction == after.direction && before.gas == after.gas),
                "two lines that should be one: $before, $after",
            )
        }
    }

    @Test
    fun `a deco cylinder is switched to on the way up, and a bailout never is`() {
        val deco = done(planned(*FORTY, gases = listOf(Breathed(), Breathed("EAN50", Role.DECO))))
        assertTrue(deco.tail.any { it.gas == 1 }, "the ascent takes the deco gas")
        val bailout = done(planned(*FORTY, gases = listOf(Breathed(), Breathed("EAN50", Role.BAILOUT))))
        assertTrue(bailout.tail.none { it.gas == 1 }, "and leaves the bailout alone")
    }

    @Test
    fun `a dive typed all the way up has nothing added`() {
        val done = done(
            planned(
                Segment("18"),
                Segment("18", duration = "20"),
                Segment("6"),
                Segment("6", duration = "3"),
                Segment("0"),
            ),
        )
        assertEquals(emptyList(), done.tail)
    }

    @Test
    fun `the safety stop is held on the way up, and not where there is none`() {
        val shallow = planned(Segment("18"), Segment("18", duration = "20"))
        val held = done(shallow).tail.filter { it.direction == Direction.STAY && it.to == 6.0 }
        assertTrue(held.sumOf { it.seconds } >= 180, "${done(shallow).tail}")
        shallow.safetyMinutes = "0"
        assertTrue(done(shallow).tail.none { it.direction == Direction.STAY }, "a dive in its limits owes nothing")
    }

    @Test
    fun `a typed way up that skips the safety stop or rises too fast is warned of`() {
        val skipped = done(planned(Segment("18"), Segment("18", duration = "20"), Segment("0")))
        assertTrue(skipped.evaluated.findings.any { "safety stop" in it.said }, "${skipped.evaluated.findings}")
        val fast = done(planned(Segment("18"), Segment("18", duration = "20"), Segment("0", rate = "18")))
        assertTrue(fast.evaluated.findings.any { "faster than" in it.said }, "${fast.evaluated.findings}")
    }

    @Test
    fun `the whole dive is one the model answers for`() {
        assertIs<Evaluated.Done>(done(planned(*FORTY)).evaluated)
    }
}
