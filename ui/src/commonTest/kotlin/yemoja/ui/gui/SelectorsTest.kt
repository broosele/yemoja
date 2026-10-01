package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import yemoja.logic.wasMade
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What each tab's selector holds. See ../../../../../../gui/doc.md — `GUI-20` to `GUI-22`, and `DESK-7` in desktop/doc.md.
 */

private fun logbook(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

private fun dive(day: Int, trip: String? = null, site: String? = null, number: Int = day) =
    """{"dive_number": $number""" +
        (trip?.let { ""","dive_trip": "@$it"""" } ?: "") +
        (site?.let { ""","dive_site": "@$it"""" } ?: "") +
        ""","profiles": {"p1": {"start_date": "2026-06-${"%02d".format(day)}",
        "start_time": "10:00:00", "depth": [[0, 0], [60, 12.0]]}}}"""

class PlannedRowTest {

    private val held = logbook(
        "dive/2026-06-01#0.json" to """{"dive_number": 1, "profiles": {"p": {
            "start_date": "2026-06-01", "start_time": "10:00:00",
            "depth": [[0, 0], [60, 12.0], [1800, 0]]}}}""",
        "dive/2026-10-03#0.json" to """{"start_date": "2026-10-03", "profiles": {"p": {
            "planned": true, "depth": [[0, 0], [60, 30.0], [3600, 0]]}}}""",
    )

    @Test
    fun `a planned dive is a row like any other, saying so where a number would be`() {
        // Shown and marked, counted nowhere. `GUI-39`.
        val rows = diveRowsOf(held)
        assertEquals(2, rows.size, "a plan is listed")
        val plan = rows.first { it.dive.id == "2026-10-03#0" }
        assertTrue(plan.planned)
        assertEquals(PLANNED, plan.number, "its own numbering has no place for one")
        val made = rows.first { it.dive.id == "2026-06-01#0" }
        assertTrue(!made.planned)
        assertEquals("1", made.number)
    }

    @Test
    fun `a year counts and chooses the dives made in it`() {
        val years = yearsOf(diveRowsOf(held))
        assertEquals(listOf("2026"), years.map { it.label })
        assertEquals(2, years.single().rows.size, "both are listed under it")
        assertEquals(listOf("2026-06-01#0"), years.single().made.map { it.dive.id })
    }

    @Test
    fun `what was made is what the window counts`() {
        assertEquals(listOf("2026-06-01#0"), divesMadeIn(held).map { held.idOf(it) })
        assertTrue(wasMade(held["2026-06-01#0"]!!))
        assertTrue(!wasMade(held["2026-10-03#0"]!!))
    }
}

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
    fun `a trip's name rides on the last row of its run, spanning the whole run`() {
        // 05 Zeeland alone, 04 none, 03 and 02 Red Sea, 01 Zeeland alone.
        assertEquals(mapOf(0 to 1, 3 to 2, 4 to 1), spannedOf(rows))
    }

    @Test
    fun `a dive on no trip stands alone, however many surround it`() {
        assertNull(rows[1].trip)
        assertEquals(1, rows[1].run, "nothing is not the same as nothing")
    }
}

class YearsTest {

    private val set = logbook(
        "dive_trip.json" to """{"newyear": {"name": "New Year"}}""",
        "dive/2025-12-31#0.json" to dive(31, "newyear").replace("2026-06-31", "2025-12-31"),
        "dive/2026-01-01#0.json" to dive(1, "newyear").replace("2026-06-01", "2026-01-01"),
        "dive/2026-06-02#0.json" to dive(2),
    )

    private val years = yearsOf(diveRowsOf(set))

    @Test
    fun `the table is divided by year, newest first`() {
        assertEquals(listOf("2026", "2025"), years.map { it.label })
        assertEquals(listOf(2, 1), years.map { it.rows.size })
    }

    @Test
    fun `a dive is found under its year, and nothing else is under any`() {
        assertEquals("2025", yearHolding(years, years[1].rows.single().dive.id))
        assertEquals("2026", yearHolding(years, years[0].rows.first().dive.id))
        assertNull(yearHolding(years, "not_a_dive"))
        assertNull(yearHolding(years, null))
    }

    @Test
    fun `a trip over New Year is a run in each year, since a cell does not cross a divider`() {
        val january = years[0].rows.last()
        val december = years[1].rows.single()
        assertEquals("New Year", january.trip?.title)
        assertEquals(1, january.run)
        assertEquals(1, december.run)
    }

    @Test
    fun `a dive with no date is filed under a year of its own`() {
        val undated = logbook("dive/x#0.json" to """{"dive_number": 9}""")
        assertEquals(listOf(UNDATED), yearsOf(diveRowsOf(undated)).map { it.label })
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
            "blue_hole": {"name": "Blue Hole", "regions": ["@egypt"]},
            "elphinstone": {"name": "Elphinstone", "regions": ["@egypt"]},
            "zeelandbrug": {"name": "Zeelandbrug", "regions": ["@europe"]}
        }""",
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
    fun `what is at a place is its sites`() {
        assertEquals(listOf("Blue Hole", "Elphinstone"), atPlaceIn(set, "egypt").map { it.title })
    }

    @Test
    fun `a region holds what is anywhere inside it`() {
        // A site in Egypt is in Africa, and in the world.
        assertEquals(
            listOf("Blue Hole", "Elphinstone"),
            atPlaceIn(set, "africa").map { it.title },
        )
        assertEquals(3, atPlaceIn(set, "world").size)
        assertEquals(setOf("world", "europe", "africa", "egypt", "red_sea"), withinOf(set, "world"))
    }

    @Test
    fun `a region with nothing inside it has nothing at it`() {
        assertEquals(emptyList(), atPlaceIn(set, "red_sea").map { it.title })
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

/*
 * Who may be made the logbook's user. See ../../../../../../gui/doc.md — `GUI-51`.
 */
class OwningOfferTest {

    private val set = logbook(
        "person.json" to """{"anna": {"first_name": "Anna"}, "bo": {"first_name": "Bo"}}""",
        "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
    )

    @Test
    fun `offered on a person who is not the user, and on nobody else`() {
        val anna = set["anna"]!! as yemoja.data.ReferenceableItem
        val bo = set["bo"]!!
        assertTrue(owningOf(bo, anna, set), "another person")
        assertTrue(!owningOf(anna, anna, set), "not the user's own card")
        assertTrue(!owningOf(set["blue"]!!, anna, set), "not a site")
        assertTrue(owningOf(anna, null, set), "anybody, while the logbook names nobody")
    }
}

/*
 * Unfolding a tree towards something chosen somewhere else. See ../../../../../../gui/doc.md —
 * `GUI-26`.
 */
class UnfoldingTest {

    private val set = logbook(
        "region.json" to """{
            "world": {"name": "World"},
            "africa": {"name": "Africa", "parents": ["@world"]},
            "egypt": {"name": "Egypt", "parents": ["@africa"]},
            "europe": {"name": "Europe", "parents": ["@world"]}
        }""",
    )

    private val tree = regionTreeOf(set)

    @Test
    fun `a tree opens on its roots and nothing under them`() {
        assertEquals(setOf("/world"), rootsOf(tree))
    }

    @Test
    fun `a branch is found by the path its line is keyed with`() {
        assertEquals("/world/africa/egypt", pathTo(tree, "egypt"))
        assertEquals("/world", pathTo(tree, "world"))
        assertNull(pathTo(tree, "atlantis"), "nothing holds it")
    }

    @Test
    fun `unfolding towards a branch opens its ancestors and not itself`() {
        assertEquals(setOf("/world", "/world/africa"), openingTo(tree, "egypt"))
        assertEquals(
            emptySet(),
            openingTo(tree, "world"),
            "a root needs nothing opened, and opening it would unfold the world",
        )
        assertNull(openingTo(tree, "atlantis"))
    }

    @Test
    fun `a line is counted among the lines a reader can see`() {
        val shut = setOf("/world")
        assertEquals(0, lineOf(tree, shut, "/world"))
        assertEquals(1, lineOf(tree, shut, "/world/africa"))
        assertEquals(2, lineOf(tree, shut, "/world/europe"), "Egypt is folded away under Africa")
        assertNull(lineOf(tree, shut, "/world/africa/egypt"))
        val open = shut + "/world/africa"
        assertEquals(2, lineOf(tree, open, "/world/africa/egypt"))
        assertEquals(3, lineOf(tree, open, "/world/europe"), "and Europe moves down for it")
    }

    @Test
    fun `a region under two parents is answered with the first place it sits`() {
        val both = logbook(
            "region.json" to """{
                "world": {"name": "World"},
                "africa": {"name": "Africa", "parents": ["@world"]},
                "europe": {"name": "Europe", "parents": ["@world"]},
                "red_sea": {"name": "Red Sea", "parents": ["@africa", "@europe"]}
            }""",
        )
        val opening = assertNotNull(openingTo(regionTreeOf(both), "red_sea"))
        assertEquals(2, opening.size, "one chain, not two: $opening")
        assertTrue("/world" in opening)
    }
}

class HideUnusedTest {

    private val set = logbook(
        "region.json" to """{
            "world": {"name": "World"},
            "europe": {"name": "Europe", "parents": ["@world"]},
            "netherlands": {"name": "Netherlands", "parents": ["@europe"]},
            "zeeland": {"name": "Zeeland", "parents": ["@netherlands"]},
            "spain": {"name": "Spain", "parents": ["@europe"]},
            "africa": {"name": "Africa", "parents": ["@world"]},
            "egypt": {"name": "Egypt", "parents": ["@africa"]},
            "red_sea": {"name": "Red Sea", "parents": ["@africa", "@asia"]},
            "asia": {"name": "Asia", "parents": ["@world"]},
            "antarctica": {"name": "Antarctica", "parents": ["@world"]}
        }""",
        "dive_site.json" to """{
            "zeelandbrug": {"name": "Zeelandbrug", "regions": ["@zeeland"]},
            "medes": {"name": "Medes", "regions": ["@spain"]},
            "blue_hole": {"name": "Blue Hole", "regions": ["@egypt"]},
            "thistlegorm": {"name": "Thistlegorm", "regions": ["@red_sea"]}
        }""",
        "dive/2026-06-01#0.json" to dive(1, site = "zeelandbrug"),
        "dive/2026-06-02#0.json" to dive(2, site = "blue_hole"),
        "dive/2026-06-03#0.json" to dive(3, site = "thistlegorm"),
    )

    @Test
    fun `a site is used where a dive names it`() {
        assertEquals(setOf("zeelandbrug", "blue_hole", "thistlegorm"), usedSitesIn(set))
    }

    @Test
    fun `off, the tree is the whole atlas`() {
        val world = shownTreeOf(set, hideUnused = false).single()
        assertEquals(
            setOf("Africa", "Antarctica", "Asia", "Europe"),
            world.children.map { it.label }.toSet(),
        )
    }

    @Test
    fun `on, what no dive touches is left out and a branch that only leads on is cut out`() {
        val world = shownTreeOf(set, hideUnused = true).single()
        // Antarctica has nothing. Europe holds only Zeeland, through the Netherlands, so both
        // are cut out. Africa holds Egypt and the Red Sea, so it stays. Asia holds only the
        // Red Sea, so it is cut out, and the Red Sea it leads to is already under Africa.
        assertEquals(setOf("Africa", "Red Sea", "Zeeland"), world.children.map { it.label }.toSet())
        val africa = world.children.first { it.label == "Africa" }
        assertEquals(setOf("Egypt", "Red Sea"), africa.children.map { it.label }.toSet())
    }

    @Test
    fun `a region with one child and a site of its own is not cut out`() {
        val own = logbook(
            "region.json" to """{
                "netherlands": {"name": "Netherlands"},
                "zeeland": {"name": "Zeeland", "parents": ["@netherlands"]}
            }""",
            "dive_site.json" to """{
                "zeelandbrug": {"name": "Zeelandbrug", "regions": ["@zeeland"]},
                "vinkeveen": {"name": "Vinkeveen", "regions": ["@netherlands"]}
            }""",
            "dive/2026-06-01#0.json" to dive(1, site = "zeelandbrug"),
            "dive/2026-06-02#0.json" to dive(2, site = "vinkeveen"),
        )
        val roots = shownTreeOf(own, hideUnused = true)
        assertEquals(listOf("Netherlands"), roots.map { it.label })
        assertEquals(listOf("Zeeland"), roots.single().children.map { it.label })
    }

    @Test
    fun `on, a region lists only the sites dives name`() {
        assertEquals(
            listOf("Blue Hole", "Thistlegorm", "Zeelandbrug"),
            atPlaceIn(set, "world", hideUnused = true).map { it.title },
        )
        assertEquals(4, atPlaceIn(set, "world").size, "off, every site")
    }
}

class UnplacedSitesTest {

    private val set = logbook(
        "region.json" to """{"zeeland": {"name": "Zeeland"}}""",
        "dive_site.json" to """{
            "zeelandbrug": {"name": "Zeelandbrug", "regions": ["@zeeland"]},
            "nionplas": {"name": "Nionplas"}
        }""",
        "dive/2026-06-21#0.json" to """{"dive_site": "@zeelandbrug"}""",
    )

    @Test
    fun `a site naming no region hangs where such sites hang, rather than nowhere`() {
        assertEquals(listOf("Nionplas"), looseSitesIn(set).map { it.title })
        val tree = shownTreeOf(set, hideUnused = false)
        assertEquals(listOf("Zeeland", "No region"), tree.map { it.label })
        assertEquals(listOf("Nionplas"), atPlaceIn(set, UNPLACED).map { it.title })
    }

    @Test
    fun `it hangs there whether or not a dive names it, which a new one does not`() {
        // A site nobody has dived is the one a reader has just made, and hiding it is what sent
        // them looking for it.
        val tree = shownTreeOf(set, hideUnused = true)
        assertEquals(listOf("Zeeland", "No region"), tree.map { it.label })
        assertEquals(
            listOf("Nionplas"),
            atPlaceIn(set, UNPLACED, hideUnused = true).map { it.title },
        )
    }

    @Test
    fun `the branch holds no region, so what chooses it is its key`() {
        // Every other branch is chosen by the region it holds; this one has none to hold, which
        // is why the tree reports the key instead.
        val branch = shownTreeOf(set, hideUnused = false).single { it.key == UNPLACED }
        assertEquals(emptyList(), branch.held.map { it.id })
        assertEquals("No region", branch.label)
    }

    @Test
    fun `a logbook whose sites all name a region has no such branch`() {
        val placed = logbook(
            "region.json" to """{"zeeland": {"name": "Zeeland"}}""",
            "dive_site.json" to """{"zeelandbrug": {"name": "Zeelandbrug", "regions": ["@zeeland"]}}""",
        )
        assertEquals(listOf("Zeeland"), shownTreeOf(placed, hideUnused = false).map { it.label })
    }
}

class HomeTest {

    private val set = logbook(
        "region.json" to """{
            "red_sea": {"name": "Red Sea", "west": 32, "east": 43, "south": 12, "north": 30},
            "egypt": {"name": "Egypt", "west": 25, "east": 37, "south": 22, "north": 32},
            "nowhere": {"name": "Nowhere"}
        }""",
        "dive_site.json" to """{
            "blue_hole": {"name": "Blue Hole", "regions": ["@red_sea", "@egypt"]},
            "lost": {"name": "Lost", "regions": ["@nowhere"]},
            "wrecked": {"name": "Wrecked", "regions": ["@red_sea"]}
        }""",
    )

    @Test
    fun `a site is at home in the most specific region it names, by the smallest frame`() {
        assertEquals("egypt", homeOf(set, set["blue_hole"]!!))
    }

    @Test
    fun `a site whose only region has no frame still goes there`() {
        assertEquals("nowhere", homeOf(set, set["lost"]!!))
    }

    @Test
    fun `a site naming no region has no home to go to`() {
        val homeless = logbook("dive_site.json" to """{"x": {"name": "X"}}""")
        assertNull(homeOf(homeless, homeless["x"]!!))
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
        val dots = dotsOf(atPlaceIn(set, "bare"))
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
        val frame = frameOf(set["bare"]!!, dotsOf(atPlaceIn(set, "bare")))!!
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
    fun `what a canvas shows is the frame and whatever the fit leaves room for`() {
        val frame = frameOf(set["boxed"]!!, emptyList())!!
        // Twice as wide as the frame needs: the shown frame is wider, and no taller.
        val shown = frame.shown(400.0, 200.0)
        assertTrue(shown.width > frame.width, "${shown.width}")
        assertEquals(frame.height, shown.height, 1e-9)
        assertTrue(shown.west < 30.0 && shown.east > 36.0, "${shown.west}..${shown.east}")
        // The world on a tall canvas is still the world, and no taller than the poles.
        val world = Frame(-180.0, 180.0, -90.0, 90.0).shown(200.0, 400.0)
        assertEquals(360.0, world.width)
        assertEquals(-90.0, world.south)
        assertEquals(90.0, world.north)
    }

    @Test
    fun `a point just west of the frame is drawn just off the left, not a world away`() {
        val mediterranean = Frame(west = -6.0, east = 36.0, south = 30.0, north = 46.0)
        val (inside, _) = mediterranean.place(40.0, -5.0, 420.0, 160.0)
        val (portugal, _) = mediterranean.place(40.0, -9.0, 420.0, 160.0)
        assertTrue(portugal < inside, "$portugal should be left of $inside")
        assertTrue(portugal > -100.0, "$portugal should be just off the left")
    }

    @Test
    fun `an outline straddling the west edge starts west of it and is drawn whole`() {
        val mediterranean = Frame(west = -6.0, east = 36.0, south = 30.0, north = 46.0)
        // Iberia, from -9 to 3: it starts three degrees west of the edge.
        assertEquals(-3.0, mediterranean.startOf(-9.0, 3.0), 1e-9)
        // Something well inside starts where it is.
        assertEquals(16.0, mediterranean.startOf(10.0, 20.0), 1e-9)
    }

    @Test
    fun `a frame across the date line places a site beyond it east of the west edge`() {
        val frame = frameOf(set["pacific"]!!, emptyList())!!
        assertEquals(170.0, frame.width)
        // Hawaii, at -155, is 85 degrees east of 120.
        assertEquals(85.0, frame.eastOf(-155.0))
    }
}
