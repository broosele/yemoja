package yemoja.logic

import yemoja.data.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A plan asked for, with nothing read and nothing written. See
 * ../../../../../../ui/api/doc.md — `API-7`.
 */

/** Forty metres for twenty-five minutes on air, at the full factors, as a table would have it. */
private fun table(
    metres: String = "40",
    stay: String = "22:46",
    gases: List<Breathed> = listOf(Breathed("air", Role.BOTTOM, size = "24", fill = "232", sac = "20")),
    low: String = "100",
    high: String = "100",
): Planned = Planned(
    segments = listOf(Segment(metres), Segment(metres, duration = stay)),
    gases = gases,
    gradientLow = low,
    gradientHigh = high,
    bottomOxygen = "1.4",
    decoOxygen = "1.6",
    leastOxygen = "0.18",
    descentRate = "18",
    ascentRate = "9",
    safetyDepth = "6",
    safetyMinutes = "3",
    lastStop = "3",
    water = "salt",
)

class CalculatedTest {

    @Test
    fun `a plan is calculated from its description alone, with no logbook anywhere`() {
        val schedule = assertIs<Calculated.Done>(calculated(table())).schedule
        assertTrue(schedule.stopSeconds > 0, "forty metres for twenty-five minutes owes stops")
        assertEquals(40.0, schedule.maxDepthMetres)
        assertNotNull(schedule.deepestStop)
        assertTrue(
            schedule.runtimeSeconds > 25 * 60,
            "the way up is in the runtime, which is therefore longer than the bottom time",
        )
    }

    @Test
    fun `the lines say which the caller described and which the model added`() {
        val schedule = assertIs<Calculated.Done>(calculated(table())).schedule
        val described = schedule.lines.filter { !it.added }
        assertEquals(2, described.size, "the descent and the stay, as they were written")
        assertEquals("down", described.first().direction)
        assertEquals("stay", described.last().direction)
        assertTrue(schedule.lines.any { it.added && it.direction == "up" }, "and the way up")
        // One list in one order, so a reader never has to join two of them.
        val order = schedule.lines.map { it.beginsAt }
        assertEquals(order.sorted(), order)
    }

    @Test
    fun `the stops are the depths held on the way up, deepest first`() {
        val schedule = assertIs<Calculated.Done>(calculated(table())).schedule
        assertEquals(
            schedule.stops.map { it.metres }.sortedDescending(),
            schedule.stops.map { it.metres },
        )
        assertEquals(schedule.stopSeconds, schedule.stops.sumOf { it.seconds })
        assertEquals(schedule.deepestStop, schedule.stops.first().metres)
    }

    @Test
    fun `a cylinder is named by its number, in the lines and in the gas used`() {
        val schedule = assertIs<Calculated.Done>(
            calculated(
                table(
                    gases = listOf(
                        Breathed("air", Role.BOTTOM, size = "24", fill = "232", sac = "20"),
                        Breathed("EAN50", Role.DECO, size = "11", fill = "200", sac = "20"),
                    ),
                ),
            ),
        ).schedule
        assertEquals("1", schedule.lines.first().gas)
        assertTrue(schedule.gasUsedLitres.keys.all { it.toIntOrNull() != null }, "numbers, not keys")
        assertTrue(schedule.gasUsedLitres.getValue("1") > 0)
    }

    @Test
    fun `holding tighter factors asks for longer stops`() {
        val loose = assertIs<Calculated.Done>(calculated(table(low = "100", high = "100"))).schedule
        val tight = assertIs<Calculated.Done>(calculated(table(low = "30", high = "70"))).schedule
        assertTrue(
            tight.stopSeconds > loose.stopSeconds,
            "30/70 asked for ${tight.stopSeconds}s and 100/100 for ${loose.stopSeconds}s",
        )
    }

    @Test
    fun `a shallow dive that owes nothing still gives a schedule`() {
        val schedule = assertIs<Calculated.Done>(calculated(table("12", stay = "30"))).schedule
        assertEquals(0.0, schedule.stops.filter { it.metres > 6 }.sumOf { it.metres })
        assertTrue(schedule.runtimeSeconds > 0)
    }

