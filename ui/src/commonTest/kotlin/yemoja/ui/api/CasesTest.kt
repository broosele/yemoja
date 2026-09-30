package yemoja.ui.api

import yemoja.data.json.Json
import yemoja.logic.Settings
import yemoja.ui.gui.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * Plans written down as data, and what they come to written back. See
 * ../../../../../../ui/api/doc.md — `API-8`.
 */

private fun read(json: String): Read = casesOf(Json.parse(json))

private fun oneOf(json: String): Case = assertIs<Read.Cases>(read(json)).cases.single()

private const val FORTY = """
    [{"name": "forty", "lines": [{"depth": 40}, {"depth": 40, "duration": "22:46"}],
      "gases": [{"gas": "air", "size": 24, "fill": 232, "sac": 20}],
      "gf_low": 100, "gf_high": 100}]
"""

class CasesTest {

    @Test
    fun `a case is its lines, its cylinders and whatever settings it names`() {
        val case = oneOf(FORTY)
        assertEquals("forty", case.name)
        assertEquals(2, case.planned.segments.size)
        assertEquals("40", case.planned.segments.first().depth)
        assertEquals("22:46", case.planned.segments.last().duration)
        assertEquals("air", case.planned.gases.single().gas)
        assertEquals("100", case.planned.gradientLow)
    }

    @Test
    fun `a number says what a string says, so a file need not remember which is quoted`() {
        val quoted = oneOf(
            """[{"lines": [{"depth": "40", "duration": "10"}], "gf_low": "100", "gf_high": "100"}]""",
        )
        val plain = oneOf("""[{"lines": [{"depth": 40, "duration": 10}], "gf_low": 100, "gf_high": 100}]""")
        assertEquals(quoted.planned.segments, plain.planned.segments)
        assertEquals(quoted.planned.gradientLow, plain.planned.gradientLow)
    }

    @Test
    fun `what a case leaves out the application answers`() {
        val case = oneOf(FORTY)
        assertEquals(
            yemoja.ui.gui.shownOf(Settings.DEFAULT_ASCENT_RATE, Settings.DEFAULT_ASCENT_RATE.default),
            case.planned.ascentRate,
        )
        assertEquals(Settings.DEFAULT_WATER_TYPE.default, case.planned.water)
        assertEquals("1.4", case.planned.bottomOxygen)
    }

    @Test
    fun `a cylinder is named by its number on a line, as it is everywhere else`() {
        val case = oneOf(
            """[{"lines": [{"depth": 21, "gas": 2, "duration": 5}],
                "gases": [{"gas": "air"}, {"gas": "EAN50", "role": "deco"}],
                "gf_low": 30, "gf_high": 70}]""",
        )
        assertEquals(1, case.planned.segments.single().gas, "the second cylinder, counted from nought")
        assertEquals(Role.DECO, case.planned.gases[1].role)
    }

    @Test
    fun `a plan on its own is a file of one`() {
        val cases = assertIs<Read.Cases>(
            read("""{"lines": [{"depth": 20, "duration": 10}], "gf_low": 30, "gf_high": 70}"""),
        ).cases
        assertEquals(1, cases.size)
        assertEquals("case 1", cases.single().name, "and it is called by its place")
    }

    @Test
    fun `a file that is not a list of plans says what one looks like`() {
        assertTrue("a plan or a list of them" in assertIs<Read.Wrong>(read("3")).reason)
        assertTrue("written as a list" in assertIs<Read.Wrong>(read("""[{"name": "x"}]""")).reason)
        assertTrue("has no lines" in assertIs<Read.Wrong>(read("""[{"lines": []}]""")).reason)
        assertTrue(
            "not bottom, deco or bailout" in
                assertIs<Read.Wrong>(
                    read("""[{"lines": [{"depth": 10}], "gases": [{"gas": "air", "role": "spare"}]}]"""),
                ).reason,
        )
    }
}

class ReportedTest {

    private fun schedule(): Schedule =
        assertIs<Calculated.Done>(calculated(oneOf(FORTY).planned)).schedule

    @Test
    fun `a row carries a column for each heading, in order`() {
        val row = rowOf("forty", schedule())
        assertEquals(COLUMNS.size, row.size)
        assertEquals("forty", row.first())
        assertEquals("40", row[COLUMNS.indexOf("max_depth_m")])
        assertEquals("25", row[COLUMNS.indexOf("bottom_minutes")], "up to the last line described")
        assertTrue(row[COLUMNS.indexOf("stop_minutes")].toDouble() > 0)
        assertEquals("", row.last(), "nothing was refused")
    }

    @Test
    fun `the stops read as a depth and the minutes held there, deepest first`() {
        val said = rowOf("forty", schedule())[COLUMNS.indexOf("stops")]
        val depths = said.split(" ").map { it.substringBefore("@").toDouble() }
        assertEquals(depths.sortedDescending(), depths)
        assertTrue(said.endsWith("@${schedule().stops.last().seconds / 60}"), said)
    }

    @Test
    fun `a plan that would not calculate is a row with its reason and nothing else`() {
        val row = rowOf("broken", "Line 1 needs a depth")
        assertEquals(COLUMNS.size, row.size)
        assertEquals("broken", row.first())
        assertEquals("Line 1 needs a depth", row.last())
        assertTrue(row.drop(1).dropLast(1).all { it.isEmpty() })
    }

    @Test
    fun `a field holding a comma or a quote is quoted, so the table still parses`() {
        val table = tableOf(listOf(rowOf("one, two", """he said "no"""")))
        assertTrue("\"one, two\"" in table, table)
        assertTrue("\"\"no\"\"" in table, table)
        assertEquals(COLUMNS.joinToString(","), table.lines().first())
    }

    @Test
    fun `the whole answer holds every line, marked as described or added`() {
        val said = Json.write(saidOf("forty", schedule()))
        assertTrue("\"added\": false" in said, "the lines the caller wrote")
        assertTrue("\"added\": true" in said, "and the way up")
        assertTrue("\"direction\": \"stay\"" in said)
        assertTrue("\"warnings\"" in said)
        assertTrue("\"gas_litres\"" in said)
    }
}
