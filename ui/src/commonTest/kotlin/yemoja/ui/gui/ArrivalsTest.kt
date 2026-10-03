package yemoja.ui.gui

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Matching
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.titleOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Deciding what to do with what arrived, and doing all of it at once. See
 * ../../../../../../gui/doc.md — `GUI-31`.
 */

private fun logbook(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

private fun arriving(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private const val ONE = """{"start_date": "2026-06-21", "start_time": "10:00:00",
    "duration": 3600, "max_depth": 30.0,
    "profiles": {"perdix_2": {"fingerprint": ["a1"], "depth": [[0, 0], [60, 30.0]]}}}"""

/** The same dive as [ONE], seen by another computer: it overlaps. */
private const val OTHER = """{"start_date": "2026-06-21", "start_time": "10:11:00",
    "duration": 3400, "max_depth": 29.4,
    "profiles": {"i330r": {"fingerprint": ["b2"], "depth": [[0, 0], [60, 29.4]]}}}"""

private const val LATER = """{"start_date": "2026-06-21", "start_time": "14:00:00",
    "duration": 2400, "max_depth": 18.0,
    "profiles": {"perdix_2": {"fingerprint": ["c3"], "depth": [[0, 0], [60, 18.0]]}}}"""

private const val SITES = """{"gombe": {"name": "La Gombe", "latitude": 50.4213,
    "longitude": 4.5077}}"""

/** A dive that came with a fix, as a download brings one: a site of its own with no name. */
private const val FIXED = """{"start_date": "2026-06-22", "start_time": "09:00:00",
    "duration": 3000, "dive_site": "@unknown_dive_site"}"""

private const val FOUND = """{"unknown_dive_site": {"latitude": 50.4215, "longitude": 4.5079}}"""

/** [ONE] held; [OTHER], [LATER] and [FIXED] arriving. */
private fun review(): Universe {
    val into = logbook("dive/2026-06-21#0.json" to ONE, "dive_site.json" to SITES)
    into.importFrom(
        arriving(
            "dive/2026-06-21#0.json" to OTHER,
            "dive/2026-06-21#1.json" to LATER,
            "dive/2026-06-22#0.json" to FIXED,
            "dive_site.json" to FOUND,
        ),
        MemoryFileStore(emptyMap()),
        Matching.NONE,
    )
    return into
}

private fun Universe.waiting(): List<Arriving> =
    arrivingIn(importing!!, logbook, nextNumberIn(logbook))

class DecidingTest {

    @Test
    fun `untouched, a dive that appears to be one already held is merged and the rest imported`() {
        val reviewing = Reviewing()
        val (other, later, fixed) = review().waiting()
        assertNotNull(other.onto)
        assertEquals(Decision.MERGE, decisionOf(other, reviewing))
        assertEquals(Decision.IMPORT, decisionOf(later, reviewing))
        assertEquals(Decision.IMPORT, decisionOf(fixed, reviewing))
    }

    @Test
    fun `a dive with a fix is placed nowhere until somebody says`() {
        val reviewing = Reviewing()
        val fixed = review().waiting().last()
        assertEquals("50.4215, 4.5079", fixed.fix)
        assertEquals(Placing.Nowhere, placingOf(fixed, reviewing))
    }

    @Test
    fun `a decision changed before applying reaches nothing`() {
        val universe = review()
        val reviewing = Reviewing()
        val other = universe.waiting().first()
        reviewing.decisions[other.id] = Decision.SKIP
        reviewing.decisions[other.id] = Decision.IMPORT
        reviewing.placings[universe.waiting().last().id] = Placing.At("gombe")
        assertEquals(3, arrivedIn(universe.importing), "everything is still waiting")
        assertEquals(1, universe.logbook.allOf(Types.DIVE).size, "and nothing has landed")
    }

    @Test
    fun `a row's number follows the decisions above it, and two rows never claim one`() {
        val universe = review()
        val reviewing = Reviewing()
        val waiting = universe.waiting()
        val (other, later, fixed) = waiting
        // Untouched: the overlapping dive is merged and takes no number, so the two after it are
        // 2 and 3, and the merged row says what pressing Import on it would give.
        assertEquals(
            listOf(2, 2, 3),
            numbersOf(waiting, reviewing, 2).values.toList(),
        )
        // Declined, it takes the number and everything after it moves down.
        reviewing.decisions[other.id] = Decision.IMPORT
        assertEquals(
            listOf(2, 3, 4),
            numbersOf(waiting, reviewing, 2).values.toList(),
        )
        // Skipped, it takes none and the row still says what Import would give.
        reviewing.decisions[later.id] = Decision.SKIP
        val numbers = numbersOf(waiting, reviewing, 2)
        assertEquals(3, numbers.getValue(later.id))
        assertEquals(3, numbers.getValue(fixed.id), "the skipped row's number is free for the next")
    }

    @Test
    fun `what applying would do is counted for the reader`() {
        val universe = review()
        val reviewing = Reviewing()
        assertEquals("1 merged, 2 imported.", appliedSaid(universe.waiting(), reviewing))
        reviewing.decisions[universe.waiting()[1].id] = Decision.SKIP
        assertEquals("1 merged, 1 imported, 1 left staged.", appliedSaid(universe.waiting(), reviewing))
        assertEquals("Nothing is waiting.", appliedSaid(emptyList(), reviewing))
    }
}

class AppliedTest {

    @Test
    fun `applying merges, imports and skips as decided, and says how many landed`() {
        val universe = review()
        val reviewing = Reviewing()
        val (_, later, _) = universe.waiting()
        reviewing.decisions[later.id] = Decision.SKIP
        val taken = applied(universe.importing!!, universe.logbook, reviewing)
        assertEquals(2, taken.many)
        assertNull(taken.refusal)
        assertEquals(2, universe.logbook.allOf(Types.DIVE).size, "the merged one is not a third")
        assertEquals(1, arrivedIn(universe.importing), "the skipped one is still waiting")
        val held = assertNotNull(universe.logbook["2026-06-21#0"])
        val profiles = (held.read("profiles") as Result.Usable<*>).value as Map<*, *>
        assertEquals(setOf("perdix_2", "i330r"), profiles.keys, "merged: both computers on one dive")
    }

    @Test
    fun `merge declined, the overlapping dive comes in as one of its own, numbered in turn`() {
        val universe = review()
        val reviewing = Reviewing()
        val other = universe.waiting().first()
        reviewing.decisions[other.id] = Decision.IMPORT
        applied(universe.importing!!, universe.logbook, reviewing)
        assertEquals(4, universe.logbook.allOf(Types.DIVE).size, "held, other, later, fixed")
        val numbered = universe.logbook.allOf(Types.DIVE).mapNotNull {
            ((it.read("dive_number") as? Result.Usable<*>)?.value as? Number)?.toInt()
        }.sorted()
        assertEquals(listOf(1, 2, 3), numbered, "each landed dive took the next number in turn")
    }

    @Test
    fun `placed at a site already held, the dive names it and the fix is dropped`() {
        val universe = review()
        val reviewing = Reviewing()
        val fixed = universe.waiting().last()
        reviewing.placings[fixed.id] = Placing.At("gombe")
        applied(universe.importing!!, universe.logbook, reviewing)
        val dive = assertNotNull(universe.logbook["2026-06-22#0"])
        assertEquals("@gombe", (dive.read("dive_site") as Result.Usable<*>).value.toString())
        assertNull(universe.logbook["unknown_dive_site"], "a fix nobody claimed is no site")
    }

    @Test
    fun `named, the fix becomes a site of its own and lands with the dive`() {
        val universe = review()
        val reviewing = Reviewing()
        val fixed = universe.waiting().last()
        reviewing.placings[fixed.id] = Placing.Named
        reviewing.names[fixed.id] = "Nionplas"
        applied(universe.importing!!, universe.logbook, reviewing)
        val made = universe.logbook.allOf(Types.DIVE_SITE).firstOrNull { titleOf(it) == "Nionplas" }
        assertNotNull(made, "the new site is in the logbook")
    }

    @Test
    fun `a new site with no name is refused, and the review says at which dive`() {
        val universe = review()
        val reviewing = Reviewing()
        val fixed = universe.waiting().last()
        reviewing.placings[fixed.id] = Placing.Named
        val taken = applied(universe.importing!!, universe.logbook, reviewing)
        assertEquals(2, taken.many, "the two before it landed")
        assertTrue("name the new site" in taken.refusal.orEmpty(), taken.refusal)
        assertEquals(fixed.id, reviewing.refusedAt)
        assertEquals(1, arrivedIn(universe.importing), "and it is still waiting to be decided")
    }

    @Test
    fun `typing a name is choosing to make a site, as the review reads it`() {
        // The box says so the moment it is typed into, so the chooser follows it.
        val reviewing = Reviewing()
        val fixed = review().waiting().last()
        reviewing.names[fixed.id] = "Somewhere"
        reviewing.placings[fixed.id] = Placing.Named
        assertEquals(Placing.Named, placingOf(fixed, reviewing))
    }
}

/*
 * One arriving dive asked for alone, as applying asks for each in turn.
 */
class ArrivingOneTest {

    @Test
    fun `a dive asked for alone is the one the whole list holds, number and all`() {
        val universe = review()
        val import = universe.importing!!
        val next = nextNumberIn(universe.logbook)
        val whole = arrivingIn(import, universe.logbook, next)
        for (dive in whole) {
            val alone = arrivingIn(import, universe.logbook, next, only = dive.id).single()
            assertEquals(dive.id, alone.id)
            assertEquals(dive.number, alone.number, "${dive.id} numbered as in the list")
            assertEquals(dive.onto, alone.onto)
            assertEquals(dive.fix, alone.fix)
        }
    }

    @Test
    fun `a dive no longer waiting is not found`() {
        val universe = review()
        val import = universe.importing!!
        assertTrue(arrivingIn(import, universe.logbook, 1, only = "nowhere#0").isEmpty())
    }
}