    @Test
    fun `the ceiling comes back with the schedule, and is empty where it never rises`() {
        val owing = assertIs<Calculated.Done>(calculated(table())).schedule
        assertTrue(owing.ceiling.any { it.value > 0 }, "forty metres for twenty-five minutes owes a ceiling")
        assertEquals(owing.ceiling.map { it.second }.sorted(), owing.ceiling.map { it.second })
        val free = assertIs<Calculated.Done>(calculated(table("12", stay = "30"))).schedule
        assertTrue(free.ceiling.isEmpty(), "a dive owing nothing says nothing")
    }

    @Test
    fun `the clocks and the gauges run through the dive, not just to its end`() {
        val schedule = assertIs<Calculated.Done>(calculated(table())).schedule
        assertTrue(schedule.noDecompressionSeconds.isNotEmpty())
        assertTrue(schedule.cnsSeries.last().value > 0)
        assertTrue(schedule.otuSeries.last().value > 0)
        val gauge = schedule.pressures.getValue("1")
        assertTrue(gauge.first().value > gauge.last().value, "a gauge runs down as the dive breathes")
        assertTrue(schedule.timeToSurfaceSeconds.any { it.value > 0 }, "the bottom owes a way up")
        assertEquals(0.0, schedule.timeToSurfaceSeconds.last().value, "nothing is owed at the surface")
        assertTrue(schedule.gradientFactorNow.any { it.value > 0 }, "the way up loads the compartments")
    }

    @Test
    fun `a plan that will not read is refused in the words the form refuses it in`() {
        val wrong = assertIs<Calculated.Refused>(calculated(table(stay = "soon")))
        assertTrue("2:13" in wrong.reason, wrong.reason)
        val empty = assertIs<Calculated.Refused>(
            calculated(table().copy(segments = listOf(Segment()))),
        )
        assertEquals("the runtime is empty", empty.reason)
        val unmixed = assertIs<Calculated.Refused>(calculated(table(gases = listOf(Breathed("")))))
        assertTrue("missing its mix" in unmixed.reason, unmixed.reason)
    }

    @Test
    fun `the oxygen clocks and the waits come back with it`() {
        val schedule = assertIs<Calculated.Done>(calculated(table())).schedule
        assertTrue(schedule.cnsPercent > 0, "air at forty metres spends the clock")
        assertTrue(schedule.otu > 0)
        assertNotNull(schedule.noFlightSeconds)
        assertNotNull(schedule.desaturationSeconds)
    }

    @Test
    fun `the gas reserve is answered beside the schedule, lost gas included where there is a deco cylinder`() {
        val gases = listOf(
            Breathed("air", Role.BOTTOM, size = "24", fill = "232", sac = "20"),
            Breathed("EAN50", Role.DECO, size = "11", fill = "200", sac = "20"),
        )
        val schedule = assertIs<Calculated.Done>(
            calculated(table(gases = gases).copy(stressFactor = "2", problemMinutes = "2")),
        ).schedule
        val lost = assertIs<ReserveAnswer.Done>(schedule.reserves.getValue(Scenario.LOST_GAS))
        assertTrue(lost.kept.getValue("1").litres > 0, "losing the deco gas leaves the bottom gas something to keep")
        assertTrue(Scenario.SHARED in schedule.reserves, "switched on by default")
    }

