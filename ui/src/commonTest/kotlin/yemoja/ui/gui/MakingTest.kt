package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Making an item and unmaking one. See ../../../../../../gui/doc.md — `GUI-35`.
 */
private fun set(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun chosen(held: yemoja.data.ItemSet, id: String) =
    Chosen(id, titleOf(held[id]!!), held[id] as yemoja.data.ReferenceableItem)

class MadeTypeTest {

    private val dive = TABS.first { it.name == "Dive" }
    private val community = TABS.first { it.name == "Community" }

    @Test
    fun `plus makes another of what is being looked at`() {
        val held = set("person.json" to """{"anna": {"first_name": "Anna"}}""")
        assertEquals(Types.PERSON, makingOf(community, chosen(held, "anna")))
    }

    @Test
    fun `a trip chosen on the dive tab makes another trip, not another dive`() {
        val held = set("dive_trip.json" to """{"egypt": {"name": "Egypt"}}""")
        assertEquals(Types.DIVE_TRIP, makingOf(dive, chosen(held, "egypt")))
    }

    @Test
    fun `with nothing chosen it makes the first type the tab holds`() {
        assertEquals(Types.DIVE, makingOf(dive, null), "the table's own type")
        assertEquals(Types.PERSON, makingOf(community, null))
    }

    @Test
    fun `a tab holding no types makes nothing`() {
        assertNull(makingOf(TABS.first { it.name == "Home" }, null))
    }

    @Test
    fun `the button says what it makes`() {
        assertEquals("Add a dive", makeSaid(Types.DIVE))
        assertEquals("Add a dive site", makeSaid(Types.DIVE_SITE))
    }
}

class DeleteAskedTest {

    private val logbook = set(
        "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries"},
            "bo": {"first_name": "Bo", "last_name": "Lund"}}""",
        "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
        "dive/2026-06-21#0.json" to """{"dive_site": "@blue", "buddies": ["@anna"]}""",
        "dive/2026-06-22#0.json" to """{"buddies": ["@anna", "@bo"]}""",
    )

    @Test
    fun `one is named, since a reader wants to see which`() {
        assertEquals("Delete Blue Hole?", deleteAsked(logbook, setOf("blue")))
    }

    @Test
    fun `several are counted, a list nobody can check being no help`() {
        assertEquals("Delete 2 people?", deleteAsked(logbook, setOf("anna", "bo")))
    }

    @Test
    fun `several of different types are counted as items`() {
        assertEquals("Delete 2 items?", deleteAsked(logbook, setOf("anna", "blue")))
    }

    @Test
    fun `nothing to delete asks nothing`() {
        assertNull(deleteAsked(logbook, emptySet()))
        assertNull(deleteAsked(logbook, setOf("nobody")))
    }

    @Test
    fun `what points at it is counted, and will go on pointing`() {
        // A reference to something deleted is left dangling rather than hunted down.
        val warned = deleteWarned(logbook, setOf("anna"))!!
        assertTrue(warned.startsWith("2 items name them"), warned)
        assertTrue("no longer there" in warned)
    }

    @Test
    fun `one thing pointing reads as one`() {
        val warned = deleteWarned(logbook, setOf("blue"))!!
        assertTrue(warned.startsWith("One item names it"), warned)
    }

    @Test
    fun `nothing pointing warns of nothing`() {
        val alone = set("person.json" to """{"cy": {"first_name": "Cy"}}""")
        assertNull(deleteWarned(alone, setOf("cy")))
    }
}
