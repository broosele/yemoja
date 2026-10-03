package yemoja.logic

import yemoja.data.Date
import yemoja.data.Moment
import yemoja.data.Time
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * The tide calculators. See ../../../../../doc.md — `LOGIC-44`.
 *
 * The service is never reached: each test hands the calculators a function answering with what
 * the service answered once, cut down to the members the calculators read.
 */

/** Three stations as the library lists them: one on the Oosterschelde, one up the coast, one with the prediction only. */
private val STATIONS = """
    stavenisse	Stavenisse	51.598	4.004	afm
    scheveningen	Scheveningen	52.099035	4.263563	afm
    dalem	Dalem	51.822	5.016527	a
""".trimIndent()

/** Under the Zeelandbrug, nine kilometres west of Stavenisse. */
private const val BRIDGE_LATITUDE = 51.62
private const val BRIDGE_LONGITUDE = 3.88

private val DAY = Date(2026, 10, 3)

/** One measurement as the service writes it: a stamp in MET and a value in centimetres. */
private fun measured(stamp: String, centimetres: Int): String =
    """{"Meetwaarde":{"Waarde_Alfanumeriek":"$centimetres","Waarde_Numeriek":$centimetres.0},"Tijdstip":"$stamp","WaarnemingMetadata":{"Statuswaarde":"Ongecontroleerd"}}"""

/** A whole answer holding [measurements] for Stavenisse. */
private fun answered(vararg measurements: String): String =
    """{"Succesvol":true,"WaarnemingenLijst":[{"AquoMetadata":{"Grootheid":{"Code":"WATHTE"}},""" +
        """"Locatie":{"Code":"stavenisse","Naam":"Stavenisse"},"MetingenLijst":[${measurements.joinToString(",")}]}]}"""

/** The four turns the service gave for the third of October, and the last of the day before. */
private val TURNS = answered(
    measured("2026-10-02T20:00:00.000+01:00", 150),
    measured("2026-10-03T01:21:00.000+01:00", -132),
    measured("2026-10-03T07:42:00.000+01:00", 146),
    measured("2026-10-03T13:45:00.000+01:00", -130),
    measured("2026-10-03T20:11:00.000+01:00", 159),
    measured("2026-10-04T02:22:00.000+01:00", -115),
)

/** The service's answers by the grouping asked for, which is how the two series are told apart. */
private fun service(vararg answers: Pair<String, Posted>): (String, String) -> Posted = { _, body ->
    answers.firstOrNull { (grouping, _) -> "\"Groepering\":{\"Code\":\"$grouping\"}" in body }?.second
        ?: Posted(204, "")
}

private fun calculators(post: (String, String) -> Posted): List<TideCalculator> =
    rijkswaterstaatCalculators(stationsOf(STATIONS), post)

private fun astronomical(post: (String, String) -> Posted): TideCalculator =
    calculators(post).single { it.accuracy == Accuracy.LOCAL }

class StationsTest {

    @Test
    fun `a line is a station with its five cells`() {
        val stations = stationsOf(STATIONS)
        assertEquals(3, stations.size)
        assertEquals(Station("stavenisse", "Stavenisse", 51.598, 4.004, "afm"), stations[0])
    }

    @Test
    fun `a site is covered by the nearest station carrying the series, within reach`() {
        val calculators = calculators(service())
        for (calculator in calculators) assertTrue(calculator.covers(BRIDGE_LATITUDE, BRIDGE_LONGITUDE), calculator.name)
        // The Red Sea has no Dutch gauge.
        for (calculator in calculators) assertFalse(calculator.covers(27.9, 34.3), calculator.name)
        // Dalem carries the prediction only, so a site beside it has the prediction only.
        val beside = calculators.filter { it.covers(51.82, 5.02) }.map { it.accuracy }
        assertEquals(listOf(Accuracy.LOCAL), beside)
    }

    @Test
    fun `most accurate first, and each covers its own days`() {
        val calculators = calculators(service())
        assertEquals(listOf(Accuracy.MEASURED, Accuracy.FORECAST, Accuracy.LOCAL), calculators.map { it.accuracy })
        val today = DAY
        val (measured, forecast, predicted) = calculators
        assertTrue(measured.coversDay(today.plusDays(-1), today))
        assertFalse(measured.coversDay(today, today))
        assertFalse(forecast.coversDay(today.plusDays(-1), today))
        assertTrue(forecast.coversDay(today, today))
        assertTrue(forecast.coversDay(today.plusDays(FORECAST_DAYS.toLong()), today))
        assertFalse(forecast.coversDay(today.plusDays(FORECAST_DAYS + 1L), today))
        assertTrue(predicted.coversDay(Date(2020, 1, 1), today))
        assertTrue(predicted.coversDay(Date(2027, 12, 31), today))
    }
}

class DutchClockTest {

