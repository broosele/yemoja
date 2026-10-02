package yemoja.logic

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
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
}
