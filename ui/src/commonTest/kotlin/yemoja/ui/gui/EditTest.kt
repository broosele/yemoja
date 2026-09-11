package yemoja.ui.gui

import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Change
import yemoja.logic.Types
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
}
