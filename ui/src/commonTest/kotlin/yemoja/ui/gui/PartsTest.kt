package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Where what a field says leads: a reference to its item, and nothing else anywhere.
 * See ../../../../../../gui/doc.md — `GUI-28`.
 */
class PartsTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "person.json" to
                    """{"anna": {"first_name": "Anna"}, "bram": {"first_name": "Bram"}}""",
                "dive_site.json" to """{"blue_hole": {"name": "Blue Hole"}}""",
                "dive/2026-06-21#0.json" to
                    """{"dive_site": "@blue_hole", "buddies": ["@anna", "@bram", "@gone"],
                       "max_depth": 30.0, "rating": 7}""",
            ),
        ),
        Types.ALL,
    )

    private val dive = set["2026-06-21#0"]!!

    private fun shown(field: String): Shown = shownOf(dive.description[field]!!, dive)!!

    @Test
    fun `a reference leads to the item it names`() {
        val part = shown("dive_site").parts.single()
        assertEquals("Blue Hole", part.text)
        assertEquals("blue_hole", part.leadsTo)
    }

    @Test
    fun `a list leads entry by entry, and the commas between lead nowhere`() {
        val parts = shown("buddies").parts
        assertEquals(listOf("Anna", ", ", "Bram", ", ", "@gone"), parts.map { it.text })
        assertEquals(listOf("anna", null, "bram", null, null), parts.map { it.leadsTo })
    }

    @Test
    fun `a region's children are left to the tree, and everything else is shown`() {
        val names = fieldsShownOf(Types.REGION).map { it.name }
        assertEquals(Types.REGION.fields.size - 1, names.size)
        assertEquals(false, "children" in names)
        assertEquals(Types.DIVE.fields.size, fieldsShownOf(Types.DIVE).size)
    }

    @Test
    fun `a value that is not a reference leads nowhere`() {
        assertNull(shown("max_depth").parts.single().leadsTo)
    }

    @Test
    fun `a rating is a rating, and nothing else is`() {
        assertEquals(7, shown("rating").rating)
        assertEquals("7", shown("rating").text, "read straight through, it is still the number")
        assertNull(shown("max_depth").rating)
    }

    @Test
    fun `a rating out of ten is five stars, two points a star, an odd one ending in a half`() {
        val (f, h, e) = Triple(Star.FULL, Star.HALF, Star.EMPTY)
        assertEquals(listOf(f, f, f, h, e), starsOf(7))
        assertEquals(listOf(f, f, f, f, f), starsOf(10))
        assertEquals(listOf(h, e, e, e, e), starsOf(1))
        assertEquals(listOf(f, f, f, f, f), starsOf(12), "above the range is all of them")
        assertEquals(listOf(e, e, e, e, e), starsOf(0), "below it is none")
    }
}

class DisplayTest {

    private val depth = Types.DIVE["max_depth"]!!
    private val duration = Types.DIVE["duration"]!!

    @Test
    fun `a time reads as minutes and seconds, however long`() {
        assertEquals("61:16", clockOf(3676.0))
        assertEquals("121:05", clockOf(7265.0))
        assertEquals("0:05", clockOf(5.0))
        assertEquals("-60:00", clockOf(-3600.0))
        assertEquals("61:16", displayOf(duration, 3676.0))
    }

    @Test
    fun `a depth reads to one decimal, no trailing zero, and its unit after it`() {
        assertEquals("12.3 m", displayOf(depth, 12.345))
        assertEquals("12 m", displayOf(depth, 12.0))
        assertEquals("12.4 m", displayOf(depth, 12.35))
        assertEquals("12.3", numberOf(depth, 12.345), "the number alone, for a range")
    }

    @Test
    fun `an angle keeps the file's own precision, a coordinate being nothing to round`() {
        val latitude = Types.DIVE_SITE["latitude"]!!
        assertEquals("51.643556 °", displayOf(latitude, 51.643556))
    }

    @Test
    fun `a time carries no unit, being a clock, and a whole number none either`() {
        assertEquals("", unitOf(duration))
        assertEquals("", unitOf(Types.DIVE["dive_number"]!!))
        assertEquals("m", unitOf(depth))
    }
}

class ArrangedTest {

    @Test
    fun `an item view flows the plain fields and sets each owned item in an inset, in order`() {
        val dive = arrangedOf(Types.DIVE)
        assertEquals(
            listOf("details", "environment", "gear", "profiles", "gas_sources"),
            dive.insets.map { it.name },
        )
        assertEquals(false, dive.plain.any { it.name in dive.insets.map { i -> i.name } })
        assertEquals(Types.DIVE.fields.size, dive.plain.size + dive.insets.size)
    }

    @Test
    fun `a tab is called by the entry's name, else by what it points at, else by its key`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"perdix": {"name": "Perdix 2"}}""",
                    "dive/2026-06-21#0.json" to """{"profiles": {
                        "p1": {"dive_computer": "@perdix", "depth": [[0, 0]]},
                        "p2": {"depth": [[0, 0]]}
                    }}""",
                ),
            ),
            Types.ALL,
        )
        val dive = set["2026-06-21#0"]!!
        val read = (dive.read("profiles") as Result.Usable<*>).value
        @Suppress("UNCHECKED_CAST")
        val profiles = read as Map<String, Element<Any>>
        val p1 = (profiles.getValue("p1") as Element.Usable).value as OwnedItem
        val p2 = (profiles.getValue("p2") as Element.Usable).value as OwnedItem
        assertEquals("Perdix 2", entryLabelOf("p1", p1), "the computer that made it")
        assertEquals("p2", entryLabelOf("p2", p2), "nothing on it says a thing")
    }
}
