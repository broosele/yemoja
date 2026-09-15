package yemoja.ui.gui

import yemoja.data.Item
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What an item and its fields read as. See ../../../../../../gui/doc.md.
 */

private fun logbook(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun shownOf(item: Item, field: String): Shown? =
    shownOf(item.description[field]!!, item)

class ShownTest {

    private val set = logbook(
        "gear.json" to """{"perdix": {"name": "Perdix 2", "serial": "6A9191A5"}}""",
        "dive/2026-06-21#0.json" to """{"dive_number": 42, "buddies": ["@anna", "@bram"],
            "environment": {"visibility": 5},
            "profiles": {"p1": {"start_date": "2026-06-21", "start_time": "10:00:00",
                "depth": [[0, 0], [60, 12.0], [120, 0]]}}}""",
        "person.json" to """{"anna": {"first_name": "Anna"}, "bram": {"first_name": "Bram"}}""",
    )

    private val dive = set["2026-06-21#0"]!!

    @Test
    fun `a type reads as a label rather than as a field name`() {
        assertEquals("Dive site", labelOf(Types.DIVE_SITE))
        assertEquals("Gear", labelOf(Types.GEAR))
    }

    @Test
    fun `an item is titled by its name, never by its id`() {
        assertEquals("Perdix 2", titleOf(set["perdix"]!!))
        // A dive's name is worked out to be its id, which is a date and a number within the
        // day, so a reader sees a date rather than a file name. `Dive.kt` decides that.
        assertEquals("2026-06-21#0", titleOf(dive))
    }

    @Test
    fun `a field nobody filled in says nothing at all`() {
        assertNull(shownOf(dive, "rating"))
    }

    @Test
    fun `a list with nothing in it says nothing, worked out or written`() {
        // A worked-out list always answers, so a site nobody dived had "Dives: (empty)".
        val held = logbook(
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
            "person.json" to """{"anna": {"first_name": "Anna",
                "courses": {"k1": {"number": "7", "dives": []}}}}""",
        )
        assertNull(shownOf(held["blue"]!!, "dives"), "worked out, and empty")
        val course = keyedEntriesOf(held["anna"]!!, "courses").single().second
        assertNull(shownOf(course, "dives"), "written, and empty")
    }

    @Test
    fun `a value says what a file would write, under the field's own label`() {
        val shown = shownOf(dive, "dive_number")!!
        assertEquals("Dive number", shown.label)
        assertEquals("42", shown.text)
        assertTrue(!shown.worked && !shown.wrong)
    }

    @Test
    fun `a reference says the name of what it points at, never the id`() {
        assertEquals("Anna, Bram", shownOf(dive, "buddies")!!.text)
        val perdix = set["perdix"]!!
        assertEquals("Perdix 2", titleOf(perdix))
    }

    @Test
    fun `a reference to nothing keeps what was written, the spelling being all there is`() {
        val dangling = logbook("dive/2026-06-21#0.json" to """{"dive_site": "@gone"}""")
        assertEquals("@gone", shownOf(dangling["2026-06-21#0"]!!, "dive_site")!!.text)
    }

    @Test
    fun `a long list says every entry, each being a link that a count would fold away`() {
        val many = (1..9).joinToString(", ") { "\"@p$it\"" }
        val people = (1..9).joinToString(", ") { "\"p$it\": {\"first_name\": \"P$it\"}" }
        val big = logbook(
            "person.json" to "{$people}",
            "dive/2026-06-21#0.json" to """{"buddies": [$many]}""",
        )
        assertEquals(
            "P1, P2, P3, P4, P5, P6, P7, P8, P9",
            shownOf(big["2026-06-21#0"]!!, "buddies")!!.text,
        )
    }

    @Test
    fun `a series says how many samples`() {
        val profile = set["2026-06-21#0"]!!
        assertEquals("1 entry", shownOf(profile, "profiles")!!.text)
    }

    @Test
    fun `an owned item says how many of its fields say anything`() {
        assertEquals("1 field", shownOf(dive, "environment")!!.text)
    }

    @Test
    fun `a worked-out value is marked as one`() {
        // Nothing wrote the dive's date; it comes from the recording. `GUI-16`.
        val shown = shownOf(dive, "start_date")!!
        assertEquals("2026-06-21", shown.text)
        assertTrue(shown.worked, "nobody wrote it")
        assertTrue(!shownOf(dive, "dive_number")!!.worked, "somebody wrote this one")
    }

    @Test
    fun `a value that will not read shows the reason rather than the value`() {
        val broken = logbook("gear.json" to """{"x": {"name": "X", "capacity": "wide"}}""")
        val shown = shownOf(broken["x"]!!, "capacity")!!
        assertTrue(shown.wrong)
        assertEquals("capacity should be a number", shown.text, "the reason, not the value")
    }
}
