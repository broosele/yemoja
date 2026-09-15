package yemoja.ui.gui

import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Change
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.Types
import yemoja.logic.Universe
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What an edit form hands the model. See ../../../../../../gui/doc.md — `GUI-29`.
 */
class EditTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "person.json" to """{"anna": {"first_name": "Anna"}}""",
                "dive/2026-06-21#0.json" to
                    """{"dive_site": "@blue", "buddies": ["@anna", "Jo"], "rating": 7,
                       "profiles": {"p": {"depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
            ),
        ),
        Types.ALL,
    )

    private val dive = set["2026-06-21#0"]!!

    private fun field(name: String) = Types.DIVE[name]!!

    @Test
    fun `each kind of field is edited its own way`() {
        assertEquals(Kind.NUMBER, kindOf(field("max_depth")))
        assertEquals(Kind.CLOCK, kindOf(field("duration")))
        assertEquals(Kind.RATING, kindOf(field("rating")))
        assertEquals(Kind.WHOLE, kindOf(field("dive_number")))
        assertEquals(Kind.DATE, kindOf(field("start_date")))
        assertEquals(Kind.YES_NO, kindOf(field("deco")))
        assertEquals(Kind.REFERENCE, kindOf(field("dive_site")))
        assertEquals(Kind.KEY, kindOf(field("primary_profile")))
        assertEquals(Kind.CHOICE, kindOf(Types.DIVE_SITE["water_type"]!!))
        assertEquals(Kind.SUGGESTED, kindOf(field("entry")), "offered, not enforced")
        assertEquals(Kind.LONG_TEXT, kindOf(field("remarks")))
        assertEquals(Kind.NONE, kindOf(profile()["depth"]!!), "a series is read on the graph")
    }

    private fun profile() = (field("profiles") as yemoja.data.OwnedItemDescription).description

    @Test
    fun `what is worked out is not edited, and what is worked out unless told otherwise is`() {
        assertTrue(!editable(field("name")), "a dive's name is its id")
        assertTrue(editable(field("max_depth")) && overrideable(field("max_depth")))
        assertTrue(editable(field("rating")) && !overrideable(field("rating")))
    }

    @Test
    fun `a value is edited as the text a file holds it as, a time as a clock`() {
        assertEquals("@blue", textOf(field("dive_site"), yemoja.data.Reference.Identified("blue")))
        assertEquals("Jo", textOf(field("buddies"), yemoja.data.Reference.OneOff("Jo")))
        assertEquals("61:16", textOf(field("duration"), 3676.0))
        assertEquals("12.3", textOf(field("max_depth"), 12.345))
        assertEquals("", textOf(field("max_depth"), null))
    }

    @Test
    fun `a clock typed is seconds given, and a bare number is minutes`() {
        assertEquals("3676", givenOf(Kind.CLOCK, "61:16"))
        assertEquals("2700", givenOf(Kind.CLOCK, "45"))
        assertEquals("-60", givenOf(Kind.CLOCK, "-1:00"))
        // Not a clock, so it is left as typed for the model to refuse.
        assertEquals("1:75", givenOf(Kind.CLOCK, "1:75"))
        assertNull(givenOf(Kind.CLOCK, "  "), "nothing typed is the field cleared")
        assertEquals("12.3", givenOf(Kind.NUMBER, " 12.3 "))
    }

    @Test
    fun `a list's entries are edited as texts`() {
        val buddies = (dive.read("buddies") as yemoja.data.Result.Usable<*>).value
        assertEquals(listOf("@anna", "Jo"), entriesOf(field("buddies"), buddies))
    }

    @Test
    fun `a draft becomes one write per field changed, a list as its entries`() {
        val draft = Draft()
        assertTrue(draft.isEmpty)
        draft.put(dive, "max_depth", "12.3")
        draft.put(dive, "buddies", listOf("@anna"), listOf("@anna"))
        draft.put(dive, "rating", null, null)
        val writes = draft.writes().map { it as Change.Write }.associateBy { it.field }
        assertEquals(setOf("max_depth", "buddies", "rating"), writes.keys)
        assertEquals(Stored.Leaf("12.3"), writes.getValue("max_depth").given)
        assertTrue(writes.getValue("buddies").given is Stored.Elements)
        assertNull(writes.getValue("rating").given, "a clearing is nothing given")
    }

    @Test
    fun `the model's refusal is known before anything is saved`() {
        val draft = Draft()
        draft.put(dive, "max_depth", "deep")
        assertTrue(draft.refusalOf(dive, "max_depth")!!.isNotEmpty())
        draft.put(dive, "max_depth", "12.3")
        assertNull(draft.refusalOf(dive, "max_depth"))
        assertNull(draft.refusalOf(dive, "rating"), "not drafted, nothing to refuse")
    }

    @Test
    fun `a field of an owned item is drafted on that item, told apart by identity`() {
        val draft = Draft()
        val read = (dive.read("profiles") as yemoja.data.Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val profiles = read as Map<String, yemoja.data.Element<Any>>
        val entry = (profiles.getValue("p") as yemoja.data.Element.Usable).value
        val p = entry as yemoja.data.OwnedItem
        draft.put(p, "remarks", "cold")
        assertTrue(draft.changed(p, "remarks"))
        assertTrue(!draft.changed(dive, "remarks"))
        assertEquals(p, (draft.writes().single() as Change.Write).item)
    }

    @Test
    fun `a collection is changed whole, without the entry taken out or with one added`() {
        val two = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive/2026-06-22#0.json" to
                        """{"gas_sources": {"tank_1": {"gas_type": "EAN32"}, "tank_2": {}}}""",
                ),
            ),
            Types.ALL,
        )["2026-06-22#0"]!!
        val without = withoutEntry(two, "gas_sources", "tank_2")
        assertEquals(listOf("tank_1"), without.members.keys.toList())
        val kept = without.members.getValue("tank_1") as Stored.Members
        assertEquals("EAN32", (kept.members.getValue("gas_type") as Stored.Leaf).value)
        val (key, with) = withEntry(two, "gas_sources")
        assertEquals("gas", key, "what a gas source holding nothing is called")
        assertEquals(listOf("tank_1", "tank_2", "gas"), with.members.keys.toList())
    }

    @Test
    fun `every draft on an entry is dropped when the entry is no longer the one held`() {
        val draft = Draft()
        val (_, tank) = keyedEntriesOf(dive, "profiles").single()
        draft.put(tank, "remarks", "cold")
        draft.put(dive, "rating", "8")
        draft.dropAll(tank)
        assertTrue(!draft.changed(tank, "remarks"))
        assertTrue(draft.changed(dive, "rating"))
    }
}

/*
 * Beginning a singular owned item that the logbook has none of.
 * See ../../../../../../gui/doc.md — `GUI-29`.
 */
class BegunTest {

    private fun logbook(vararg files: Pair<String, String>): Universe {
        val store = MemoryFileStore(mapOf(*files))
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    @Test
    fun `a block the form began writes nothing while nothing is typed into it`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        draft.begin(suit, inset)
        assertEquals(emptyList(), draft.writes(), "an empty block is not a change")
    }

    @Test
    fun `a block the form began lands whole once a field is typed into it`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        val block = draft.begin(suit, inset)
        draft.put(block, "mass", "4.2")
        val write = draft.writes().single() as Change.Write
        assertEquals("buoyancy", write.field, "one write, of the block onto its owner")
        assertTrue(write.item === suit)
        val members = (write.given as Stored.Members).members
        assertEquals(listOf("mass"), members.keys.toList(), "holding what was typed and no more")
    }

    @Test
    fun `beginning the same block twice gives the same one back`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        val draft = Draft()
        val inset = suit.description["buoyancy"] as OwnedItemDescription
        assertTrue(draft.begin(suit, inset) === draft.begin(suit, inset), "one block, not two")
    }

    @Test
    fun `writing an empty block gives an item one it did not have`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        val suit = held.logbook["suit"]!!
        assertEquals(Result.Absent, suit.read("buoyancy"), "nothing to fill in yet")
        val done = held.change(
            Operation.EDIT,
            Change.Write(suit, "buoyancy", Stored.Members(emptyMap())),
        )
        assertTrue(done is Outcome.Done, "an empty block is a shape, not a value")
        val begun = held.logbook["suit"]!!.read("buoyancy")
        assertTrue(begun is Result.Usable, "and now there is one")
        val buoyancy = (begun as Result.Usable).value as OwnedItem
        assertEquals(Result.Absent, buoyancy.read("mass"), "empty, with its fields to fill in")
        assertTrue(
            buoyancy.description.fields.any { it.name == "compressible_fraction" },
            "including the ones that were unreachable",
        )
    }

    @Test
    fun `a field written into the begun block lands`() {
        val held = logbook("gear.json" to """{"suit": {"name": "Drysuit", "category": "suit"}}""")
        held.change(
            Operation.EDIT,
            Change.Write(held.logbook["suit"]!!, "buoyancy", Stored.Members(emptyMap())),
        )
        val buoyancy = (held.logbook["suit"]!!.read("buoyancy") as Result.Usable).value as OwnedItem
        val done = held.change(Operation.EDIT, Change.Write(buoyancy, "mass", Stored.Leaf(4.2)))
        assertTrue(done is Outcome.Done)
        val read = (held.logbook["suit"]!!.read("buoyancy") as Result.Usable).value as OwnedItem
        assertEquals(4.2, (read.single<Double>("mass") as Result.Usable).value)
    }
}