    @Test
    fun `a rebreather plan answers its schedule and setpoints, and leaves gas and reserves to come`() {
        val loop = table(gases = listOf(Breathed("air", Role.BOTTOM, size = "3", fill = "200", sac = "20")), low = "30", high = "70")
            .copy(diveMode = "ccr", stressFactor = "2", problemMinutes = "2")
        val schedule = assertIs<Calculated.Done>(calculated(loop)).schedule
        val open = assertIs<Calculated.Done>(calculated(loop.copy(diveMode = "oc"))).schedule

        assertEquals("ccr", schedule.diveMode)
        assertEquals(0.7, schedule.setpointSeries.first().value)
        assertEquals(1.3, schedule.oxygenSeries.last { it.second <= 1800 }.value, 1e-12, "the loop's oxygen, at the end of the bottom")
        assertEquals(1.3, schedule.setpointSeries.last().value)
        assertTrue(schedule.stopSeconds < open.stopSeconds, "${schedule.stopSeconds} s against ${open.stopSeconds} s")
        assertTrue(schedule.gasUsedLitres.isEmpty(), "gas on the loop comes with the second step")
        assertEquals(
            "No bailout: give a cylinder the bailout role",
            assertIs<ReserveAnswer.Refused>(schedule.reserves.getValue(Scenario.BAILOUT)).reason,
            "its one scenario is the bailout, and it has none",
        )
        assertTrue(schedule.lines.all { it.gas == "1" }, "every line breathes the diluent")
        assertEquals("oc", open.diveMode)
        assertTrue(open.setpointSeries.isEmpty())
    }

    @Test
    fun `a diluent is held to the bottom's oxygen limit whatever role its cylinder was given`() {
        // EAN32 at forty metres is 1.6 bar: within the deco limit, past the bottom's 1.4.
        val loop = table(gases = listOf(Breathed("EAN32", Role.DECO))).copy(diveMode = "ccr")
        val schedule = assertIs<Calculated.Done>(calculated(loop)).schedule

        assertTrue(schedule.warnings.any { "pO₂ too high" in it.said && "1.40 bar allowed" in it.said }, "${schedule.warnings.map { it.said }}")
    }

    @Test
    fun `a rebreather plan's reserve is its bailout, said as a sentence`() {
        val loop = table(
            gases = listOf(
                Breathed("air", Role.BOTTOM),
                Breathed("air", Role.BAILOUT, size = "11", fill = "200", sac = "20"),
                Breathed("EAN50", Role.DECO, size = "7", fill = "200", sac = "20"),
            ),
            low = "30",
            high = "70",
        ).copy(diveMode = "ccr", stressFactor = "2", problemMinutes = "2")
        val schedule = assertIs<Calculated.Done>(calculated(loop)).schedule
        val bailout = assertIs<ReserveAnswer.Done>(schedule.reserves.getValue(Scenario.BAILOUT))

        assertEquals(setOf(Scenario.BAILOUT), schedule.reserves.keys, "no lost gas and no sharing on a loop")
        assertEquals(setOf("2"), bailout.kept.keys, "the bailout alone, not the deco cylinder")
        assertTrue(bailout.said.startsWith("Gas 2 reserve needs to be ") && "bailing out to the surface at normal SAC" in bailout.said, bailout.said)
        val off = assertIs<Calculated.Done>(calculated(loop.copy(bailoutScenario = false))).schedule
        assertTrue(off.reserves.isEmpty())
    }

    @Test
    fun `a bailout lacking its SAC is named, whatever cylinder the lost-gas setting names`() {
        val loop = table(
            gases = listOf(Breathed("air", Role.BOTTOM), Breathed("air", Role.BAILOUT, size = "11", fill = "200")),
        ).copy(diveMode = "ccr", problemMinutes = "2", lostGas = 1)
        val bailout = assertIs<ReserveAnswer.Refused>(
            assertIs<Calculated.Done>(calculated(loop)).schedule.reserves.getValue(Scenario.BAILOUT),
        )

        assertEquals("Cannot be calculated (missing for Gas 2: SAC)", bailout.reason, "the diluent's own SAC is not asked for")
    }

