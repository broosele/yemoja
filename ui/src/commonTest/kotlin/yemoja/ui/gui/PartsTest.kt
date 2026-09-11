package yemoja.ui.gui

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
