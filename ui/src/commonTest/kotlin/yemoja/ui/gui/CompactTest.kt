package yemoja.ui.gui

import yemoja.data.ReferenceableItem
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.titleOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/*
 * A phone's screen, one page at a time. See ../../../../../../gui/phone/doc.md — `PHONE-2`.
 */
class CompactTest {

    private val dives = TABS.first { it.name == "Dives" }
    private val places = TABS.first { it.name == "Locations" }
    private val held = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "region.json" to """{"egypt": {"name": "Egypt"}}""",
                "dive_site.json" to """{"elph": {"name": "Elphinstone", "regions": ["@egypt"]}}""",
                "dive/2026-06-01#0.json" to """{"rating": 4}""",
            ),
        ),
        Types.ALL,
    )

    private fun chosen(id: String): Chosen =
        Chosen(id, titleOf(held[id]!!), held[id] as ReferenceableItem)

    @Test
    fun `a tab opens on its list, and back has nowhere to go`() {
        val kept = Kept()
        assertEquals(Page.LIST, pageOf(dives, kept))
        assertNull(backOf(dives, kept))
    }

    @Test
    fun `an item chosen fills the screen, and back returns to the list`() {
        val kept = Kept().apply { chosen = chosen("2026-06-01#0") }
        assertEquals(Page.ITEM, pageOf(dives, kept))
        assertNotNull(backOf(dives, kept)).invoke()
        assertNull(kept.chosen)
        assertEquals(Page.LIST, pageOf(dives, kept))
    }

    @Test
    fun `back leaves a form before the item it was opened over`() {
        val kept = Kept().apply {
            chosen = chosen("2026-06-01#0")
            editing = "2026-06-01#0"
        }
        assertNotNull(backOf(dives, kept)).invoke()
        assertNull(kept.editing, "the form is cancelled")
        assertEquals("2026-06-01#0", kept.chosen?.id, "and the item is still there")
        val making = Kept().apply { this.making = Types.DIVE }
        assertEquals(Page.ITEM, pageOf(dives, making))
        assertNotNull(backOf(dives, making)).invoke()
        assertNull(making.making)
    }

    @Test
    fun `Locations steps from a site to its region to the regions`() {
        val kept = Kept().apply {
            place = chosen("egypt")
            chosen = chosen("elph")
        }
        assertEquals(Page.ITEM, pageOf(places, kept))
        backOf(places, kept)!!.invoke()
        assertEquals(Page.PLACE, pageOf(places, kept), "the region the site was chosen from")
        backOf(places, kept)!!.invoke()
        assertEquals(Page.LIST, pageOf(places, kept), "the regions themselves")
        assertNull(backOf(places, kept))
    }

    @Test
    fun `the places with no region are a page of their own too`() {
        val kept = Kept().apply { unplaced = true }
        assertEquals(Page.PLACE, pageOf(places, kept))
        backOf(places, kept)!!.invoke()
        assertEquals(Page.LIST, pageOf(places, kept))
    }

    @Test
    fun `several dives are a page, and back lets them go`() {
        val kept = Kept().apply { chosenMany = setOf("2026-06-01#0", "2026-06-02#0") }
        assertEquals(Page.ITEM, pageOf(dives, kept))
        backOf(dives, kept)!!.invoke()
        assertEquals(emptySet(), kept.chosenMany)
    }

    @Test
    fun `Calculations opens on the list of them, and back leads from a form to the list`() {
        val calculations = TABS.first { it.name == "Calculations" }
        val kept = Kept()
        assertNull(backOf(calculations, kept), "the list is as far back as the tab goes")
        kept.working.calculation = Calculation.MIX
        kept.working.inForm = true
        kept.working.mixing.startPressure = "100"
        assertNotNull(backOf(calculations, kept)).invoke()
        assertEquals(false, kept.working.inForm)
        assertEquals("100", kept.working.mixing.startPressure, "what was typed is kept")
        assertNull(backOf(calculations, kept))
    }

    @Test
    fun `fields stand one to a row on a phone`() {
        val rows = rowsOf(listOf("a", "b", "c"), 1) { false }
        assertEquals(listOf(listOf("a"), listOf("b"), listOf("c")), rows)
    }
}
