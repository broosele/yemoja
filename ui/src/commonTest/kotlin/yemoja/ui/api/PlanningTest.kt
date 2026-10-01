package yemoja.ui.api

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Breathed
import yemoja.logic.Calculated
import yemoja.logic.Planned
import yemoja.logic.Role
import yemoja.logic.Segment
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.calculated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * A plan written, by something with no window. See ../../../../../../ui/api/doc.md — `API-7`.
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
