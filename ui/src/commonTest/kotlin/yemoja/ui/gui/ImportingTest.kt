package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Matching
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Taking in another logbook. See ../../../../../../gui/doc.md — `GUI-33`.
 */
class ImportingTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    private fun arriving(vararg files: Pair<String, String>) =
        LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

    private fun staged(
        into: Universe,
        matching: Matching,
        vararg files: Pair<String, String>,
    ) = into.also {
        it.importFrom(arriving(*files), MemoryFileStore(emptyMap()), matching)
    }.importing!!

    @Test
    fun `what arrives besides the dives is counted by type`() {
        val import = staged(
            logbook(),
            Matching.BY_ID,
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
            "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries"},
                "bo": {"first_name": "Bo", "last_name": "Lund"}}""",
            "dive/2026-06-21#0.json" to """{"max_depth": 30.0}""",
        )
        val counted = countedIn(import)
        assertEquals(listOf("Dive site", "Person"), counted.map { it.type }, "in the model's order")
        assertEquals(listOf(1, 2), counted.map { it.many })
        assertEquals(1, divesIn(import), "and a dive is reviewed rather than counted")
    }

    @Test
    fun `what answers to something already held is counted apart`() {
        val into = logbook("dive_site.json" to """{"blue": {"name": "Blue Hole"}}""")
        val import = staged(
            into,
            Matching.BY_ID,
            "dive_site.json" to
                """{"blue": {"name": "Blue Hole"}, "quarry": {"name": "The Quarry"}}""",
        )
        val counted = countedIn(import).single()
        assertEquals(2, counted.many)
        assertEquals(1, counted.known, "the one this logbook already has under that id")
    }

    @Test
    fun `nothing can be matched where the source has no ids of its own`() {
        val into = logbook("dive_site.json" to """{"blue": {"name": "Blue Hole"}}""")
        val import = staged(
            into,
            Matching.NONE,
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
        )
        assertEquals(0, countedIn(import).single().known, "a minted id names no particular item")
        assertEquals("Dive sites", countedIn(import).single().several)
    }

    @Test
    fun `the summary says what else is coming, and nothing where only dives are`() {
        assertNull(summaryOf(emptyList()))
        val said = summaryOf(
            listOf(Counted("Person", "People", 2, 0), Counted("Dive site", "Dive sites", 3, 1)),
        )
        assertEquals("Also arriving: 2 people, 3 dive sites (1 already held).", said)
        val one = summaryOf(listOf(Counted("Person", "People", 1, 0)))!!
        assertTrue("1 person." in one, "one of them is a person rather than a people: $one")
    }
}

/*
 * A dive arriving with a number of its own keeps it, which a downloaded one never has.
 */
class ArrivingNumberTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    private fun arriving(vararg files: Pair<String, String>) =
        LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

    @Test
    fun `a dive answering to one already held says so`() {
        val into = logbook("dive/2026-06-21#0.json" to """{"max_depth": 30.0}""")
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"max_depth": 31.0}""",
                "dive/2026-06-22#0.json" to """{"max_depth": 18.0}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.BY_ID,
        )
        val held = arrivingIn(into.importing!!, into.logbook, nextNumberIn(into.logbook))
        assertEquals(listOf(true, false), held.map { it.held })
    }

    @Test
    fun `a site with a name is not a question, only a fix without one is`() {
        val into = logbook()
        into.importFrom(
            arriving(
                "dive_site.json" to """{"blue": {"name": "Blue Hole", "latitude": 12.0,
                    "longitude": 34.0}, "unknown_dive_site": {"latitude": 12.5,
                    "longitude": 34.5}}""",
                "dive/2026-06-21#0.json" to """{"dive_site": "@blue"}""",
                "dive/2026-06-22#0.json" to """{"dive_site": "@unknown_dive_site"}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val held = arrivingIn(into.importing!!, into.logbook, nextNumberIn(into.logbook))
        assertEquals(listOf("blue", "unknown_dive_site"), held.map { it.site }, "both come in")
        assertNull(held[0].fix, "but a dive that knows where it was is not asked")
        assertEquals("12.5000, 34.5000", held[1].fix, "and one that does not is")
    }

    @Test
    fun `a dive that carries its own number keeps it, and hands none out`() {
        val into = logbook("dive/2026-01-01#0.json" to """{"dive_number": 7}""")
        into.importFrom(
            arriving(
                "dive/2026-06-21#0.json" to """{"dive_number": 40, "start_date": "2026-06-21"}""",
                "dive/2026-06-22#0.json" to """{"start_date": "2026-06-22"}""",
            ),
            MemoryFileStore(emptyMap()),
            Matching.NONE,
        )
        val held = arrivingIn(into.importing!!, into.logbook, nextNumberIn(into.logbook))
        assertEquals(listOf(40, 8), held.map { it.number })
    }
}