    @Test
    fun `a rebreather plan is refused for what it cannot be`() {
        fun refused(planned: Planned): String = assertIs<Calculated.Refused>(calculated(planned)).reason
        val loop = table().copy(diveMode = "ccr")

        assertEquals("Dive mode should be oc or ccr, not \"scr\"", refused(table().copy(diveMode = "scr")))
        assertEquals(
            "Setpoint high should be more than 0 bar and at most the pO₂ max bottom, 1.4 bar, not \"1.5\"",
            refused(loop.copy(setpointHigh = "1.5")),
        )
        assertEquals("Diluent should be one of the 1 cylinders, not 2", refused(loop.copy(diluent = 1)))
        assertEquals(
            "Gas 1 is the diluent, so its role should not be bailout",
            refused(loop.copy(gases = listOf(Breathed("air", Role.BAILOUT)))),
        )
        assertEquals(
            "Line 2 names a gas, but a CCR plan breathes the loop throughout",
            refused(
                loop.copy(
                    gases = listOf(Breathed("air", Role.BOTTOM), Breathed("EAN50", Role.DECO)),
                    segments = listOf(Segment("40"), Segment("40", duration = "20", gas = 1)),
                ),
            ),
        )
    }

    @Test
    fun `a plan starts at one standard atmosphere`() {
        assertEquals(1.013, assertNotNull(conditionsOf(table()).first).atmosphericPressure)
    }

    @Test
    fun `a plan at altitude owes longer stops, and its gases may be breathed deeper`() {
        // At 0.8 bar, about 2,000 m up, the same depth leaves the tissues further from the surface.
        val high = table(low = "30", high = "70").copy(atmosphericPressure = "0.8")
        val sea = table(low = "30", high = "70")
        fun stops(planned: Planned): Int = assertIs<Calculated.Done>(calculated(planned)).schedule.stopSeconds

        assertTrue(stops(high) > stops(sea), "${stops(high)} s against ${stops(sea)} s")
        val air = Breathed("air", Role.BOTTOM)
        val atAltitude = deepestSaid(air, conditionsOf(high).first).removeSuffix(" m").toDouble()
        val atSea = deepestSaid(air, conditionsOf(sea).first).removeSuffix(" m").toDouble()
        assertTrue(atAltitude > atSea, "less air above, so more water before the oxygen limit: $atAltitude against $atSea")
    }

    @Test
    fun `an atmospheric pressure in millibars is refused rather than read as bar`() {
        assertEquals(
            "Atmospheric pressure should be 0.4 to 1.1 bar, not \"1013\"",
            conditionsOf(table().copy(atmosphericPressure = "1013")).second,
        )
    }

    @Test
    fun `a warning and a reserve are whole sentences, the cylinder and the moment in them`() {
        // Ten litres of air cannot do forty metres for twenty-five minutes with its stops.
        val gases = listOf(
            Breathed("air", Role.BOTTOM, size = "10", fill = "200", sac = "25"),
            Breathed("EAN50", Role.DECO, size = "3", fill = "200", sac = "25"),
        )
        val schedule = assertIs<Calculated.Done>(
            calculated(table(gases = gases, low = "30", high = "70").copy(stressFactor = "2", problemMinutes = "2")),
        ).schedule
        val empty = schedule.warnings.first { it.gas == "1" }
        val lost = assertIs<ReserveAnswer.Done>(schedule.reserves.getValue(Scenario.LOST_GAS))

        assertTrue(empty.said.startsWith("Gas 1 runs empty: "), empty.said)
        assertTrue(empty.said.endsWith(" at ${clockOf(empty.second)}"), empty.said)
        assertTrue(lost.said.startsWith("Gas 1 reserve needs to be ") && "worst at " in lost.said, lost.said)
        assertTrue(assertNotNull(lost.shortfall).startsWith("Gas 1 reserve violation: "), lost.shortfall)
        assertNull(lost.unchecked, "both cylinders have a size and a fill")
    }

    @Test
    fun `a cylinder with no size is said to be costed in litres only`() {
        val schedule = assertIs<Calculated.Done>(
            calculated(table(gases = listOf(Breathed("air", Role.BOTTOM, sac = "20"))).copy(stressFactor = "2", problemMinutes = "0")),
        ).schedule
        val shared = assertIs<ReserveAnswer.Done>(schedule.reserves.getValue(Scenario.SHARED))

        assertEquals("Gas 1: reserve in litres only (missing: volume, start pressure)", shared.unchecked)
        assertNull(shared.shortfall, "nothing to judge it against")
    }

