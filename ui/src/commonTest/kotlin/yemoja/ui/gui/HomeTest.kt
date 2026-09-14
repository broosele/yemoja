package yemoja.ui.gui

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

    @Test
    fun `the greeting reads as a sentence, and without a user it still does`() {
        assertEquals(
            "Hello Anna Devries, you have 3 logged dives on 2 different locations" +
                " for a total of 2 hours and 10 minutes underwater.",
            hailOf(user, greetingOf(DIVED)),
        )
        assertTrue(hailOf(null, greetingOf(DIVED)).startsWith("You have 3 logged dives"))
    }

    @Test
    fun `a length reads in hours and minutes, and one of each is singular`() {
        assertEquals("45 minutes", spanOf(2700.0))
        assertEquals("2 hours", spanOf(7200.0))
        assertEquals("1 hour and 1 minute", spanOf(3660.0))
        assertEquals("0 minutes", spanOf(0.0), "a logbook with nothing in it says so")
    }

    @Test
    fun `every deed the application knows is named, built or not`() {
        assertEquals(
            listOf("New logbook", "Open a logbook", "Import a logbook", "Download from a computer"),
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
