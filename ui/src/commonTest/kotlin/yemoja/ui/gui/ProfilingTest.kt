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
        assertEquals("Line 2 needs a duration, because it stays at 40 m", wrong.reason)
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
        assertEquals("Line 1 needs a depth", wrong.reason)
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
    fun `a line follows the gas chosen on the line directly above, filled in or not`() {
        val shaping = planned(
            Segment(gas = 1),
            Segment("18"),
            Segment("18", duration = "20"),
            gases = listOf(Breathed(), Breathed("EAN32")),
        )
        assertEquals(1, gasAbove(shaping, 1), "what the second line shows in italics")
        assertEquals(listOf(1, 1), ready(shaping).legs.map { it.gas }, "and what it breathes")
        assertEquals(setOf(1), shaping.breathed())
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
        assertEquals("GF low is missing", wrong.reason)
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
    fun `a line names a cylinder by its number and its mix`() {
        val shaping = planned(Segment("18"), gases = listOf(Breathed(), Breathed("EAN50"), Breathed(" ")))
        assertEquals("1: AIR", gasChoiceOf(shaping, 0))
        assertEquals("2: EAN50", gasChoiceOf(shaping, 1))
        assertEquals("3", gasChoiceOf(shaping, 2), "a mix not yet typed leaves the number alone")
    }

    @Test
    fun `a gas is named as the application writes it, whatever case it was typed in`() {
        val shaping = planned(Segment("18"), gases = listOf(Breathed("air"), Breathed("ean50"), Breathed("tmx 18/45")))
        assertEquals("1: AIR", gasChoiceOf(shaping, 0))
        assertEquals("2: EAN50", gasChoiceOf(shaping, 1))
        assertEquals("3: TMX18/45", gasChoiceOf(shaping, 2))
        assertEquals("EAN50", prettyGasOf("ean50"))
        assertEquals("O2", prettyGasOf("o2"))
        assertEquals("tmx 18/45", prettyGasOf("tmx 18/45"), "what would move under the cursor is left as typed")
        assertEquals("nonsense", prettyGasOf("nonsense"))
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
        assertTrue(skipped.evaluated.findings.any { "Safety stop" in it.said }, "${skipped.evaluated.findings}")
        val fast = done(planned(Segment("18"), Segment("18", duration = "20"), Segment("0", rate = "18")))
        assertTrue(fast.evaluated.findings.any { "at most 9 m/min" in it.said }, "${fast.evaluated.findings}")
    }

    @Test
    fun `the whole dive is one the model answers for`() {
        assertIs<Evaluated.Done>(done(planned(*FORTY)).evaluated)
    }
}

/** Air on the bottom in a twelve-litre cylinder and EAN50 for the stops, each breathed at 20. */
private val BOTTOM_AND_DECO = listOf(
    Breathed(gas = "air", size = "12", fill = "200", sac = "20"),
    Breathed(gas = "EAN50", role = Role.DECO, size = "7", fill = "200", sac = "20"),
)

private fun reckoned(shaping: Shaping): Reckoned {
    val done = done(shaping)
    return reckonedOf(shaping, done, assertNotNull(conditionsOf(shaping).first))
}

private fun scenario(shaping: Shaping, scenario: Scenario): yemoja.logic.Reserve.Done =
    assertIs<Reckoning.Done>(reckoned(shaping).scenarios[scenario]).reserve

class ReserveTest {

    @Test
    fun `a new plan's divers sharing gas breathe at twice their usual rate`() {
        assertEquals("2", planned().panicFactor)
    }

    @Test
    fun `both scenarios are tried until one is switched off`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        assertEquals(setOf(Scenario.LOST_GAS, Scenario.SHARED), reckoned(shaping).done.keys)

