package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.ReferenceableItem
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What the home screen says. See ../../../../../../gui/doc.md — `GUI-30`.
 */
private val DIVED = LogbookReader.read(
    MemoryFileStore(
        mapOf(
            "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries"}}""",
            "dive_site.json" to
                """{"blue": {"name": "Blue Hole"}, "quarry": {"name": "The Quarry"}}""",
            "dive/2026-06-21#0.json" to """{"dive_site": "@blue", "duration": 3600,
                "start_date": "2026-06-21", "max_depth": 30.0,
                "environment": {"visibility": 8.0}}""",
            "dive/2026-06-22#0.json" to """{"dive_site": "@blue", "duration": 1800,
                "start_date": "2026-06-22", "max_depth": 18.0}""",
            "dive/2026-06-23#0.json" to """{"dive_site": "@quarry", "duration": 2400,
                "start_date": "2026-06-23", "max_depth": 12.0,
                "environment": {"visibility": 2.0}}""",
        ),
    ),
    Types.ALL,
)

class GreetingTest {

    private val user = DIVED["anna"] as ReferenceableItem

    @Test
    fun `a logbook comes to its dives, the places they were made, and how long they ran`() {
        val greeting = greetingOf(DIVED)
        assertEquals(3, greeting.dives)
        assertEquals(2, greeting.places, "two dives at one site are one place")
        assertEquals(7800.0, greeting.underwater)
    }

    private val plain = Date(2026, 5, 5)

    @Test
    fun `a reader the logbook knows is greeted by name, and told what is in it`() {
        val greeting = greetingOf(DIVED)
        assertEquals("Hello Anna Devries.", hailOf(user, greeting, plain, false).sentence)
        assertEquals(
            "You have 3 logged dives on 2 different locations" +
                " for a total of 2 hours and 10 minutes underwater.",
            tellingOf(user, greeting),
        )
    }

    @Test
    fun `a logbook that names nobody greets a diver and says how to fix that`() {
        val greeting = greetingOf(DIVED)
        assertEquals("Hello diver.", hailOf(null, greeting, plain, false).sentence)
        assertEquals(
            "Name one of this logbook's people as yourself, and it will greet you by name.",
            tellingOf(null, greeting),
        )
    }

    @Test
    fun `with no logbook open at all there is a welcome and two ways out of it`() {
        assertEquals("Hello, and welcome.", hailOf(null, null, plain, false).sentence)
        assertEquals(
            "You can make a new logbook, or open one you already have.",
            tellingOf(null, null),
        )
    }

    @Test
    fun `a length reads in hours and minutes, and one of each is singular`() {
        assertEquals("45 minutes", spanOf(2700.0))
        assertEquals("2 hours", spanOf(7200.0))
        assertEquals("1 hour and 1 minute", spanOf(3660.0))
        assertEquals("0 minutes", spanOf(0.0), "a logbook with nothing in it says so")
    }

    @Test
    fun `a day worth remarking on is remarked on, and most days are not`() {
        val born = Date(1904, 6, 16)
        assertNull(occasionOf(plain, born, false), "a plain day says nothing")
        assertEquals("happy birthday", occasionOf(Date(2026, 6, 16), born, false)?.said)
        assertEquals("a happy new year", occasionOf(Date(2026, 1, 1), born, false)?.said)
        assertEquals("happy World Oceans Day", occasionOf(Date(2026, 6, 8), null, false)?.said)
        assertEquals(
            "a thought for Jacques Cousteau, born on this day in 1910",
            occasionOf(Date(2026, 6, 11), null, false)?.said,
        )
        assertEquals(
            "happy World Sea Turtle Day",
            occasionOf(Date(2026, 6, 16), null, false)?.said,
            "a birthday would have won, and there is none",
        )
    }

    @Test
    fun `a season turns on the first of a month, and turns the other way in the south`() {
        assertEquals("happy first day of spring", occasionOf(Date(2026, 3, 1), null, false)?.said)
        assertEquals("happy first day of autumn", occasionOf(Date(2026, 3, 1), null, true)?.said)
        assertEquals("happy first day of winter", occasionOf(Date(2026, 12, 1), null, false)?.said)
        assertEquals("happy first day of summer", occasionOf(Date(2026, 12, 1), null, true)?.said)
        assertNull(occasionOf(Date(2026, 3, 2), null, false), "only the first")
        assertNull(occasionOf(Date(2026, 5, 1), null, false), "no season begins in May")
    }

    @Test
    fun `the day is woven into the greeting rather than said beside it`() {
        val greeting = greetingOf(DIVED)
        assertEquals(
            "Hello Anna Devries, and happy World Oceans Day.",
            hailOf(user, greeting, Date(2026, 6, 8), false).sentence,
        )
        assertEquals(
            "Hello diver, and a happy new year.",
            hailOf(null, greeting, Date(2026, 1, 1), false).sentence,
        )
        assertEquals(
            "Hello, and happy Earth Day.",
            hailOf(null, null, Date(2026, 4, 22), false).sentence,
            "a welcome gives way to the day",
        )
    }

    @Test
    fun `a day worth remarking on leads to a page about it, and a birthday leads nowhere`() {
        assertEquals(
            "https://en.wikipedia.org/wiki/Shark",
            occasionOf(Date(2026, 7, 14), null, false)?.page,
            "a day with no article of its own leads to what it is about",
        )
        assertEquals(
            "https://en.wikipedia.org/wiki/World_Oceans_Day",
            occasionOf(Date(2026, 6, 8), null, false)?.page,
        )
        assertEquals(
            "https://en.wikipedia.org/wiki/Spring_(season)",
            occasionOf(Date(2026, 3, 1), null, false)?.page,
        )
        val born = Date(1990, 2, 17)
        assertNull(occasionOf(Date(2026, 2, 17), born, false)?.page, "a birthday is the reader's")
        assertNull(hailOf(null, null, Date(2026, 5, 5), false).occasion?.page, "so is a welcome")
    }

    @Test
    fun `every page a day leads to is an article on the English Wikipedia`() {
        val days = (1..12).flatMap { month -> (1..28).map { day -> Date(2026, month, day) } }
        val pages = days.mapNotNull { occasionOf(it, null, false)?.page }.distinct()
        assertEquals(14, pages.size, "nine days of the sea, four seasons, and the new year")
        for (page in pages) {
            assertTrue(page.startsWith("https://en.wikipedia.org/wiki/"), page)
            assertTrue(page.length > "https://en.wikipedia.org/wiki/".length, page)
        }
    }

    @Test
    fun `which half of the world a logbook dives in is the mean of what its sites say`() {
        assertTrue(!southernOf(DIVED), "sites that say nothing are taken to be northern")
        val south = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive_site.json" to """{"poor": {"name": "Poor Knights", "latitude": -35.5,
                        "longitude": 174.7}, "north": {"name": "Zeeland", "latitude": 51.6,
                        "longitude": 3.8}, "fiord": {"name": "Fiordland", "latitude": -45.4,
                        "longitude": 167.0}}""",
                ),
            ),
            Types.ALL,
        )
        assertTrue(southernOf(south), "two of three sites south, and further south than the north")
    }

    @Test
    fun `a dive that has only been planned is counted nowhere`() {
        // A logbook claiming a dive nobody made is wrong; one hiding a plan is useless. `GUI-39`.
        val held = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive_site.json" to """{"blue": {"name": "Blue Hole"},
                        "quarry": {"name": "The Quarry"}}""",
                    "dive/2026-06-21#0.json" to """{"dive_site": "@blue", "profiles": {"p": {
                        "depth": [[0, 0], [60, 12.0], [1800, 0]]}}}""",
                    "dive/2026-10-03#0.json" to """{"dive_site": "@quarry", "profiles": {"p": {
                        "planned": true, "depth": [[0, 0], [60, 30.0], [3600, 0]]}}}""",
                ),
            ),
            Types.ALL,
        )
        val greeting = greetingOf(held)
        assertEquals(1, greeting.dives, "the one that was made")
        assertEquals(1, greeting.places, "and the place it was made at")
        assertEquals(1800.0, greeting.underwater, "the plan's hour is not time underwater")
        val across = variablesOf().first { it.label == "Max depth" }
        val up = variablesOf().first { it.label == "Duration" }
        assertEquals(1, plottedOf(held, across, up).size, "a dot apiece, and no dot for a plan")
    }

    @Test
    fun `every deed the application knows is named, built or not`() {
        assertEquals(
            listOf(
                "New logbook",
                "Open logbook",
                "Import",
                "Export to UDDF",
                "Download from dive computer",
                "Settings",
            ),
            Deed.entries.map { it.label },
        )
    }
}

class PlotTest {

    private fun variable(label: String) = variablesOf().first { it.label == label }

    @Test
    fun `what can be plotted is what a dive answers as a number, its own and its parts'`() {
        val names = variablesOf().map { it.label }
        assertTrue("Max depth" in names)
        assertTrue("Dive number" in names)
        assertTrue("Rating" in names)
        assertTrue("Start date" in names)
        assertTrue("Visibility" in names, "a field of the conditions it owns")
        assertTrue("Weight" in names, "a field of the gear it owns")
        assertTrue("Name" !in names, "not a number")
        assertTrue("Dive site" !in names, "not a number")
        assertTrue("Deco" !in names, "not a number")
    }

    @Test
    fun `a time is plotted in minutes and a date in years, each titled as it reads`() {
        assertEquals("min", variable("Duration").unit)
        assertEquals("m", variable("Max depth").unit)
        assertEquals("", variable("Start date").unit)
        val dive = DIVED["2026-06-21#0"]!!
        assertEquals(60.0, variable("Duration").of(dive), "an hour is sixty minutes")
        assertEquals(2026.5, variable("Start date").of(dive)!!, 0.1)
    }

    @Test
    fun `a dive is a spot where it answers both, and is left out where it does not`() {
        val spots = plottedOf(DIVED, variable("Max depth"), variable("Visibility"))
        assertEquals(2, spots.size, "the dive with no visibility is not at nought")
        assertEquals(listOf(30.0, 12.0), spots.map { it.across })
        assertEquals(listOf(8.0, 2.0), spots.map { it.up })
        assertEquals(listOf("2026-06-21#0", "2026-06-23#0"), spots.map { it.id })
    }

    @Test
    fun `the plot opens on how deep against when`() {
        val variables = variablesOf()
        val (across, up) = openingOf(variables)
        assertEquals("Start date", variables[across].label)
        assertEquals("Max depth", variables[up].label)
    }

    @Test
    fun `a variable nothing answers plots nothing at all`() {
        assertEquals(emptyList(), plottedOf(DIVED, variable("Rating"), variable("Max depth")))
        assertNull(variablesOf().firstOrNull { it.label == "Fingerprint" })
    }
}
