package yemoja.ui.gui

import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/*
 * Bringing many dives to one figure. See ../../../../../../gui/doc.md — `GUI-32`.
 */
private val YEAR = LogbookReader.read(
    MemoryFileStore(
        mapOf(
            "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
            // Two in January, one in March, none in February, and one the following January.
            "dive/2026-01-05#0.json" to """{"dive_site": "@blue", "start_date": "2026-01-05",
                "duration": 3600, "max_depth": 30.0}""",
            "dive/2026-01-25#0.json" to """{"dive_site": "@blue", "start_date": "2026-01-25",
                "duration": 1800, "max_depth": 18.0}""",
            "dive/2026-03-01#0.json" to """{"dive_site": "@blue", "start_date": "2026-03-01",
                "duration": 2400, "max_depth": 12.0}""",
            "dive/2027-01-10#0.json" to """{"dive_site": "@blue", "start_date": "2027-01-10",
                "duration": 600, "max_depth": 6.0}""",
        ),
    ),
    Types.ALL,
)

private fun variable(label: String): Variable = variablesOf().first { it.label == label }

private val WHEN = variable("Start date")
private val DEEP = variable("Max depth")
private val LONG = variable("Duration")

private fun bars(gathering: Gathering, step: Step = Step.Calendar(1, "month")): List<Bar> =
    barsOf(YEAR, WHEN, DEEP, gathering, step)

class GatheredTest {

    @Test
    fun `counting puts the dives of each month in its own bar`() {
        val held = bars(Gathering.COUNT)
        assertEquals(13, held.size, "January to the January after it, months and all")
        assertEquals(2.0, held.first().value)
        assertEquals(1.0, held.last().value)
    }

    @Test
    fun `a month nobody dived counts nought rather than being left out`() {
        // None made is a figure. `GUI-32`.
        assertEquals(0.0, bars(Gathering.COUNT)[1].value, "February")
        assertEquals(1.0, bars(Gathering.COUNT)[2].value, "March")
    }

    @Test
    fun `a month nobody dived has no average, and is left out`() {
        val held = bars(Gathering.AVERAGE)
        assertEquals(3, held.size, "the three months dived, and no more")
        assertEquals(24.0, held.first().value, "thirty and eighteen")
        assertEquals(6.0, held.last().value)
    }

    @Test
    fun `a total adds the month up and an extreme takes one of it`() {
        assertEquals(48.0, bars(Gathering.TOTAL).first().value)
        assertEquals(30.0, bars(Gathering.LARGEST).first().value)
        assertEquals(18.0, bars(Gathering.SMALLEST).first().value)
    }

    @Test
    fun `a bar says how many dives it gathered, whatever it gathered of them`() {
        assertEquals(2, bars(Gathering.LARGEST).first().dives)
    }

    @Test
    fun `counting needs nothing up the side, so a dive missing it still counts`() {
        val without = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
                    "dive/2026-01-05#0.json" to
                        """{"dive_site": "@blue", "start_date": "2026-01-05"}""",
                ),
            ),
            Types.ALL,
        )
        val held = barsOf(without, WHEN, DEEP, Gathering.COUNT, Step.Calendar(1, "month"))
        assertEquals(listOf(1.0), held.map { it.value })
        assertTrue(
            barsOf(without, WHEN, DEEP, Gathering.TOTAL, Step.Calendar(1, "month")).isEmpty(),
            "a total of what was not recorded is not nought",
        )
    }
}

class CalendarTest {

    @Test
    fun `a bar begins where its month begins, not at a twelfth of a year`() {
        val held = bars(Gathering.COUNT)
        assertEquals(yearOf(yemoja.data.Date(2026, 1, 1)), held.first().from)
        assertEquals(yearOf(yemoja.data.Date(2026, 2, 1)), held.first().to)
    }

    @Test
    fun `a quarter is three months and a year is twelve`() {
        assertEquals(5, bars(Gathering.COUNT, Step.Calendar(3, "quarter")).size)
        assertEquals(2, bars(Gathering.COUNT, Step.Calendar(12, "year")).size)
    }

    @Test
    fun `a date is cut by the calendar and everything else by a round width`() {
        assertEquals(listOf("month", "quarter", "year", "five years"), stepsOf(WHEN, emptyList())
            .map { it.label })
        val widths = stepsOf(DEEP, listOf(6.0, 12.0, 18.0, 30.0))
        assertTrue(widths.all { it is Step.Width }, "metres are not months")
        assertTrue(widths.first().label.endsWith(" m"), "and a width says what it counts in")
    }

    @Test
    fun `dives are gathered by a plain width where the axis is a number`() {
        val held = barsOf(YEAR, DEEP, LONG, Gathering.COUNT, Step.Width(10.0, "10 m"))
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), held.map { it.from })
        // Six; twelve and eighteen; nothing between twenty and thirty; thirty.
        assertEquals(listOf(1.0, 2.0, 0.0, 1.0), held.map { it.value })
    }
}

class RunningTest {

    @Test
    fun `a running total climbs to what the logbook comes to`() {
        val held = runningOf(YEAR, WHEN, LONG)
        assertEquals(4, held.size)
        assertEquals(listOf(60.0, 90.0, 130.0, 140.0), held.map { it.up }, "minutes, added up")
    }

    @Test
    fun `it climbs in the order the dives were made, whatever order they were read in`() {
        val held = runningOf(YEAR, WHEN, LONG)
        assertEquals(held.map { it.across }.sorted(), held.map { it.across })
    }
}

class FittedTest {

    @Test
    fun `the fit opens on months where months are few enough`() {
        val steps = stepsOf(WHEN, emptyList())
        assertEquals(0, fittedOf(YEAR, WHEN, DEEP, Gathering.COUNT, steps))
    }

    @Test
    fun `the fit widens where the months would be too many`() {
        val long = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
                    "dive/1990-01-05#0.json" to
                        """{"dive_site": "@blue", "start_date": "1990-01-05"}""",
                    "dive/2026-01-05#0.json" to
                        """{"dive_site": "@blue", "start_date": "2026-01-05"}""",
                ),
            ),
            Types.ALL,
        )
        val steps = stepsOf(WHEN, emptyList())
        val at = fittedOf(long, WHEN, DEEP, Gathering.COUNT, steps)
        assertTrue(at > 0, "thirty-six years of months is a smear, and it opened on $at")
        assertEquals("year", steps[at].label)
    }
}
