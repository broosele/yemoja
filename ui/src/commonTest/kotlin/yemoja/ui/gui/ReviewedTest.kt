package yemoja.ui.gui

import yemoja.data.json.Json
import yemoja.logic.Read
import yemoja.logic.casesOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What a review of the interface layer found, each held to the behaviour it should have had.
 */
class ReviewedTest {

    @Test
    fun `a position keeps its sign and every one of its four decimals`() {
        assertEquals("12.0503, 4.5079", placeOf(12.0503, 4.5079))
        assertEquals("-0.5000, 0.0050", placeOf(-0.5, 0.005))
        assertEquals("50.4215, -4.5000", placeOf(50.4215, -4.5))
    }

    @Test
    fun `a decimal number of minutes rounds to the second rather than cutting it short`() {
        assertEquals(246L, secondsOf("4.1"), "4.1 minutes is 246 seconds, not 245")
        assertEquals(-246L, secondsOf("-4.1"))
        assertEquals(150L, secondsOf("2:30"))
    }

    @Test
    fun `a case naming a cylinder it has not got is refused, not quietly breathed from the line above`() {
        fun read(json: String) = casesOf(Json.parse(json))
        val beyond = assertIs<Read.Wrong>(
            read("""{"runtime": [{"depth": 20, "duration": 10, "gas": 3}], "gases": [{"gas": "air"}, {"gas": "EAN50"}]}"""),
        )
        assertTrue("one of the 2 cylinders, not 3" in beyond.reason, beyond.reason)
        val typo = assertIs<Read.Wrong>(read("""{"runtime": [{"depth": 20, "duration": 10, "gas": "two"}]}"""))
        assertTrue("not two" in typo.reason, typo.reason)
        assertIs<Read.Cases>(
            read("""{"runtime": [{"depth": 20, "duration": 10, "gas": 2}], "gases": [{"gas": "air"}, {"gas": "EAN50"}]}"""),
        )
    }

    @Test
    fun `taking out a list entry moves what the ones after it were given up with them`() {
        val givens = mapOf(0 to "@anna", 1 to "@bo", 2 to "@cy")
        assertEquals(mapOf(0 to "@bo", 1 to "@cy"), withoutEntry(givens, 0), "the third is not lost")
        assertEquals(mapOf(0 to "@anna", 1 to "@cy"), withoutEntry(givens, 1))
        assertEquals(mapOf(0 to "@anna", 1 to "@bo"), withoutEntry(givens, 2))
        assertEquals(mapOf(1 to "@cy"), withoutEntry(mapOf(2 to "@cy"), 0), "an entry never given keeps its gap")
    }

    @Test
    fun `the tab a form has open is kept on the draft, which outlives a redraw`() {
        val draft = Draft()
        draft.tabs["dive.gas_sources"] = 2
        assertEquals(2, draft.tabs["dive.gas_sources"])
        assertEquals(null, draft.tabs["dive.profiles"], "each collection its own")
    }

    @Test
    fun `a suggested field offers what the logbook holds, not only what ships with it`() {
        val store = yemoja.data.json.MemoryFileStore(
            mapOf("dive_site.json" to """{"quarry": {"facilities": ["boat access"]}}"""),
        )
        val universe = yemoja.logic.Universe(
            yemoja.data.json.LogbookReader.read(store, yemoja.logic.Types.ALL), null, store,
        )
        val facilities = yemoja.logic.Types.DIVE_SITE["facilities"] as yemoja.data.TextDescription
        val offered = Changer(universe).suggested(facilities)
        assertTrue("parking" in offered, "what ships with it")
        assertTrue("boat access" in offered, "and what another site was given")
        assertTrue("boat access" !in Changer(null).suggested(facilities), "no logbook, presets alone")
    }
}