        shaping.sharedScenario = false
        assertNull(reckoned(shaping).scenarios[Scenario.SHARED], "a buddy is not assumed on a solo dive")
        shaping.lostGasScenario = false
        assertTrue(reckoned(shaping).done.isEmpty())
        assertEquals("", minimumSaid(reckoned(shaping), "g1"), "nothing kept back for nothing tried")
    }

    @Test
    fun `the gas lost is the first deco gas until another is chosen`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO + Breathed(gas = "EAN80", role = Role.DECO))
        assertEquals(1, shaping.lostIndex())

        shaping.lostGas = 0
        assertEquals(0, shaping.lostIndex(), "any cylinder may be chosen, the bottom gas too")
    }

    @Test
    fun `a plan with no deco gas loses none until one is chosen`() {
        val shaping = planned(*FORTY, gases = listOf(Breathed(gas = "air", size = "24", fill = "232", sac = "20")))

        assertNull(shaping.lostIndex())
        assertTrue(!shaping.lostGasTried())
        assertNull(reckoned(shaping).scenarios[Scenario.LOST_GAS], "no lost-gas scenario, rather than a complaint")
    }

    @Test
    fun `choosing None leaves the lost-gas scenario out, and choosing a gas brings it back`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        shaping.lostGasScenario = false

        assertTrue(!shaping.lostGasTried())
        assertNull(reckoned(shaping).scenarios[Scenario.LOST_GAS])
        assertIs<Reckoning.Done>(reckoned(shaping).scenarios[Scenario.SHARED], "the other goes on")

        shaping.lostGasScenario = true
        shaping.lostGas = 1
        assertIs<Reckoning.Done>(reckoned(shaping).scenarios[Scenario.LOST_GAS])
    }

    @Test
    fun `the gas lost follows its cylinder when others are added or taken out`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO + Breathed(gas = "EAN80", role = Role.DECO))
        shaping.lostGas = 2
        shaping.addGas(0)
        assertEquals(3, shaping.lostIndex(), "moved down with the cylinder added above it")

        shaping.removeGas(3)
        assertNull(shaping.lostGas, "taken out with its cylinder")
        assertEquals(1, shaping.lostIndex(), "and back to the first deco cylinder, the one just added")
    }

    @Test
    fun `losing the deco gas costs bottom gas, in bar, at the end of the bottom`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        val reserve = scenario(shaping, Scenario.LOST_GAS)

        assertEquals(setOf("g1"), reserve.needed.keys, "the deco gas is lost")
        val said = scenarioSaid(Scenario.LOST_GAS, reserve, shaping)
        assertTrue(
            said.matches(Regex("Gas 1 needs [0-9]+ bar at 25:00 \\(40 m\\), 2:00 at depth, then surfacing without Gas 2 at normal SAC")),
            said,
        )
        assertEquals("25:00 (40 m)", worstSaid(reserve))
    }

    @Test
    fun `a buddy shares bottom gas only as far as the deco gas`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        val reserve = scenario(shaping, Scenario.SHARED)
        val said = scenarioSaid(Scenario.SHARED, reserve, shaping)

        assertTrue(reserve.upTo > 20, "EAN50 may be breathed from about 22 m: ${reserve.upTo}")
        assertTrue(said.startsWith("Gas 1 needs ") && said.endsWith("at 2 × SAC"), said)
        assertTrue(Regex("sharing 2:00 at depth, then to [0-9]+([.][0-9])? m at").containsMatchIn(said), "to a tenth, as the MOD is: $said")
        assertTrue(
            reserve.needed.getValue("g1") < scenario(shaping, Scenario.LOST_GAS).needed.getValue("g1"),
            "a short share to the deco gas costs less than every stop on bottom gas",
        )
    }

    @Test
    fun `a new plan's reserve begins with two minutes at depth, and none is no hold`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        assertEquals("2", shaping.problemMinutes)
        val held = scenario(shaping, Scenario.SHARED).needed.getValue("g1")

        shaping.problemMinutes = "0"
        val prompt = scenario(shaping, Scenario.SHARED)
        assertTrue(prompt.needed.getValue("g1") < held, "a minute sharing at 40 m costs gas")
        assertTrue("at depth" !in scenarioSaid(Scenario.SHARED, prompt, shaping))
    }

    @Test
    fun `a problem-solving time typed wrong leaves both scenarios unsaid, and the plan answered`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        shaping.problemMinutes = "soon"

        assertIs<Worked.Done>(workedOf(ready(shaping)))
        for (scenario in Scenario.entries) {
            val wrong = assertIs<Reckoning.Wrong>(reckoned(shaping).scenarios[scenario])
            assertTrue(wrong.reason.startsWith("Problem solving time should be"), wrong.reason)
        }
    }

    @Test
    fun `a cylinder's minimum is the most any scenario asks of it`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        val both = reckoned(shaping)
        val most = both.done.values.maxOf { kotlin.math.ceil(it.reserve.getValue("g1")).toInt() }

        assertEquals("$most bar", minimumSaid(both, "g1"))
        assertEquals("", minimumSaid(both, "g2"), "no scenario breathes the deco gas")
    }

    @Test
    fun `a shortfall says which cylinder, when, and in which scenario`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        val reckoned = reckoned(shaping)
        val lost = assertNotNull(shortfallSaid(Scenario.LOST_GAS, reckoned.done.getValue(Scenario.LOST_GAS)))

        assertTrue(lost.first().isDigit() && "Gas 1: " in lost && "should be at least" in lost, lost)
        assertTrue(lost.endsWith("(surfacing without the lost gas)"), lost)
        assertTrue("-" !in lost, "a gauge run dry is empty, not below nought: $lost")
        assertTrue(isShort(reckoned, "g1"))
        assertTrue(!isShort(reckoned, "g2"))
    }

    @Test
    fun `sharing that falls short says how far the sharing went`() {
        val small = listOf(BOTTOM_AND_DECO[0].copy(size = "3"), BOTTOM_AND_DECO[1])
        val reserve = scenario(planned(*FORTY, gases = small), Scenario.SHARED)
        val said = assertNotNull(shortfallSaid(Scenario.SHARED, reserve))

        assertTrue("(two divers sharing to " in said, said)
    }

    @Test
    fun `a plan with enough gas has no shortfall to say`() {
        val shaping = planned(
            Segment("18"),
            Segment("18", duration = "15"),
            gases = listOf(Breathed(gas = "air", size = "24", fill = "232", sac = "20")),
        )
        val reckoned = reckoned(shaping)

        for ((scenario, reserve) in reckoned.done) assertNull(shortfallSaid(scenario, reserve), "$scenario")
        assertNull(uncheckedSaid(reckoned, shaping))
    }

    @Test
    fun `a panic factor typed wrong leaves the rest answered`() {
        val shaping = planned(*FORTY, gases = BOTTOM_AND_DECO)
        shaping.panicFactor = "0.5"

        assertIs<Worked.Done>(workedOf(ready(shaping)), "the plan is still worked out")
        val wrong = assertIs<Reckoning.Wrong>(reckoned(shaping).scenarios[Scenario.SHARED])
        assertTrue("\"0.5\"" in wrong.reason, wrong.reason)
        assertIs<Reckoning.Done>(reckoned(shaping).scenarios[Scenario.LOST_GAS], "which needs no factor")
    }

    @Test
    fun `a cylinder with no SAC is named where the reserve would be`() {
        val shaping = planned(*FORTY, gases = listOf(Breathed(gas = "air", size = "12", fill = "200")))
        val wrong = assertIs<Reckoning.Wrong>(reckoned(shaping).scenarios[Scenario.SHARED])

        assertEquals("Cannot be calculated (missing for Gas 1: SAC)", wrong.reason)
    }

    @Test
    fun `a reserve that cannot be counted names everything each cylinder lacks`() {
        val shaping = planned(*FORTY, gases = listOf(Breathed(), Breathed("EAN32", Role.BAILOUT, size = "11")))
        assertEquals(
            "Cannot be calculated (missing for Gas 1: SAC, volume, start pressure; Gas 2: SAC, start pressure)",
            missingSaid(shaping),
        )
    }

    @Test
    fun `a buddy who can go straight to a deco gas needs no sharing, and says so`() {
        // A cylinder added as deco and left as air may be breathed at 40 m, so nothing is shared.
        val shaping = planned(*FORTY, gases = listOf(Breathed(sac = "20"), Breathed(role = Role.DECO, sac = "20")))
        val reserve = scenario(shaping, Scenario.SHARED)
        assertEquals("No sharing needed: each diver switches to 2: AIR at once", scenarioSaid(Scenario.SHARED, reserve, shaping))
    }

    @Test
    fun `a cylinder with no size is given in litres and said to be unchecked`() {
        val shaping = planned(*FORTY, gases = listOf(Breathed(gas = "air", sac = "20")))
        val reckoned = reckoned(shaping)

        assertTrue(minimumSaid(reckoned, "g1").endsWith(" L"), minimumSaid(reckoned, "g1"))
        assertEquals("Gas 1: reserve in litres only (missing: volume, start pressure)", uncheckedSaid(reckoned, shaping))
    }

    @Test
    fun `a deco gas not chosen as lost is breathed on the way up in trouble`() {
        val gases = BOTTOM_AND_DECO + Breathed(gas = "EAN80", role = Role.DECO, size = "7", fill = "200", sac = "20")
        val shaping = planned(*FORTY, gases = gases)
        shaping.lostGas = 2
        val reserve = scenario(shaping, Scenario.LOST_GAS)

        assertTrue("g2" in reserve.needed.keys, "${reserve.needed}")
        assertTrue("g3" !in reserve.needed.keys, "the one lost is not")
        assertTrue(scenarioSaid(Scenario.LOST_GAS, reserve, shaping).endsWith("surfacing without Gas 3 at normal SAC"))
    }

    @Test
    fun `the gas lost is read by the lost-gas scenario alone`() {
        val chosen = planned(*FORTY, gases = BOTTOM_AND_DECO)
        chosen.lostGas = 0

        assertEquals(
            scenario(planned(*FORTY, gases = BOTTOM_AND_DECO), Scenario.SHARED).needed,
            scenario(chosen, Scenario.SHARED).needed,
        )
    }
}

