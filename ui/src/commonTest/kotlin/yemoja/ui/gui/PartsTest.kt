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
                       "max_depth": 30.0}""",
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
}
