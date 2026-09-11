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
    fun `a region holds what is anywhere inside it`() {
        // A site in Egypt is in Africa, and in the world.
        assertEquals(
            listOf("Blue Hole", "Elphinstone"),
            atPlaceIn(set, "africa").first.map { it.title },
        )
        assertEquals(3, atPlaceIn(set, "world").first.size)
        assertEquals(setOf("world", "europe", "africa", "egypt", "red_sea"), withinOf(set, "world"))
    }

    @Test
    fun `a region with nothing inside it has nothing at it`() {
        val (sites, wrecks) = atPlaceIn(set, "red_sea")
        assertEquals(emptyList(), sites.map { it.title })
        assertEquals(emptyList(), wrecks.map { it.title })
    }

    @Test
    fun `a cycle in the regions is walked once`() {
        val looped = logbook(
            "region.json" to """{
                "a": {"name": "A", "parents": ["@b"]},
                "b": {"name": "B", "parents": ["@a"]}
            }""",
        )
        assertEquals(setOf("a", "b"), withinOf(looped, "a"))
    }
}

class MapTest {

    private val set = logbook(
        "region.json" to """{
            "boxed": {"name": "Boxed", "west": 30, "east": 36, "south": 22, "north": 30},
            "pacific": {"name": "Pacific", "west": 120, "east": -70, "south": -60, "north": 60},
            "bare": {"name": "Bare"}
        }""",
        "dive_site.json" to """{
            "a": {"name": "A", "regions": ["@bare"], "latitude": 51.0, "longitude": 4.0},
            "b": {"name": "B", "regions": ["@bare"], "latitude": 52.0, "longitude": 3.0},
            "c": {"name": "C", "regions": ["@bare"]}
        }""",
    )

    @Test
    fun `a site without a position is not a dot`() {
        val dots = dotsOf(atPlaceIn(set, "bare").first)
        assertEquals(listOf("A", "B"), dots.map { it.title })
    }

    @Test
    fun `a region with a box is framed by it`() {
        val frame = frameOf(set["boxed"]!!, emptyList())!!
        assertEquals(6.0, frame.width)
        assertEquals(8.0, frame.height)
    }

    @Test
    fun `a region without a box is framed round its dots, with room to spare`() {
        val frame = frameOf(set["bare"]!!, dotsOf(atPlaceIn(set, "bare").first))!!
        assertTrue(frame.west < 3.0 && frame.east > 4.0, "${frame.west}..${frame.east}")
        assertTrue(frame.south < 51.0 && frame.north > 52.0, "${frame.south}..${frame.north}")
    }

    @Test
    fun `one site is still a frame, not a point`() {
        val one = listOf(Dot("a", "A", 51.0, 4.0))
        val frame = frameOf(set["bare"]!!, one)!!
        assertTrue(frame.width > 0.0 && frame.height > 0.0)
    }

    @Test
    fun `nothing to frame is no frame`() {
        assertNull(frameOf(set["bare"]!!, emptyList()))
    }

    @Test
    fun `the frame's corners land on the canvas edges, and a point inside lands inside`() {
        val frame = frameOf(set["boxed"]!!, emptyList())!!
        val (x0, y0) = frame.place(30.0, 30.0, 200.0, 200.0)
        val (x1, y1) = frame.place(22.0, 36.0, 200.0, 200.0)
        // The frame is taller than it is wide once longitude is squeezed, so it fills the
        // height and is centred across the width.
        assertEquals(0.0, y0)
        assertEquals(200.0, y1)
        assertTrue(x0 > 0.0 && x1 < 200.0 && x0 < x1, "$x0..$x1")
        val (x, y) = frame.place(26.0, 33.0, 200.0, 200.0)
        assertTrue(x > x0 && x < x1 && y > y0 && y < y1)
    }

    @Test
    fun `a frame across the date line places a site beyond it east of the west edge`() {
        val frame = frameOf(set["pacific"]!!, emptyList())!!
        assertEquals(170.0, frame.width)
        // Hawaii, at -155, is 85 degrees east of 120.
        assertEquals(85.0, frame.eastOf(-155.0))
    }
}
