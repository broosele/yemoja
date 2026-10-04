package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.Moment
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Accuracy
import yemoja.logic.Extreme
import yemoja.logic.Slack
import yemoja.logic.Tidal
import yemoja.logic.TideCalculator
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * What the Tides form decides before it asks anybody. See ../../../../../../gui/doc.md — `GUI-55`.
 */

/** Northern is a calculator covering everything north of the fiftieth parallel, on the days it is told. */
private class Northern(
    override val name: String,
    override val accuracy: Accuracy,
    override val needsNetwork: Boolean = true,
    private val days: (day: Date, today: Date) -> Boolean = { _, _ -> true },
) : TideCalculator {
    override fun covers(latitude: Double, longitude: Double): Boolean = latitude > 50.0
    override fun coversDay(day: Date, today: Date): Boolean = days(day, today)
    override fun tides(latitude: Double, longitude: Double, day: Date): Tidal = Tidal.None("never asked")
}

private val TODAY = Date(2026, 10, 3)

private val GAUGE = Northern("Gauge", Accuracy.MEASURED) { day, today -> day < today }
private val FORECAST = Northern("Forecast", Accuracy.FORECAST) { day, today -> day >= today }
private val PREDICTION = Northern("Prediction", Accuracy.LOCAL)
private val TABLE = Northern("Table", Accuracy.WORLD, needsNetwork = false)

class TidesTest {

    private val held = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "dive_site.json" to """{
                    "bridge": {"name": "The bridge", "latitude": 51.62, "longitude": 3.88},
                    "reef": {"name": "The reef", "latitude": 27.9, "longitude": 34.3},
                    "somewhere": {"name": "Somewhere"}
                }""",
            ),
        ),
        Types.ALL,
    )

    @Test
    fun `only a site with a position that a model covers is offered`() {
        val sites = sitedIn(held, listOf(PREDICTION))
        assertEquals(listOf("bridge"), sites.map { it.id })
        assertEquals("The bridge", sites.single().title)
        assertEquals(51.62, sites.single().latitude)
        assertEquals(emptyList(), sitedIn(held, emptyList()))
    }

    @Test
    fun `the models offered are the ones covering the day, most accurate first`() {
        val site = sitedIn(held, listOf(PREDICTION)).single()
        val all = listOf(PREDICTION, GAUGE, TABLE, FORECAST)
        assertEquals(listOf("Forecast", "Prediction", "Table"), offeredFor(all, site, TODAY, TODAY).map { it.name })
        assertEquals(listOf("Gauge", "Prediction", "Table"), offeredFor(all, site, Date(2026, 10, 1), TODAY).map { it.name })
    }

    @Test
    fun `the most accurate is asked unless another is chosen, and offline the first that needs no network`() {
        val offered = listOf(FORECAST, PREDICTION, TABLE)
        assertEquals("Forecast", calculatorOf(offered, null, offline = false)?.name)
        assertEquals("Prediction", calculatorOf(offered, "Prediction", offline = false)?.name)
        // A model chosen for another day and not on offer for this one is not asked.
        assertEquals("Forecast", calculatorOf(offered, "Gauge", offline = false)?.name)
        assertEquals("Table", calculatorOf(offered, null, offline = true)?.name)
        // Nothing works offline: the most accurate stands, greyed, and says why when asked.
        assertEquals("Forecast", calculatorOf(listOf(FORECAST, PREDICTION), null, offline = true)?.name)
        assertNull(calculatorOf(emptyList(), null, offline = false))
    }

    @Test
    fun `an empty day is today, and what is not a date is none`() {
        assertEquals(TODAY, dayAsked("", TODAY))
        assertEquals(Date(2026, 12, 30), dayAsked(" 2026-12-30 ", TODAY))
        assertNull(dayAsked("tomorrow", TODAY))
        assertNull(dayAsked("2026-02-30", TODAY))
    }

    @Test
    fun `a turn is written with its time, its signed height and how far the water moved`() {
        val rows = rowsOf(
            listOf(
                Extreme(Moment(TODAY, Time(2, 21, 0)), -1.32, false),
                Extreme(Moment(TODAY, Time(8, 42, 0)), 1.46, true),
                Extreme(Moment(TODAY, Time(14, 45, 0)), -1.3, false),
            ),
        )
        assertEquals(
            listOf(
                TideRow("Low water", "02:21", "−1.32", ""),
                TideRow("High water", "08:42", "+1.46", "2.78"),
                TideRow("Low water", "14:45", "−1.30", "2.76"),
            ),
            rows,
        )
    }

    @Test
    fun `a curve that is part gauge and part forecast says where the one ends`() {
        assertEquals(
            "Measured until 19:40, forecast after that. The plot draws the forecast dashed.",
            measuredSaid(Moment(TODAY, Time(19, 40, 0))),
        )
    }

    @Test
    fun `a slack takes its place among the turns of the water, and has no height`() {
        val rows = rowsOf(
            listOf(
                Extreme(Moment(TODAY, Time(2, 21, 0)), -1.32, false),
                Extreme(Moment(TODAY, Time(8, 42, 0)), 1.46, true),
            ),
            listOf(Slack(Moment(TODAY, Time(3, 5, 0)), toFlood = true), Slack(Moment(TODAY, Time(9, 30, 0)), toFlood = false)),
        )
        assertEquals(
            listOf(
                TideRow("Low water", "02:21", "−1.32", ""),
                TideRow("Flood begins", "03:05", "", ""),
                TideRow("High water", "08:42", "+1.46", "2.78"),
                TideRow("Ebb begins", "09:30", "", ""),
            ),
            rows,
        )
    }

    @Test
    fun `a distance is given to a tenth under ten kilometres and whole above`() {
        assertEquals("0.2", distanceOf(0.17))
        assertEquals("9.4", distanceOf(9.37))
        assertEquals("12", distanceOf(12.4))
    }

    @Test
    fun `a height at the datum carries no sign`() {
        assertEquals("0.00", signedOf(0.0))
        assertEquals("0.00", signedOf(-0.004))
        assertEquals("+0.05", signedOf(0.05))
        assertEquals("−12.00", signedOf(-12.0))
    }
}
