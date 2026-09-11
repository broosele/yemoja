package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * What several dives say together. See ../../../../../../gui/doc.md — `GUI-23`.
 */
class StatsTest {

    private val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "dive_site.json" to
                    """{"blue": {"name": "Blue Hole"}, "elph": {"name": "Elphinstone"}}""",
                "person.json" to
                    """{"anna": {"first_name": "Anna"}, "bram": {"first_name": "Bram"}}""",
                "dive/2026-06-01#0.json" to """{"dive_number": 1, "dive_site": "@blue", "rating": 6,
                    "deco": false, "buddies": ["@anna"], "entry": "shore",
                    "profiles": {"p": {"start_date": "2026-06-01", "start_time": "10:00:00",
                    "depth": [[0, 0], [60, 10.0], [120, 0]]}}}""",
                "dive/2026-06-02#0.json" to """{"dive_number": 2, "dive_site": "@blue", "rating": 8,
                    "deco": true, "buddies": ["@anna", "@bram"], "entry": "boat",
                    "profiles": {"p": {"start_date": "2026-06-02", "start_time": "11:00:00",
                    "depth": [[0, 0], [60, 30.0], [120, 0]]}}}""",
                "dive/2026-06-03#0.json" to """{"dive_number": 3, "dive_site": "@elph",
                    "profiles": {"p": {"start_date": "2026-06-03", "start_time": "12:00:00",
                    "depth": [[0, 0], [60, 20.0], [120, 0]]}}}""",
            ),
        ),
        Types.ALL,
    )

    private val dives = listOf("2026-06-03#0", "2026-06-02#0", "2026-06-01#0").map { set[it]!! }

    private val stats = statisticsOf(dives)

    private fun about(label: String): Shown = stats.first { it.label == label }

    @Test
    fun `a number is its range and its average`() {
        assertEquals("1 – 3, average 2", about("Dive number").text)
        assertEquals("10 – 30, average 20", about("Max depth").text)
    }

    @Test
    fun `a date is its range`() {
        assertEquals("2026-06-01 – 2026-06-03", about("Start date").text)
    }

    @Test
    fun `a yes-or-no is how many yeses, and a field not all answered says how many did`() {
        assertEquals("1 of 2 (2 of 3)", about("Deco").text)
    }

    @Test
    fun `what is named is how many different, each leading to its item`() {
        val site = about("Dive site")
        assertEquals("Elphinstone, Blue Hole (2 different)", site.text)
        assertEquals(listOf("elph", null, "blue", null), site.parts.map { it.leadsTo })
        assertEquals("Anna, Bram (2 different) (2 of 3)", about("Buddies").text)
        assertEquals("boat, shore (2 different) (2 of 3)", about("Entry").text)
    }

    @Test
    fun `a rating averages into stars`() {
        assertEquals(7, about("Rating").rating)
    }

    @Test
    fun `a field nobody answered is left out, and nothing is said of no items`() {
        assertNull(stats.firstOrNull { it.label == "Remarks" })
        assertEquals(emptyList(), statisticsOf(emptyList()))
    }
}
