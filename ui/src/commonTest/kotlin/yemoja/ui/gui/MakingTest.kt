package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.titleOf
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

    private val dive = TABS.first { it.name == "Dives" }
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
    fun `a tab offers every type it holds, the one being looked at first`() {
        // A logbook with no trip in it has no trip to press + on, so the tab has to offer one.
        assertEquals(listOf(Types.DIVE, Types.DIVE_TRIP), makeableIn(dive, null))
        val held = set("dive_trip.json" to """{"egypt": {"name": "Egypt"}}""")
        assertEquals(listOf(Types.DIVE_TRIP, Types.DIVE), makeableIn(dive, chosen(held, "egypt")))
        assertEquals(
            listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION),
            makeableIn(community, null),
        )
    }

    @Test
    fun `a tab holding no types makes nothing, and offers none`() {
        assertEquals(emptyList(), makeableIn(TABS.first { it.name == "Home" }, null))
    }

    @Test
    fun `a tab holding no types makes nothing`() {
        assertNull(makingOf(TABS.first { it.name == "Home" }, null))
    }

    @Test
    fun `a new piece of gear starts out filed where the tree was`() {
        // A reader looking at the cylinders who presses + is adding a cylinder.
        assertEquals(mapOf("category" to "cylinder"), startedOf(Types.GEAR, "cylinder"))
        assertEquals(
            mapOf("category" to "cylinder", "kind" to "steel"),
            startedOf(Types.GEAR, "cylinder/steel"),
        )
        assertEquals(emptyMap(), startedOf(Types.GEAR, null), "no branch chosen, nothing filled")
        assertEquals(emptyMap(), startedOf(Types.PERSON, "cylinder"), "only gear has such a tree")
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
    fun `the references a delete would leave dangling are counted, and where few, named`() {
        // A reference to something deleted is left dangling rather than hunted down.
        assertEquals(
            "2 other items name it, and will be left naming something that is no longer here: 2026-06-21#0 and " +
                    "2026-06-22#0.",
            deleteWarned(logbook, setOf("anna")),
        )
        assertEquals(
            "3 other items name them, and will be left naming something that is no longer here: 2026-06-21#0 and " +
                    "2026-06-22#0.",
            deleteWarned(logbook, setOf("anna", "bo")),
            "two references in one dive is one name, not two",
        )
    }

    @Test
    fun `one reference reads as one`() {
        assertEquals(
            "One other item names it, and will be left naming something that is no longer here: 2026-06-21#0.",
            deleteWarned(logbook, setOf("blue")),
        )
    }

    @Test
    fun `a derived reference is not counted, putting itself right`() {
        // A site's dives and a person's dives follow from the dives naming them, so deleting the
        // last dive leaves nothing dangling at all.
        val held = set(
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
            "person.json" to """{"anna": {"first_name": "Anna"}}""",
            "dive/2026-06-21#0.json" to """{"dive_site": "@blue", "buddies": ["@anna"]}""",
        )
        assertNull(deleteWarned(held, setOf("2026-06-21#0")), "nothing stored points at it")
        assertEquals(
            "One other item names it, and will be left naming something that is no longer here: 2026-06-21#0.",
            deleteWarned(held, setOf("blue")),
            "and the dive's own dive_site, which is stored, still counts",
        )
    }

    @Test
    fun `too many to check are counted and not named`() {
        val crowd = set(
            "person.json" to """{"anna": {"first_name": "Anna"}}""",
            "dive/2026-06-21#0.json" to """{"buddies": ["@anna"]}""",
            "dive/2026-06-22#0.json" to """{"buddies": ["@anna"]}""",
            "dive/2026-06-23#0.json" to """{"buddies": ["@anna"]}""",
            "dive/2026-06-24#0.json" to """{"buddies": ["@anna"]}""",
        )
        assertEquals(
            "4 other items name it, and will be left naming something that is no longer here.",
            deleteWarned(crowd, setOf("anna")),
            "four names is a wall rather than a question",
        )
    }

    @Test
    fun `nothing pointing warns of nothing`() {
        val alone = set("person.json" to """{"cy": {"first_name": "Cy"}}""")
        assertNull(deleteWarned(alone, setOf("cy")))
    }
}

/*
 * The add, edit and delete buttons on the tab row. See ../../../../../../gui/doc.md — `GUI-53`.
 */
class RibbonTest {

    private val home = TABS.first { it.name == "Home" }
    private val dives = TABS.first { it.name == "Dives" }
    private val places = TABS.first { it.name == "Locations" }
    private val held = set(
        "region.json" to """{"egypt": {"name": "Egypt"}}""",
        "dive_site.json" to """{"elph": {"name": "Elphinstone", "regions": ["@egypt"]}}""",
        "dive/2026-06-01#0.json" to """{"rating": 4}""",
        "dive/2026-06-02#0.json" to """{"rating": 5}""",
    )

    @Test
    fun `a tab holding no items greys all three, and says why`() {
        val ribbon = ribbonOf(home, Kept())
        assertTrue(ribbon.makeable.isEmpty())
        assertNull(ribbon.edited)
        assertTrue(ribbon.deleted.isEmpty())
        assertTrue(ribbon.addWhy != null && ribbon.editWhy != null && ribbon.deleteWhy != null)
    }

    @Test
    fun `with nothing chosen only plus can be pressed`() {
        val ribbon = ribbonOf(dives, Kept())
        assertNull(ribbon.addWhy)
        assertEquals(Types.DIVE, ribbon.makeable.first())
        assertEquals("Choose something to edit.", ribbon.editWhy)
        assertEquals("Choose something to delete.", ribbon.deleteWhy)
    }

    @Test
    fun `the chosen item is what the pencil and the bin act on`() {
        val kept = Kept().apply { chosen = chosen(held, "2026-06-01#0") }
        val ribbon = ribbonOf(dives, kept)
        assertEquals("2026-06-01#0", ribbon.edited?.id)
        assertEquals(setOf("2026-06-01#0"), ribbon.deleted)
    }

    @Test
    fun `several dives are deleted together and edited one at a time`() {
        val kept = Kept().apply { chosenMany = setOf("2026-06-01#0", "2026-06-02#0") }
        val ribbon = ribbonOf(dives, kept)
        assertNull(ribbon.edited)
        assertTrue("Choose one" in ribbon.editWhy!!, ribbon.editWhy)
        assertEquals(setOf("2026-06-01#0", "2026-06-02#0"), ribbon.deleted)
    }

    @Test
    fun `on Locations the site chosen comes before the region it is in`() {
        val kept = Kept().apply { place = chosen(held, "egypt") }
        assertEquals("egypt", ribbonOf(places, kept).edited?.id, "the region where nothing else is")
        kept.chosen = chosen(held, "elph")
        assertEquals("elph", ribbonOf(places, kept).edited?.id)
        assertEquals(setOf("elph"), ribbonOf(places, kept).deleted)
    }

    @Test
    fun `an open form keeps all three waiting`() {
        val editing = Kept().apply {
            chosen = chosen(held, "2026-06-01#0")
            this.editing = "2026-06-01#0"
        }
        val making = Kept().apply { this.making = Types.DIVE }
        for (kept in listOf(editing, making)) {
            val ribbon = ribbonOf(dives, kept)
            assertTrue(ribbon.makeable.isEmpty() && ribbon.edited == null && ribbon.deleted.isEmpty())
            assertEquals("Save or cancel the form first.", ribbon.addWhy)
        }
    }
}
