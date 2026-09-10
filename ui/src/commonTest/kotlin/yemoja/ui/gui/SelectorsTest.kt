package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What each tab's selector holds. See ../../../../../../gui/doc.md — `GUI-19` to `GUI-22`.
 */

private fun logbook(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun dive(day: Int, trip: String? = null, site: String? = null, number: Int = day) =
    """{"dive_number": $number""" +
        (trip?.let { ""","details": {"dive_trip": "@$it"}""" } ?: "") +
        (site?.let { ""","dive_site": "@$it"""" } ?: "") +
        ""","profiles": {"p1": {"start_date": "2026-06-${"%02d".format(day)}",
        "start_time": "10:00:00", "depth": [[0, 0], [60, 12.0]]}}}"""

class DiveTableTest {

    private val set = logbook(
        "dive_trip.json" to """{"red_sea": {"name": "Red Sea"}, "zeeland": {"name": "Zeeland"}}""",
        "dive_site.json" to """{"blue_hole": {"name": "Blue Hole"}}""",
        // Kept newest first, so these read 05, 04, 03, 02, 01.
        "dive/2026-06-01#0.json" to dive(1, "zeeland"),
        "dive/2026-06-02#0.json" to dive(2, "red_sea", "blue_hole"),
        "dive/2026-06-03#0.json" to dive(3, "red_sea"),
        "dive/2026-06-04#0.json" to dive(4),
        "dive/2026-06-05#0.json" to dive(5, "zeeland"),
    )

    private val rows = diveRowsOf(set)

    @Test
    fun `every dive is a row, newest first, with its four columns`() {
        assertEquals(5, rows.size)
        assertEquals(listOf("2026-06-05", "2026-06-04", "2026-06-03", "2026-06-02", "2026-06-01"),
            rows.map { it.date })
        assertEquals("2", rows[3].number)
        assertEquals("Blue Hole", rows[3].site, "the site's name, not the id it is written as")
        assertEquals("", rows[2].site, "a dive naming no site says nothing")
    }

    @Test
    fun `a trip cell spans the consecutive dives on it`() {
        // 05 Zeeland, 04 none, 03 and 02 Red Sea, 01 Zeeland.
        assertEquals(listOf(1, 1, 2, 0, 1), rows.map { it.run })
        assertEquals("Red Sea", rows[2].trip?.title)
        assertEquals("Red Sea", rows[3].trip?.title, "the dive still knows its trip")
    }

    @Test
    fun `the same trip met again is a second run, not one span`() {
        // Zeeland opens the table and closes it, and the two are not one cell.
        assertEquals("Zeeland", rows.first().trip?.title)
        assertEquals("Zeeland", rows.last().trip?.title)
        assertEquals(1, rows.first().run)
        assertEquals(1, rows.last().run)
    }

    @Test
    fun `a dive on no trip stands alone, however many surround it`() {
        assertNull(rows[1].trip)
        assertEquals(1, rows[1].run, "nothing is not the same as nothing")
    }
}

class GearTreeTest {

    private val set = logbook(
        "gear.json" to """{
            "wing": {"name": "Wing", "category": "BCD", "kind": "wing"},
            "harness": {"name": "Harness", "category": "BCD", "kind": "harness"},
            "plate": {"name": "Plate", "category": "BCD"},
            "twelve": {"name": "Twelve", "category": "cylinder"},
            "spare": {"name": "Spare"}
        }""",
    )

    private val tree = gearTreeOf(set)

    @Test
    fun `a branch per category, and a branch under it per kind`() {
        assertEquals(listOf("BCD", "cylinder"), tree.first.map { it.label })
        val bcd = tree.first.first()
        assertEquals(listOf("harness", "wing"), bcd.children.map { it.label })
        assertEquals(listOf("Plate"), bcd.held.map { it.title }, "no kind means directly under")
    }

    @Test
    fun `gear filed under nothing sits at the top rather than in a bucket`() {
        assertEquals(listOf("Spare"), tree.second.map { it.title })
    }

    @Test
    fun `the branches come from the logbook, not from a list of ten words`() {
        val odd = logbook("""gear.json""" to """{"x": {"name": "X", "category": "sledge"}}""")
        assertEquals(listOf("sledge"), gearTreeOf(odd).first.map { it.label })
    }
}

class RegionTreeTest {

    private val set = logbook(
        "region.json" to """{
            "world": {"name": "World"},
            "europe": {"name": "Europe", "parents": ["@world"]},
            "africa": {"name": "Africa", "parents": ["@world"]},
            "egypt": {"name": "Egypt", "parents": ["@africa"]},
            "red_sea": {"name": "Red Sea", "parents": ["@africa", "@europe"]}
        }""",
        "dive_site.json" to """{
            "blue_hole": {"name": "Blue Hole", "regions": ["@egypt"], "wrecks": ["@thistlegorm"]},
            "elphinstone": {"name": "Elphinstone", "regions": ["@egypt"]},
            "zeelandbrug": {"name": "Zeelandbrug", "regions": ["@europe"]}
        }""",
        "wreck.json" to """{"thistlegorm": {"name": "SS Thistlegorm"}}""",
    )

    private val tree = regionTreeOf(set)

    @Test
    fun `a region with no parent is a root`() {
        assertEquals(listOf("World"), tree.map { it.label })
    }

    @Test
    fun `a region under two parents appears under both`() {
        val world = tree.single()
        val under = world.children.associate { it.label to it.children.map { c -> c.label } }
        assertEquals(setOf("Africa", "Europe"), under.keys)
        assertTrue("Red Sea" in under.getValue("Africa"), under.toString())
        assertTrue("Red Sea" in under.getValue("Europe"), under.toString())
    }

    @Test
    fun `a region caught in a cycle is still reachable, and says what is wrong`() {
        // `LOGIC-8`: nothing prevents a chain that returns to its start, and a tree drawn from
        // one would not terminate.
        val looped = logbook(
            "region.json" to """{
                "a": {"name": "A", "parents": ["@b"]},
                "b": {"name": "B", "parents": ["@a"]}
            }""",
        )
        val branches = regionTreeOf(looped)
        // Neither is a root, and a region in no tree is unreachable, so both stand as roots
        // and each says what is wrong with it.
        assertEquals(listOf("A (inside itself)", "B (inside itself)"), branches.map { it.label })
    }

    @Test
    fun `what is at a place is its sites and the wrecks lying at them`() {
        val (sites, wrecks) = atPlaceIn(set, "egypt")
        assertEquals(listOf("Blue Hole", "Elphinstone"), sites.map { it.title })
        assertEquals(listOf("SS Thistlegorm"), wrecks.map { it.title })
    }

    @Test
    fun `a region with no site has nothing at it`() {
        val (sites, wrecks) = atPlaceIn(set, "world")
        assertEquals(emptyList(), sites.map { it.title }, "a site names its regions plainly")
        assertEquals(emptyList(), wrecks.map { it.title })
    }
}