    @Test
    fun `summer time begins and ends on the last Sunday of March and October`() {
        assertEquals(Date(2026, 3, 29), lastSunday(2026, 3))
        assertEquals(Date(2026, 10, 25), lastSunday(2026, 10))
        assertEquals(Date(2027, 3, 28), lastSunday(2027, 3))
        assertEquals(Date(2025, 10, 26), lastSunday(2025, 10))
    }

    @Test
    fun `a stamp in MET is read as an instant`() {
        val instant = instantOf("2026-10-03T01:21:00.000+01:00")
        assertEquals(Moment(Date(2026, 10, 3), Time(0, 21, 0)).epochSecond, instant)
        assertEquals(instantOf("2026-10-03T01:21:00+01:00"), instant)
        assertNull(instantOf("2026-10-03 01:21"))
    }

    @Test
    fun `the Dutch clock is an hour ahead of MET in summer and MET itself in winter`() {
        val summer = instantOf("2026-10-03T01:21:00.000+01:00")!!
        assertEquals(Moment(Date(2026, 10, 3), Time(2, 21, 0)), dutchClock(summer))
        val winter = instantOf("2026-12-30T04:57:00.000+01:00")!!
        assertEquals(Moment(Date(2026, 12, 30), Time(4, 57, 0)), dutchClock(winter))
    }

    @Test
    fun `the change is at one in the morning GMT`() {
        val before = Moment(Date(2026, 3, 29), Time(0, 59, 59)).epochSecond
        assertEquals(Moment(Date(2026, 3, 29), Time(1, 59, 59)), dutchClock(before))
        assertEquals(Moment(Date(2026, 3, 29), Time(3, 0, 0)), dutchClock(before + 1))
        val ending = Moment(Date(2026, 10, 25), Time(0, 59, 59)).epochSecond
        assertEquals(Moment(Date(2026, 10, 25), Time(2, 59, 59)), dutchClock(ending))
        assertEquals(Moment(Date(2026, 10, 25), Time(2, 0, 0)), dutchClock(ending + 1))
    }
}

class TurnsInTest {

    private fun at(minute: Int, height: Double): Reading =
        Reading(Moment(DAY, Time(minute / 60, minute % 60, 0)), height)

    @Test
    fun `the water rising to a height and falling from it is a high water, and the other way a low`() {
        val curve = listOf(at(0, 0.0), at(10, 1.0), at(20, 2.0), at(30, 1.0), at(40, -1.0), at(50, 0.0))
        assertEquals(
            listOf(Extreme(at(20, 2.0).at, 2.0, true), Extreme(at(40, -1.0).at, -1.0, false)),
            turnsIn(curve),
        )
    }

    @Test
    fun `a run of equal readings is one turn, at its middle`() {
        val curve = listOf(at(0, 0.0), at(10, 1.0), at(20, 1.0), at(30, 1.0), at(40, 0.0))
        assertEquals(listOf(Extreme(at(20, 1.0).at, 1.0, true)), turnsIn(curve))
    }

    @Test
    fun `a ripple smaller than the least turn is not a turn`() {
        // Three centimetres up on the way down, as a forecast stitched from two runs holds.
        val curve = listOf(
            at(0, 1.0), at(10, 0.5), at(20, -1.25), at(30, -1.22), at(40, -1.4), at(50, -1.54), at(60, -1.0),
        )
        assertEquals(listOf(Extreme(at(50, -1.54).at, -1.54, false)), turnsIn(curve))
        // The same bump is two turns to whoever asks for every centimetre.
        assertEquals(3, turnsIn(curve, least = 0.01).size)
    }

    @Test
    fun `the ends of a curve are never turns`() {
        assertEquals(emptyList(), turnsIn(listOf(at(0, 2.0), at(10, 1.0), at(20, 0.0))))
        assertEquals(emptyList(), turnsIn(listOf(at(0, 2.0), at(10, 1.0))))
        assertEquals(emptyList(), turnsIn(emptyList()))
        // Still rising when the curve stops: the top is not known to be one.
        assertEquals(emptyList(), turnsIn(listOf(at(0, 0.0), at(10, 1.0), at(20, 2.0))))
    }
}

class RijkswaterstaatTest {

    @Test
    fun `the astronomical turns are read, kept to the day, and told high from low on the Dutch clock`() {
        val found = assertIs<Tidal.Found>(astronomical(service("GETETBRKD2" to Posted(200, TURNS))).tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        val tides = found.tides
        assertEquals("Stavenisse", tides.station)
        assertEquals(9.0, tides.kilometres, 0.5)
        assertEquals("NAP", tides.datum)
        // October the third is summer time, so the service's 01:21 MET is 02:21 on the clock.
        assertEquals(
            listOf(
                Extreme(Moment(DAY, Time(2, 21, 0)), -1.32, false),
                Extreme(Moment(DAY, Time(8, 42, 0)), 1.46, true),
                Extreme(Moment(DAY, Time(14, 45, 0)), -1.30, false),
                Extreme(Moment(DAY, Time(21, 11, 0)), 1.59, true),
            ),
            tides.extremes,
        )
        assertEquals(emptyList(), tides.curve)
    }

    @Test
    fun `the period asked for is the day in MET with a margin either side, at the nearest station`() {
        var asked = ""
        astronomical { _, body ->
            asked = body
            Posted(200, TURNS)
        }.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY)
        assertTrue("\"Locatie\":{\"Code\":\"stavenisse\"}" in asked, asked)
        assertTrue("\"Begindatumtijd\":\"2026-10-02T22:00:00.000+01:00\"" in asked, asked)
        assertTrue("\"Einddatumtijd\":\"2026-10-04T02:00:00.000+01:00\"" in asked, asked)
    }

