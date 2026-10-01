package yemoja.ui.api

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Breathed
import yemoja.logic.Planned
import yemoja.logic.Role
import yemoja.logic.Segment
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A plan asked for and a plan written, by something with no window. See
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

private fun logbook(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

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
        assertEquals("the plan has no lines", empty.reason)
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
}

class SavedTest {

    @Test
    fun `a plan written with no dive named makes one, holding it`() {
        val universe = logbook()
        val done = assertIs<Saved.Done>(saved(universe, table()))
        val dive = assertNotNull(universe.logbook[done.dive])
        assertEquals(Types.DIVE, dive.description)
        val profiles = (dive.read("profiles") as Result.Usable<*>).value as Map<*, *>
        assertEquals(setOf(done.key), profiles.keys)
        assertEquals(true, (dive.read("planned") as Result.Usable<*>).value, "a dive still ahead")
    }

    @Test
    fun `a plan written onto a dive sits beside what that dive already holds`() {
        val universe = logbook(
            "dive/2026-06-21#0.json" to
                    """{"profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
        )
        val done = assertIs<Saved.Done>(saved(universe, table(), dive = "2026-06-21#0"))
        assertEquals("2026-06-21#0", done.dive)
        val dive = assertNotNull(universe.logbook["2026-06-21#0"])
        val profiles = (dive.read("profiles") as Result.Usable<*>).value as Map<*, *>
        assertEquals(setOf("p", done.key), profiles.keys, "the recording stays where it was")
    }

    @Test
    fun `two plans on one dive are told apart by name`() {
        val universe = logbook()
        val first = assertIs<Saved.Done>(saved(universe, table(), name = "Plan A"))
        val second = assertIs<Saved.Done>(
            saved(universe, table(low = "30", high = "70"), dive = first.dive, name = "Plan B"),
        )
        val dive = assertNotNull(universe.logbook[first.dive])
        val profiles = (dive.read("profiles") as Result.Usable<*>).value as Map<*, *>
        assertEquals(setOf(first.key, second.key), profiles.keys)
    }

    @Test
    fun `a plan that will not calculate is refused rather than written`() {
        val universe = logbook()
        val wrong = assertIs<Saved.Refused>(saved(universe, table(stay = "soon")))
        assertTrue("2:13" in wrong.reason, wrong.reason)
        assertEquals(emptyList(), universe.logbook.allOf(Types.DIVE), "and nothing was written")
    }

    @Test
    fun `a dive that is not there, or is not a dive, says so`() {
        val universe = logbook("person.json" to """{"anna": {"first_name": "Anna"}}""")
        assertEquals(
            "nowhere is not in this logbook",
            assertIs<Saved.Refused>(saved(universe, table(), dive = "nowhere")).reason,
        )
        assertTrue(
            "rather than a dive" in assertIs<Saved.Refused>(saved(universe, table(), dive = "anna")).reason,
        )
    }

    @Test
    fun `calculating writes nothing at all`() {
        val universe = logbook()
        assertIs<Calculated.Done>(calculated(table()))
        assertEquals(emptyList(), universe.logbook.allOf(Types.DIVE))
        assertNull(universe.logbook["2026-06-21#0"])
    }
}
