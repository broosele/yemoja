package yemoja.ui.gui

import yemoja.data.json.Json
import yemoja.ui.api.Read
import yemoja.ui.api.casesOf
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
            read("""{"lines": [{"depth": 20, "duration": 10, "gas": 3}], "gases": [{"gas": "air"}, {"gas": "EAN50"}]}"""),
        )
        assertTrue("one of the 2 cylinders, not 3" in beyond.reason, beyond.reason)
        val typo = assertIs<Read.Wrong>(read("""{"lines": [{"depth": 20, "duration": 10, "gas": "two"}]}"""))
        assertTrue("not two" in typo.reason, typo.reason)
        assertIs<Read.Cases>(
            read("""{"lines": [{"depth": 20, "duration": 10, "gas": 2}], "gases": [{"gas": "air"}, {"gas": "EAN50"}]}"""),
        )
    }
}