    @Test
    fun `the forecast finds its turns in the curve`() {
        val curve = answered(
            measured("2026-10-03T02:00:00.000+01:00", -100),
            measured("2026-10-03T02:10:00.000+01:00", -120),
            measured("2026-10-03T02:20:00.000+01:00", -110),
            measured("2026-10-03T02:30:00.000+01:00", 0),
            measured("2026-10-03T02:40:00.000+01:00", 150),
            measured("2026-10-03T02:50:00.000+01:00", 150),
            measured("2026-10-03T03:00:00.000+01:00", 130),
        )
        val forecast = calculators(service("" to Posted(200, curve))).single { it.accuracy == Accuracy.FORECAST }
        val found = assertIs<Tidal.Found>(forecast.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        assertEquals(7, found.tides.curve.size)
        assertEquals(
            listOf(
                Extreme(Moment(DAY, Time(3, 10, 0)), -1.2, false),
                Extreme(Moment(DAY, Time(3, 40, 0)), 1.5, true),
            ),
            found.tides.extremes,
        )
    }

    @Test
    fun `a day the service has nothing for is none, and a service out of reach is offline`() {
        val none = assertIs<Tidal.None>(astronomical(service()).tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        assertEquals("Rijkswaterstaat has nothing for Stavenisse on 2026-10-03", none.reason)
        val refused = astronomical { _, _ -> Posted(200, """{"Succesvol":false,"Foutmelding":"Geen waarnemingen gevonden"}""") }
        assertEquals(Tidal.None("Rijkswaterstaat refused: Geen waarnemingen gevonden"), refused.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        val offline = astronomical { _, _ -> throw IllegalStateException("no route to host") }
        assertEquals(Tidal.Offline("Rijkswaterstaat could not be reached: no route to host"), offline.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
    }

    @Test
    fun `a turn in the margin is read for its neighbour's sake and left out of the day`() {
        // High water at 23:50 the evening before, on the clock, and the day's own low after it.
        val curve = answered(
            measured("2026-10-02T22:30:00.000+01:00", 100),
            measured("2026-10-02T22:50:00.000+01:00", 150),
            measured("2026-10-02T23:10:00.000+01:00", 100),
            measured("2026-10-03T01:00:00.000+01:00", -120),
            measured("2026-10-03T02:00:00.000+01:00", 0),
        )
        val forecast = calculators(service("" to Posted(200, curve))).single { it.accuracy == Accuracy.FORECAST }
        val found = assertIs<Tidal.Found>(forecast.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        assertEquals(listOf(Extreme(Moment(DAY, Time(2, 0, 0)), -1.2, false)), found.tides.extremes)
        assertEquals(3, found.tides.curve.size)
    }

    @Test
    fun `a station holding nothing gives way to the next nearest`() {
        val stations = stationsOf("near\tNear\t51.62\t3.9\ta\nfurther\tFurther\t51.598\t4.004\ta\n")
        val asked = ArrayList<String>()
        val calculator = rijkswaterstaatCalculators(stations) { _, body ->
            asked += if ("\"Code\":\"near\"" in body) "near" else "further"
            if ("\"Code\":\"near\"" in body || "GETETBRKD2" !in body) Posted(204, "") else Posted(200, TURNS)
        }.single { it.accuracy == Accuracy.LOCAL }
        val found = assertIs<Tidal.Found>(calculator.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY))
        assertEquals("Further", found.tides.station)
        // The turns from each in turn, and then the curve from the one that answered.
        assertEquals(listOf("near", "further", "further"), asked)
        // Where neither answers, what is said is what the nearest said.
        val none = rijkswaterstaatCalculators(stations) { _, _ -> Posted(204, "") }.single { it.accuracy == Accuracy.LOCAL }
        assertEquals(
            Tidal.None("Rijkswaterstaat has nothing for Near on 2026-10-03"),
            none.tides(BRIDGE_LATITUDE, BRIDGE_LONGITUDE, DAY),
        )
    }

    @Test
    fun `a site with no station in reach is none`() {
        val none = assertIs<Tidal.None>(astronomical(service()).tides(27.9, 34.3, DAY))
        assertEquals("no station within 50 km", none.reason)
    }
}