class WarnedLinesTest {

    @Test
    fun `a line shallower than its hypoxic gas may be breathed is wrong, and one deep enough is not`() {
        val shaping = planned(
            Segment("3", gas = 1),
            Segment("40"),
            Segment("40", duration = "10"),
            gases = listOf(Breathed(gas = "air"), Breathed(gas = "TMX10/70")),
        )
        val conditions = assertNotNull(conditionsOf(shaping).first)
        val legs = ready(shaping).legs

        assertTrue(tooShallowFor(legs[0], shaping, conditions), "0 to 3 m on 10/70, which needs about 8")
        assertTrue(gasWrongFor(legs[0], shaping, conditions))
        assertTrue(!tooShallowFor(legs[2], shaping, conditions), "at 40 m it is breathable")
    }

    @Test
    fun `the plan's own minimum decides what is too shallow`() {
        val shaping = planned(
            Segment("3", gas = 1),
            Segment("40"),
            Segment("40", duration = "10"),
            gases = listOf(Breathed(gas = "air"), Breathed(gas = "TMX10/70")),
        )
        assertEquals("0.18", shaping.leastOxygen, "starting from the setting")
        val first = ready(shaping).legs[0]
        assertTrue(tooShallowFor(first, shaping, assertNotNull(conditionsOf(shaping).first)))

        shaping.leastOxygen = "0.1"
        assertTrue(!tooShallowFor(first, shaping, assertNotNull(conditionsOf(shaping).first)), "10/70 is 0.10 bar at the surface")

        shaping.leastOxygen = ""
        assertEquals("pO₂ min is missing", conditionsOf(shaping).second)
    }