    @Test
    fun `a scenario switched off, or with no cylinder to try, is left out of the answer`() {
        val schedule = assertIs<Calculated.Done>(
            calculated(table().copy(stressFactor = "2", problemMinutes = "2", sharedScenario = false)),
        ).schedule
        assertEquals(null, schedule.reserves[Scenario.SHARED], "switched off")
        assertEquals(null, schedule.reserves[Scenario.LOST_GAS], "no deco cylinder to lose")
    }

    @Test
    fun `a cylinder with no rate is refused by its own scenario, not by the whole plan`() {
        val gases = listOf(
            Breathed("air", Role.BOTTOM, size = "24", fill = "232"),
            Breathed("EAN50", Role.DECO, size = "11", fill = "200", sac = "20"),
        )
        val schedule = assertIs<Calculated.Done>(
            calculated(table(gases = gases).copy(stressFactor = "2", problemMinutes = "2")),
        ).schedule
        val lost = assertIs<ReserveAnswer.Refused>(schedule.reserves.getValue(Scenario.LOST_GAS))
        assertTrue("SAC" in lost.reason, lost.reason)
    }

    @Test
    fun `a case following an earlier one owes more than the same dive fresh`() {
        val answers = calculatedAll(
            listOf(
                Case("first", table()),
                Case("second", table(), Follows(0, intervalSeconds = 3600.0)),
            ),
        )
        val first = assertIs<Calculated.Done>(answers[0]).schedule
        val second = assertIs<Calculated.Done>(answers[1]).schedule
        assertEquals(
            assertIs<Calculated.Done>(calculated(table())).schedule.stopSeconds,
            first.stopSeconds,
            "the first case starts fresh",
        )
        assertTrue(
            second.stopSeconds > first.stopSeconds,
            "an hour after the first dive owes ${second.stopSeconds}s against a fresh ${first.stopSeconds}s",
        )
    }

    @Test
    fun `a longer interval leaves less behind, so the follower owes less`() {
        fun stopsAfter(seconds: Double): Int = assertIs<Calculated.Done>(
            calculatedAll(
                listOf(Case("first", table()), Case("second", table(), Follows(0, seconds))),
            )[1],
        ).schedule.stopSeconds
        assertTrue(
            stopsAfter(6 * 60 * 60.0) < stopsAfter(30 * 60.0),
            "six hours should owe less than half an hour",
        )
    }

    @Test
    fun `a line at fault still leaves the lines above it laid out`() {
        val planned = table().copy(
            segments = listOf(Segment("30"), Segment("30", duration = "20:00"), Segment("30")),
        )
        val refused = assertIs<Calculated.Refused>(calculated(planned))
        assertEquals("Line 3 needs a duration, because it stays at 30 m", refused.reason)
        assertEquals(listOf(100, 1200), refused.lines.map { it.seconds }, "the descent at 18 m/min, then the stay")
        assertTrue("\"runtime\"" in Json.write(saidOf("x", refused)))
        assertEquals(listOf(3), refused.needsDuration)
        assertTrue("\"needs_duration\": [3]" in Json.write(saidOf("x", refused)), Json.write(saidOf("x", refused)))
        assertTrue(
            "\"runtime\"" !in Json.write(saidOf("x", Calculated.Refused("the runtime is empty"))),
            "nothing laid, nothing written",
        )
    }

    @Test
    fun `a case following one that was refused is refused with it`() {
        val answers = calculatedAll(
            listOf(
                Case("first", table(stay = "soon")),
                Case("second", table(), Follows(0, intervalSeconds = 3600.0)),
            ),
        )
        assertIs<Calculated.Refused>(answers[0])
        assertEquals(
            "second follows first, which did not calculate",
            assertIs<Calculated.Refused>(answers[1]).reason,
        )
    }
}