    @Test
    fun `a mix breathable at the surface is never too shallow`() {
        val shaping = planned(Segment("10"), Segment("10", duration = "10"))
        val conditions = assertNotNull(conditionsOf(shaping).first)

        assertTrue(ready(shaping).legs.none { tooShallowFor(it, shaping, conditions) })
    }

    @Test
    fun `a line deeper than its gas may be breathed is too deep, at the limit its role gives`() {
        val shaping = planned(
            Segment("30"),
            Segment("30", duration = "10", gas = 1),
            Segment("21", gas = 2),
            gases = listOf(Breathed(), Breathed("EAN50"), Breathed("EAN50", Role.DECO)),
        )
        val conditions = assertNotNull(conditionsOf(shaping).first)
        val legs = ready(shaping).legs
        assertEquals(false, tooDeepFor(legs[0], shaping, conditions), "air at 30 m")
        assertEquals(true, tooDeepFor(legs[1], shaping, conditions), "EAN50 as a bottom gas at 30 m")
        // The rise to 21 m begins at 30 m, deeper than EAN50 may go even as a deco gas.
        assertEquals(true, tooDeepFor(legs[2], shaping, conditions))
    }

    @Test
    fun `a deco gas within its limit is not too deep`() {
        val shaping = planned(
            Segment("21"),
            Segment("21", duration = "5", gas = 1),
            gases = listOf(Breathed(), Breathed("EAN50", Role.DECO)),
        )
        val conditions = assertNotNull(conditionsOf(shaping).first)
        assertEquals(false, tooDeepFor(ready(shaping).legs[1], shaping, conditions), "21 m is inside its 21.6")
    }

    @Test
    fun `a line typed above the ceiling breaks it, and the lines below it do not`() {
        val done = done(planned(*FORTY, Segment("0")))
        val above = aboveCeilingAt(done.whole, done.evaluated)
        val legs = ready(planned(*FORTY, Segment("0"))).legs
        assertEquals(listOf(false, false, true), legs.map { breaksCeiling(it, above) })
    }

    @Test
    fun `a stay above the ceiling breaks it from where it begins`() {
        val shaping = planned(*FORTY, Segment("3"), Segment("3", duration = "1"))
        val done = done(shaping)
        val above = aboveCeilingAt(done.whole, done.evaluated)
        val legs = ready(shaping).legs
        assertTrue(breaksCeiling(legs[2], above), "the rise to 3 m")
        assertTrue(breaksCeiling(legs[3], above), "and the minute held there")
        // The way up the model adds can only begin where the typed lines left the dive, so it is
        // above the ceiling too until the ceiling clears, and is shown so.
        assertTrue(breaksCeiling(done.tail.first(), above))
    }

    @Test
    fun `a dive within its limits breaks nothing`() {
        val done = done(planned(*FORTY))
        assertEquals(emptySet(), aboveCeilingAt(done.whole, done.evaluated))
    }
}
