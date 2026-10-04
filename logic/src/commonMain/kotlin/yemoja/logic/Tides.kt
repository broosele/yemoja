package yemoja.logic

import yemoja.data.Date
import yemoja.data.Moment
import yemoja.data.Stored
import yemoja.data.Time
import yemoja.data.ValueFormatException
import yemoja.data.json.Json
import yemoja.data.json.JsonFormatException
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

/*
 * The tide at a dive site on a day: when the water turns, how high it stands, and the curve
 * between. Each calculator here names the sites and the days it can answer for, and the window
 * offers whichever cover the site chosen.
 *
 * See ../../../../../doc.md — `LOGIC-44`. `FEAT-26`.
 */

/**
 * Accuracy is how close a tide calculator's answer stands to the water on the day, least first.
 *
 * Ordered, so that the most accurate calculator covering a site and a day is the one offered
 * first.
 */
enum class Accuracy(val label: String) {
    /** A model of the whole ocean, which knows no coast in detail. */
    WORLD("worldwide model"),

    /** A model of one sea, leaning towards the site. */
    REGIONAL("regional model"),

    /** The harmonic prediction at a station near the site: the astronomical tide and nothing else. */
    LOCAL("local prediction"),

    /** The prediction with the coming weather added, which the astronomical tide leaves out. */
    FORECAST("local forecast"),

    /** What the gauge at the station read. */
    MEASURED("measured"),
}

/** Extreme is one turn of the tide: when, how high the water stood, and which way it turned. */
data class Extreme(
    /** In the calculator's clock, which [Tides.clock] names. */
    val at: Moment,
    /** In metres above the calculator's datum, which [Tides.datum] names. */
    val height: Double,
    val high: Boolean,
)

/** Reading is one point of a day's curve: when, and how high the water stood. */
data class Reading(val at: Moment, val height: Double)

/**
 * Flow is one point of a day's current: when, and how fast the water ran, in metres a second.
 *
 * Signed: positive is the flood, running in as the water rises, and negative the ebb.
 */
data class Flow(val at: Moment, val speed: Double)

/** Slack is a moment the current stops and turns, and which way it turns to. */
data class Slack(val at: Moment, val toFlood: Boolean)

/**
 * Tides is what a calculator answers for a site and a day.
 *
 * The extremes are the day's, in order; the curve is the same day at the calculator's own interval,
 * and empty where it has none. Every time is in the clock [clock] names, and every height is in
 * metres above the datum [datum] names, so a reader is told what a figure is against rather than
 * left to guess. [kilometres] is how far the station answering is from the site asked about.
 *
 * [measuredUntil] is the last moment the curve is a gauge's reading, where the rest of it is a
 * forecast. It is absent where the curve is of one kind throughout.
 *
 * [flows] and [slacks] are the current at the place answering, and empty where a calculator knows
 * only the water's height.
 *
 * Immutable.
 */
class Tides(
    val station: String,
    val kilometres: Double,
    val datum: String,
    val clock: String,
    extremes: List<Extreme>,
    curve: List<Reading>,
    val measuredUntil: Moment? = null,
    flows: List<Flow> = emptyList(),
    slacks: List<Slack> = emptyList(),
) {
    val extremes: List<Extreme> = extremes.toList()
    val curve: List<Reading> = curve.toList()
    val flows: List<Flow> = flows.toList()
    val slacks: List<Slack> = slacks.toList()
}

/** Tidal is what asking a calculator came to. */
sealed class Tidal {
    class Found(val tides: Tides) : Tidal()

    /** The calculator covers the site and the day, and its source had nothing for them. */
    data class None(val reason: String) : Tidal()

    /** The calculator needs a network and found none. */
    data class Offline(val reason: String) : Tidal()
}

/**
 * TideCalculator is one way of working out the tide at a site: what it is called, how close to
 * the water it stands, and which sites and days it answers for.
 *
 * A site is a position and nothing more: the logbook's dive sites know nothing of tides, so a
 * calculator decides from latitude and longitude alone whether a site is one it covers. A lake
 * beside the sea has no tide, so being near a tide station is not enough, and a calculator has
 * to know which water a position lies in. The window lists the calculators covering a site, most
 * accurate first, and asks the one chosen.
 */
interface TideCalculator {
    val name: String
    val accuracy: Accuracy

    /** Whether asking it reaches over a network, which the window says beside its name. */
    val needsNetwork: Boolean

    fun covers(latitude: Double, longitude: Double): Boolean

    /** Whether [day] is one it can answer for, judged against [today]. */
    fun coversDay(day: Date, today: Date): Boolean

    /** The tide at a position on a day. Blocking where [needsNetwork]: the caller moves it off the screen's thread. */
    fun tides(latitude: Double, longitude: Double, day: Date): Tidal
}

/**
 * Station is one water-level gauge of the Rijkswaterstaat network, as libraries/tides/rijkswaterstaat.txt
 * lists it.
 *
 * [series] holds a letter for each series the station carries: `a` the astronomical prediction,
 * `f` the forecast, `m` the measurement.
 */
data class Station(
    val code: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val series: String,
)

/** The stations in [text], which is the library file, one a line, five cells a line. */
fun stationsOf(text: String): List<Station> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val cells = line.split('\t')
        require(cells.size == 5) { "a station should have five cells, but this line has ${cells.size}: $line" }
        Station(cells[0], cells[1], cells[2].toDouble(), cells[3].toDouble(), cells[4])
    }
    .toList()

/**
 * Water is one tidal water a calculator covers, as libraries/tides/waters.txt outlines it.
 *
 * The outline is one ring of longitude and latitude alternating, as the map's shapes are written.
 * It is drawn generously over land and exactly along a dam, since a dive site stands on the shore
 * and what must be told apart is the water either side of a dam: the Oosterschelde from the
 * Grevelingen, the sea from a lake behind the dunes.
 */
class Water(val name: String, ring: DoubleArray) {
    private val ring: DoubleArray = ring.copyOf()

    init {
        require(ring.size >= 6 && ring.size % 2 == 0) {
            "an outline should be three or more pairs of longitude and latitude, but $name has ${ring.size} numbers"
        }
    }

    /** Whether a position lies inside the outline, by counting the edges a line due east of it crosses. */
    fun holds(latitude: Double, longitude: Double): Boolean {
        var inside = false
        val points = ring.size / 2
        for (at in 0..<points) {
            val next = (at + 1) % points
            val x = ring[2 * at]
            val y = ring[2 * at + 1]
            val nextX = ring[2 * next]
            val nextY = ring[2 * next + 1]
            if ((y > latitude) != (nextY > latitude) && longitude < (nextX - x) * (latitude - y) / (nextY - y) + x) {
                inside = !inside
            }
        }
        return inside
    }
}

/** The waters in [text], which is the library file: one a line, a name, a tab, and the outline. */
fun watersOf(text: String): List<Water> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val cells = line.split('\t')
        require(cells.size == 2) { "a water should have two cells, but this line has ${cells.size}: $line" }
        Water(cells[0], cells[1].trim().split(' ').map { it.toDouble() }.toDoubleArray())
    }
    .toList()

/** Posted is what a POST came back with. */
class Posted(val status: Int, val body: String)

/**
 * Posts [body] as JSON to [url] and answers with what came back, or throws where nothing did.
 *
 * Per platform, because common Kotlin has no socket. A JVM and Android share one answer; a browser
 * build has none and throws, the website never asking for a tide.
 */
expect fun postJson(url: String, body: String): Posted

/** Asks [url] with a GET and answers with what came back, or throws where nothing did. Per platform, as [postJson] is. */
expect fun getJson(url: String): Posted

/**
 * Every tide calculator the application has, read from the library's own files, which [text]
 * gives whole by the path they are bundled under.
 *
 * The one place a front end asks, so that a calculator added here reaches every one of them.
 */
fun tideCalculators(text: (path: String) -> String): List<TideCalculator> {
    val waters = watersOf(text("libraries/tides/waters.txt"))
    return listOf(scaldisOostCalculator(placesOf(text("libraries/tides/scaldis-oost.txt")), waters)) +
        rijkswaterstaatCalculators(stationsOf(text("libraries/tides/rijkswaterstaat.txt")), waters)
}

/**
 * The three calculators reading the Rijkswaterstaat WaterWebservices, from [stations] as
 * libraries/tides/rijkswaterstaat.txt lists them, [waters] as libraries/tides/waters.txt outlines
 * them, and [post] as the way to the service.
 *
 * Most accurate first: the measurement, the forecast, the astronomical prediction. One service
 * answers all three, in Dutch clock time against NAP. `LOGIC-44`.
 */
fun rijkswaterstaatCalculators(
    stations: List<Station>,
    waters: List<Water>,
    post: (url: String, body: String) -> Posted = ::postJson,
): List<TideCalculator> {
    val service = Rijkswaterstaat(stations, waters, post)
    return listOf(RijkswaterstaatMeasured(service), RijkswaterstaatForecast(service), RijkswaterstaatAstronomical(service))
}

/** The furthest a station may be from a site and still be the one answering for it, in metres. */
const val STATION_REACH = 50_000.0

/** How many days ahead of today the forecast runs. */
const val FORECAST_DAYS = 2

/**
 * Rijkswaterstaat is what the three calculators reading its service share: the stations, the
 * waters they stand in, the way to the service, and the request a day's series is asked for with.
 */
private class Rijkswaterstaat(
    stations: List<Station>,
    val waters: List<Water>,
    val post: (url: String, body: String) -> Posted,
) {
    /** The stations of each water. One standing in no water answers for no site. */
    private val stationsIn: Map<Water, List<Station>> =
        waters.associateWith { water -> stations.filter { water.holds(it.latitude, it.longitude) } }

    /**
     * The stations carrying every one of [series] that may answer for a position, the nearest
     * first: those in the water the position lies in, within reach of it.
     *
     * A position in none of the waters has no stations. The gauge outside a dam is the nearest
     * one to a site on the lake behind it, and its tide is not the lake's.
     */
    fun near(latitude: Double, longitude: Double, series: String): List<Pair<Station, Double>> {
        val water = waters.firstOrNull { it.holds(latitude, longitude) } ?: return emptyList()
        return stationsIn.getValue(water).filter { station -> series.all { it in station.series } }
            .map { it to metresApart(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, metres) -> metres <= STATION_REACH }
            .sortedBy { (_, metres) -> metres }
    }

    /**
     * A day's readings from the nearest station that has any, or why there are none.
     *
     * A gauge is out for a day now and then, and the station beside it is a better answer than
     * none, so the next nearest is asked where the nearest holds nothing, [STATIONS_TRIED] in all.
     * What is said where none of them answers is what the nearest said. A service out of reach is
     * not asked twice.
     */
    fun nearestRead(latitude: Double, longitude: Double, series: String, day: Date, process: String, grouping: String): Sought {
        val near = near(latitude, longitude, series).take(STATIONS_TRIED)
        var first: Sought.None? = null
        for ((station, metres) in near) {
            when (val read = readings(station, day, process, grouping)) {
                is Series.Offline -> return Sought.Offline(read.reason)
                is Series.None -> if (first == null) first = Sought.None(read.reason)
                is Series.Read -> return Sought.Read(station, metres, read.readings)
            }
        }
        return first ?: Sought.None(
            "no station within ${(STATION_REACH / METRES_IN_KILOMETRE).roundToInt()} km in the same water",
        )
    }

    /**
     * The readings of one series at [station] around the Dutch calendar day [day], in Dutch clock
     * time, or why there are none.
     *
     * Asked for with [MARGIN] either side and not trimmed to the day, so a turn of the tide at the
     * stroke of midnight has the water either side of it to be judged against; whoever asks trims.
     * The service speaks MET all year, which is why the day's bounds are widened rather than
     * converted exactly.
     */
    fun readings(station: Station, day: Date, process: String, grouping: String): Series {
        val from = Moment(day, Time(0, 0, 0)).plusSeconds(-MARGIN)
        val until = Moment(day.plusDays(1), Time(0, 0, 0)).plusSeconds(MARGIN)
        val body = """{"AquoPlusWaarnemingMetadata":{"AquoMetadata":{"Compartiment":{"Code":"OW"},""" +
            """"Grootheid":{"Code":"WATHTE"},"Hoedanigheid":{"Code":"NAP"},""" +
            """"Groepering":{"Code":"$grouping"},"ProcesType":"$process"}},""" +
            """"Locatie":{"Code":"${station.code}"},""" +
            """"Periode":{"Begindatumtijd":"${met(from)}","Einddatumtijd":"${met(until)}"}}"""
        val posted = try {
            post(OBSERVATIONS, body)
        } catch (unreachable: Exception) {
            return Series.Offline("Rijkswaterstaat could not be reached: ${unreachable.message ?: unreachable::class.simpleName}")
        }
        if (posted.status == NO_CONTENT || posted.body.isBlank()) return Series.None(nothing(station, day))
        if (posted.status != OK) return Series.None("Rijkswaterstaat answered ${posted.status} for ${station.name}")
        val read = try {
            Json.parse(posted.body)
        } catch (refused: JsonFormatException) {
            return Series.None("Rijkswaterstaat's answer for ${station.name} could not be read: ${refused.message}")
        }
        val members = (read as? Stored.Members)?.members ?: return Series.None("Rijkswaterstaat's answer for ${station.name} is not an object")
        if ((members["Succesvol"] as? Stored.Leaf)?.value != true) {
            val why = (members["Foutmelding"] as? Stored.Leaf)?.value as? String
            return Series.None(why?.let { "Rijkswaterstaat refused: $it" } ?: nothing(station, day))
        }
        val observations = (members["WaarnemingenLijst"] as? Stored.Elements)?.elements.orEmpty()
        val readings = observations.flatMap { observation ->
            val measurements = ((observation as? Stored.Members)?.members?.get("MetingenLijst") as? Stored.Elements)
                ?.elements.orEmpty()
            measurements.mapNotNull(::readingOf)
        }.sortedBy { it.at }
        if (readings.none { it.at.date == day }) return Series.None(nothing(station, day))
        return Series.Read(readings)
    }

    private fun nothing(station: Station, day: Date): String =
        "Rijkswaterstaat has nothing for ${station.name} on $day"

    /** One measurement as a reading in Dutch clock time and metres, or none where it is not a number. */
    private fun readingOf(measurement: Stored): Reading? {
        val members = (measurement as? Stored.Members)?.members ?: return null
        val stamp = (members["Tijdstip"] as? Stored.Leaf)?.value as? String ?: return null
        val value = ((members["Meetwaarde"] as? Stored.Members)?.members?.get("Waarde_Numeriek") as? Stored.Leaf)?.value
        val centimetres = when (value) {
            is Double -> value
            is Long -> value.toDouble()
            else -> return null
        }
        // The service marks a value it has none for with a number nothing real reaches.
        if (centimetres <= MISSING) return null
        val at = dutchClock(instantOf(stamp) ?: return null)
        return Reading(at, centimetres / CENTIMETRES_IN_METRE)
    }

    /** [moment] in MET, which is what the service wants a period written in. */
    private fun met(moment: Moment): String = "${moment.date}T${moment.time}.000+01:00"
}

/** Series is the readings of one series around a day at one station, or why there are none. */
private sealed class Series {
    class Read(val readings: List<Reading>) : Series()
    class None(val reason: String) : Series()
    class Offline(val reason: String) : Series()
}

/** Sought is the readings around a day from the nearest station that had any, or why none had. */
private sealed class Sought {
    class Read(val station: Station, val metres: Double, val readings: List<Reading>) : Sought()
    class None(val reason: String) : Sought()
    class Offline(val reason: String) : Sought()
}

private const val OBSERVATIONS =
    "https://ddapi20-waterwebservices.rijkswaterstaat.nl/ONLINEWAARNEMINGENSERVICES/OphalenWaarnemingen"

private const val OK = 200

/** What the service answers with where a period holds no readings. */
private const val NO_CONTENT = 204

/** The service's own mark for a value it does not have: -999999999, and anything as far below. */
private const val MISSING = -999_999.0

private const val CENTIMETRES_IN_METRE = 100.0

/** How far either side of a day its readings are asked for, in seconds. */
private const val MARGIN = 2L * Time.SECONDS_IN_HOUR

/** How many stations are asked, nearest first, before a day is said to have nothing. */
private const val STATIONS_TRIED = 3

private const val DATUM = "NAP"

private const val DUTCH_CLOCK = "Dutch clock time"

/**
 * RijkswaterstaatAstronomical is the astronomical tide at the nearest station: the harmonic
 * prediction, with no weather in it.
 *
 * The extremes are the service's own, to the minute; the curve is the same station's ten-minute
 * series, and absent where the service has the extremes alone.
 */
private class RijkswaterstaatAstronomical(private val service: Rijkswaterstaat) : TideCalculator {
    override val name: String = "Rijkswaterstaat astronomical tide"
    override val accuracy: Accuracy = Accuracy.LOCAL
    override val needsNetwork: Boolean = true

    override fun covers(latitude: Double, longitude: Double): Boolean =
        service.near(latitude, longitude, ASTRONOMICAL).isNotEmpty()

    /** Any day: the service says itself which years it has computed. */
    override fun coversDay(day: Date, today: Date): Boolean = true

    override fun tides(latitude: Double, longitude: Double, day: Date): Tidal {
        val turns = when (val sought = service.nearestRead(latitude, longitude, ASTRONOMICAL, day, "astronomisch", EXTREMES)) {
            is Sought.Offline -> return Tidal.Offline(sought.reason)
            is Sought.None -> return Tidal.None(sought.reason)
            is Sought.Read -> sought
        }
        val curve = when (val read = service.readings(turns.station, day, "astronomisch", "")) {
            is Series.Offline -> return Tidal.Offline(read.reason)
            is Series.None -> emptyList()
            is Series.Read -> read.readings
        }
        return Tidal.Found(
            Tides(
                station = turns.station.name,
                kilometres = turns.metres / METRES_IN_KILOMETRE,
                datum = DATUM,
                clock = DUTCH_CLOCK,
                extremes = turnsOf(turns.readings).filter { it.at.date == day },
                curve = curve.filter { it.at.date == day },
            ),
        )
    }

    /**
     * The service's extremes carry no mark for high or low, so each is told from the one beside it.
     *
     * One alone has nothing beside it and is judged against the datum, which the tide stands either
     * side of.
     */
    private fun turnsOf(turns: List<Reading>): List<Extreme> = turns.mapIndexed { index, turn ->
        val neighbour = turns.getOrNull(index + 1) ?: turns.getOrNull(index - 1)
        Extreme(turn.at, turn.height, high = if (neighbour == null) turn.height > 0 else turn.height > neighbour.height)
    }
}

/**
 * RijkswaterstaatForecast is the tide at the nearest station as far as its gauge has read it, and
 * the service's forecast from there on.
 *
 * **The forecast's past is never shown.** For a day under way the series holds steps of ten
 * centimetres or more a few times a day, so that part of it is no curve of the water. What the
 * gauge read stands in its place, which is why only a station with both series is asked. A day
 * that has not begun has no steps. `LOGIC-44`.
 */
private class RijkswaterstaatForecast(private val service: Rijkswaterstaat) : TideCalculator {
    override val name: String = "Rijkswaterstaat gauge and forecast"
    override val accuracy: Accuracy = Accuracy.FORECAST
    override val needsNetwork: Boolean = true

    override fun covers(latitude: Double, longitude: Double): Boolean =
        service.near(latitude, longitude, BOTH).isNotEmpty()

    /** Today and the days the forecast runs ahead. */
    override fun coversDay(day: Date, today: Date): Boolean =
        day >= today && today.daysUntil(day) <= FORECAST_DAYS

    override fun tides(latitude: Double, longitude: Double, day: Date): Tidal {
        val forecast = when (val sought = service.nearestRead(latitude, longitude, BOTH, day, "verwachting", "")) {
            is Sought.Offline -> return Tidal.Offline(sought.reason)
            is Sought.None -> return Tidal.None(sought.reason)
            is Sought.Read -> sought
        }
        // Nothing measured is the ordinary answer for a day that has not begun.
        val measured = when (val read = service.readings(forecast.station, day, "meting", "")) {
            is Series.Offline -> return Tidal.Offline(read.reason)
            is Series.None -> emptyList()
            is Series.Read -> read.readings
        }
        val last = measured.lastOrNull()
        val ahead = forecast.readings.filter { last == null || it.at > last.at }
        val curve = measured + ahead
        return Tidal.Found(
            Tides(
                station = forecast.station.name,
                kilometres = forecast.metres / METRES_IN_KILOMETRE,
                datum = DATUM,
                clock = DUTCH_CLOCK,
                extremes = turnsAcross(measured, ahead).filter { it.at.date == day },
                curve = curve.filter { it.at.date == day },
                measuredUntil = last?.at?.takeIf { it.date == day && ahead.isNotEmpty() },
            ),
        )
    }
}

/**
 * The turns of the tide in a gauge's readings followed by a forecast's.
 *
 * The forecast seldom starts where the gauge left off, and a step down in the middle of a rise
 * would read as a high water and a low water ten minutes apart. So the turns are looked for with
 * the forecast moved to meet the gauge, and each is then given the height it has unmoved.
 */
internal fun turnsAcross(measured: List<Reading>, ahead: List<Reading>): List<Extreme> {
    val last = measured.lastOrNull()
    val first = ahead.firstOrNull()
    if (last == null || first == null) return turnsIn(measured + ahead)
    val step = last.height - first.height
    val unmoved = ahead.associate { it.at to it.height }
    return turnsIn(measured + ahead.map { Reading(it.at, it.height + step) })
        .map { turn -> unmoved[turn.at]?.let { turn.copy(height = it) } ?: turn }
}

/** RijkswaterstaatMeasured is what the gauge at the nearest station read, for a day that is over. */
private class RijkswaterstaatMeasured(private val service: Rijkswaterstaat) : TideCalculator {
    override val name: String = "Rijkswaterstaat gauge"
    override val accuracy: Accuracy = Accuracy.MEASURED
    override val needsNetwork: Boolean = true

    override fun covers(latitude: Double, longitude: Double): Boolean =
        service.near(latitude, longitude, MEASURED).isNotEmpty()

    /** Days before today: today's gauge has not seen today's turns yet, which the forecast has. */
    override fun coversDay(day: Date, today: Date): Boolean = day < today

    override fun tides(latitude: Double, longitude: Double, day: Date): Tidal =
        service.curved(latitude, longitude, day, MEASURED, "meting")
}

/** A day's tide from a ten-minute series alone, its turns found in the curve. */
private fun Rijkswaterstaat.curved(latitude: Double, longitude: Double, day: Date, series: String, process: String): Tidal =
    when (val sought = nearestRead(latitude, longitude, series, day, process, "")) {
        is Sought.Offline -> Tidal.Offline(sought.reason)
        is Sought.None -> Tidal.None(sought.reason)
        is Sought.Read -> Tidal.Found(
            Tides(
                station = sought.station.name,
                kilometres = sought.metres / METRES_IN_KILOMETRE,
                datum = DATUM,
                clock = DUTCH_CLOCK,
                extremes = turnsIn(sought.readings).filter { it.at.date == day },
                curve = sought.readings.filter { it.at.date == day },
            ),
        )
    }

/** The grouping the service files the computed extremes of the astronomical series under. */
private const val EXTREMES = "GETETBRKD2"

private const val ASTRONOMICAL = "a"
private const val MEASURED = "m"

/** A station with a forecast and a gauge, which the forecast calculator needs both of. */
private const val BOTH = "fm"

private const val METRES_IN_KILOMETRE = 1000.0

/** The least the water must move away from a turn for it to count as one, in metres. */
const val LEAST_TURN = 0.10

/**
 * The turns of the tide in [curve]: each height the water rose to and then fell from by [least],
 * and each it fell to and then rose from by as much.
 *
 * A ripple smaller than [least] is not a turn. A gauge reads to the centimetre and a forecast is
 * stitched from runs of a model, so either holds bumps of a few centimetres that a reader would
 * not call a tide. A turn is placed at the middle of the readings that share its height, the water
 * standing at one centimetre for twenty minutes around it.
 *
 * Nothing is said of the water before the first turn or after the last: a turn is known only once
 * the water has left it, which is why a day is asked for with a margin either side.
 */
fun turnsIn(curve: List<Reading>, least: Double = LEAST_TURN): List<Extreme> {
    if (curve.isEmpty()) return emptyList()
    val turns = ArrayList<Extreme>()
    // The highest and lowest readings since the last turn, each as the run of readings sharing it.
    var highest = 0..0
    var lowest = 0..0
    // Which way the water is known to be going, and unknown until it has moved by `least`.
    var rising: Boolean? = null
    fun middle(run: IntRange): Reading = curve[(run.first + run.last) / 2]
    for ((index, reading) in curve.withIndex()) {
        val top = curve[highest.first].height
        val bottom = curve[lowest.first].height
        when {
            reading.height > top -> highest = index..index
            reading.height == top && highest.last == index - 1 -> highest = highest.first..index
        }
        when {
            reading.height < bottom -> lowest = index..index
            reading.height == bottom && lowest.last == index - 1 -> lowest = lowest.first..index
        }
        val fallen = reading.height <= curve[highest.first].height - least
        val risen = reading.height >= curve[lowest.first].height + least
        when (rising) {
            null -> if (fallen) {
                rising = false
                lowest = index..index
            } else if (risen) {
                rising = true
                highest = index..index
            }
            true -> if (fallen) {
                turns += Extreme(middle(highest).at, curve[highest.first].height, high = true)
                rising = false
                lowest = index..index
            }
            false -> if (risen) {
                turns += Extreme(middle(lowest).at, curve[lowest.first].height, high = false)
                rising = true
                highest = index..index
            }
        }
    }
    return turns
}

/**
 * The instant [written] names, as seconds since the epoch, or none where it is not a time with
 * its offset.
 *
 * The WaterWebservices write `2026-10-03T01:21:00.000+01:00`: a date, a time to the millisecond,
 * and how far that clock stood ahead of GMT. The RWsOS service writes `2026-10-03T00:00:00Z`,
 * which is GMT itself.
 */
fun instantOf(written: String): Long? {
    val match = STAMPED.matchEntire(written.trim()) ?: return null
    val (date, time, sign, hours, minutes) = match.destructured
    val local = try {
        Moment(Date.parse(date), Time.parse(time))
    } catch (refused: IllegalArgumentException) {
        return null
    } catch (refused: ValueFormatException) {
        return null
    }
    // A stamp ending in Z is GMT, and has no offset to take off.
    if (sign.isEmpty()) return local.epochSecond
    val offset = (hours.toLong() * Time.SECONDS_IN_HOUR + minutes.toLong() * Time.SECONDS_IN_MINUTE) *
        (if (sign == "-") -1 else 1)
    return local.epochSecond - offset
}

private val STAMPED = Regex("""(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2})(?:\.\d+)?(?:([+-])(\d{2}):(\d{2})|Z)""")

/**
 * The instant [epochSecond] on the Dutch clock, which is MET in winter and an hour ahead in summer.
 *
 * Summer runs from the last Sunday of March to the last Sunday of October, both at 01:00 GMT, as
 * the whole of the European Union keeps it. The clock is worked out here rather than asked of the
 * platform because the station's clock is the Netherlands' whatever the machine is set to.
 */
fun dutchClock(epochSecond: Long): Moment {
    val year = Moment.of(epochSecond + Time.SECONDS_IN_HOUR).date.year
    val summerFrom = Moment(lastSunday(year, MARCH), Time(1, 0, 0)).epochSecond
    val summerUntil = Moment(lastSunday(year, OCTOBER), Time(1, 0, 0)).epochSecond
    val ahead = if (epochSecond >= summerFrom && epochSecond < summerUntil) 2 else 1
    return Moment.of(epochSecond + ahead * Time.SECONDS_IN_HOUR)
}

/** The last Sunday of a month. */
fun lastSunday(year: Int, month: Int): Date {
    val last = Date(year, month, Date.lengthOfMonth(year, month))
    // The epoch began on a Thursday, four days after a Sunday.
    val sinceSunday = ((last.epochDay + 4) % 7 + 7) % 7
    return Date.ofEpochDay(last.epochDay - sinceSunday)
}

private const val MARCH = 3
private const val OCTOBER = 10

/** [days] later, carrying the month and the year as far as they have to go. */
fun Date.plusDays(days: Long): Date = Date.ofEpochDay(epochDay + days)

// --- The current, from the Scaldis-Oost model.

/** Place is one point Rijkswaterstaat's Scaldis-Oost model gives the current at, as libraries/tides/scaldis-oost.txt lists it. */
data class Place(val code: String, val name: String, val latitude: Double, val longitude: Double)

/** The places in [text], which is the library file, one a line, four cells a line. */
fun placesOf(text: String): List<Place> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val cells = line.split('\t')
        require(cells.size == 4) { "a place should have four cells, but this line has ${cells.size}: $line" }
        Place(cells[0], cells[1], cells[2].toDouble(), cells[3].toDouble())
    }
    .toList()

/**
 * The calculator reading Rijkswaterstaat's Scaldis-Oost model, from [places] as
 * libraries/tides/scaldis-oost.txt lists them, [waters] as libraries/tides/waters.txt outlines them,
 * and [get] as the way to the service. `LOGIC-44`.
 */
fun scaldisOostCalculator(
    places: List<Place>,
    waters: List<Water>,
    get: (url: String) -> Posted = ::getJson,
): TideCalculator = ScaldisOost(places, waters, get)

/** The furthest a model place may be from a site and still be the one answering for it, in metres. */
const val PLACE_REACH = 3_000.0

/** How many places are asked, nearest first, before a day is said to have nothing. */
private const val PLACES_TRIED = 3

/** How many days before today the model's series reaches. */
const val MODEL_DAYS_BACK = 13

/** How many days after today the model's series reaches, the last of them in part. */
const val MODEL_DAYS_AHEAD = 2

/** The least the current must run either way for its turning to count as slack, in metres a second. */
const val LEAST_FLOW = 0.05

/**
 * ScaldisOost is the current and the water level the Scaldis-Oost model gives at a place in the
 * Oosterschelde, which is the dive site itself rather than a station kilometres off.
 *
 * Model output with the weather in it, every ten minutes, from about two weeks back to about forty
 * hours ahead. It ranks with the forecast and is listed before the station's, so where both cover
 * a site it is the one offered first. The service is the one behind the RWsOS viewer, which states
 * no licence and documents no interface. `LOGIC-44`.
 */
private class ScaldisOost(
    private val places: List<Place>,
    private val waters: List<Water>,
    private val get: (url: String) -> Posted,
) : TideCalculator {
    override val name: String = "Rijkswaterstaat Scaldis-Oost model"
    override val accuracy: Accuracy = Accuracy.FORECAST
    override val needsNetwork: Boolean = true

    /** The places in the water a position lies in, within reach, the nearest first, and how far each is. */
    private fun near(latitude: Double, longitude: Double): List<Pair<Place, Double>> {
        val water = waters.firstOrNull { it.holds(latitude, longitude) } ?: return emptyList()
        return places.filter { water.holds(it.latitude, it.longitude) }
            .map { it to metresApart(latitude, longitude, it.latitude, it.longitude) }
            .filter { (_, metres) -> metres <= PLACE_REACH }
            .sortedBy { (_, metres) -> metres }
    }

    override fun covers(latitude: Double, longitude: Double): Boolean = near(latitude, longitude).isNotEmpty()

    override fun coversDay(day: Date, today: Date): Boolean =
        today.daysUntil(day) in -MODEL_DAYS_BACK..MODEL_DAYS_AHEAD

    /**
     * The tide and the current at the nearest place holding all three series, [PLACES_TRIED] at most.
     *
     * **A place with no level is passed over**, because the level is what tells the flood from the
     * ebb: without it the sign of the current is a guess. Zeelandbrug pijler 2 has the current and
     * no level, and pijler 1 a hundred metres off has all three. What is said where none of them
     * answers is what the nearest said.
     */
    override fun tides(latitude: Double, longitude: Double, day: Date): Tidal {
        val near = near(latitude, longitude).take(PLACES_TRIED)
        var first: Tidal.None? = null
        for ((place, metres) in near) {
            when (val read = at(place, metres, day)) {
                is Tidal.None -> if (first == null) first = read
                else -> return read
            }
        }
        return first ?: Tidal.None(
            "no model place within ${(PLACE_REACH / METRES_IN_KILOMETRE).roundToInt()} km in the same water",
        )
    }

    /** The tide and the current at one place, or why there are none. */
    private fun at(place: Place, metres: Double, day: Date): Tidal {
        val read = listOf(SPEED, DIRECTION, LEVEL).map { kind ->
            when (val answer = series(place, day, kind)) {
                is Series.Offline -> return Tidal.Offline(answer.reason)
                is Series.None -> return Tidal.None(answer.reason)
                is Series.Read -> answer.readings
            }
        }
        val (speeds, directions) = read
        val levels = read[2].map { Reading(it.at, it.height / CENTIMETRES_IN_METRE) }
        val flows = signedFlows(speeds, directions, levels)
        return Tidal.Found(
            Tides(
                station = place.name,
                kilometres = metres / METRES_IN_KILOMETRE,
                datum = DATUM,
                clock = DUTCH_CLOCK,
                extremes = turnsIn(levels).filter { it.at.date == day },
                curve = levels.filter { it.at.date == day },
                flows = flows.filter { it.at.date == day },
                slacks = slacksIn(flows).filter { it.at.date == day },
            ),
        )
    }

    /**
     * One of the model's series at [place] around the Dutch calendar day [day], on the Dutch clock,
     * or why there is none.
     *
     * Asked in GMT with [MARGIN] either side of the day as the summer clock has it, which covers the
     * winter clock's day too, so a slack at midnight has the current either side of it.
     */
    private fun series(place: Place, day: Date, series: String): Series {
        val from = Moment(day, Time(0, 0, 0)).plusSeconds(-SUMMER_AHEAD - MARGIN)
        val until = Moment(day.plusDays(1), Time(0, 0, 0)).plusSeconds(MARGIN)
        val url = "$TIMESERIES?locationCode=${place.code}&sourceName=$SOURCE&observationTypeId=$series" +
            "&startTime=${gmt(from)}&endTime=${gmt(until)}"
        val got = try {
            get(url)
        } catch (unreachable: Exception) {
            return Series.Offline(
                "Rijkswaterstaat could not be reached: ${unreachable.message ?: unreachable::class.simpleName}",
            )
        }
        val nothing = "the Scaldis-Oost model has nothing for ${place.name} on $day"
        if (got.status != OK || got.body.isBlank()) return Series.None(nothing)
        val read = try {
            Json.parse(got.body)
        } catch (refused: JsonFormatException) {
            return Series.None("the Scaldis-Oost answer for ${place.name} could not be read: ${refused.message}")
        }
        val results = ((read as? Stored.Members)?.members?.get("results") as? Stored.Elements)?.elements.orEmpty()
        val events = results.flatMap { result ->
            ((result as? Stored.Members)?.members?.get("events") as? Stored.Elements)?.elements.orEmpty()
        }
        val readings = events.mapNotNull(::readingOf).sortedBy { it.at }
        if (readings.none { it.at.date == day }) return Series.None(nothing)
        return Series.Read(readings)
    }

    /** One event as a reading on the Dutch clock, its value as the service gives it, or none where it has no value. */
    private fun readingOf(event: Stored): Reading? {
        val members = (event as? Stored.Members)?.members ?: return null
        val stamp = (members["timeStamp"] as? Stored.Leaf)?.value as? String ?: return null
        val value = when (val held = (members["value"] as? Stored.Leaf)?.value) {
            is Double -> held
            is Long -> held.toDouble()
            else -> return null
        }
        return Reading(dutchClock(instantOf(stamp) ?: return null), value)
    }

    /** [moment] in GMT, as the service wants a period written. */
    private fun gmt(moment: Moment): String = "${moment.date}T${moment.time}Z"
}

private const val TIMESERIES = "https://rwsos.rws.nl/wb-api/dd/2.0/timeseries"

/** The model's own source in the service. */
private const val SOURCE = "SOF_6"

/** The model's current speed, in centimetres a second. */
private const val SPEED = "SG.1"

/** The direction the model's current runs towards, in degrees from north. */
private const val DIRECTION = "SG.2"

/** The model's water level, in centimetres against NAP. */
private const val LEVEL = "WT"

/** How far the summer clock stands ahead of GMT, in seconds. */
private const val SUMMER_AHEAD = 2L * Time.SECONDS_IN_HOUR

private const val DEGREES_IN_HALF_TURN = 180.0

/**
 * The current as a signed speed in metres a second, flood positive, from a speed in centimetres a
 * second and the direction it runs towards, with [levels] to tell the flood from the ebb.
 *
 * **A speed alone has no sign**, and the RWsOS viewer plots it so: a turning tide reads as a dip
 * to nought and back up, the same either way. A tidal stream runs to and fro along one line, so
 * each speed is projected onto that line and the projection carries the sign. The line is the mean
 * of the directions taken as axes, each angle doubled so that a heading and its opposite agree,
 * and weighted by speed so the wandering heading at slack counts for little. The flood is the end
 * of the line the water runs towards while it rises.
 *
 * A speed with no direction at its moment is left out. Where no level is given the sign follows
 * the line as computed, which is one way or the other and not known to be the flood, so the
 * calculator asks only a place with a level.
 */
internal fun signedFlows(speeds: List<Reading>, directions: List<Reading>, levels: List<Reading>): List<Flow> {
    val headings = directions.associate { it.at to it.height }
    val paired = speeds.mapNotNull { speed -> headings[speed.at]?.let { Triple(speed.at, speed.height, it) } }
    if (paired.isEmpty()) return emptyList()
    var across = 0.0
    var along = 0.0
    for ((_, speed, heading) in paired) {
        val doubled = 2 * heading * PI / DEGREES_IN_HALF_TURN
        across += speed * cos(doubled)
        along += speed * sin(doubled)
    }
    val axis = atan2(along, across) / 2
    val projected = paired.map { (at, speed, heading) ->
        Flow(at, speed * cos(heading * PI / DEGREES_IN_HALF_TURN - axis) / CENTIMETRES_IN_METRE)
    }
    // How far the water rose at each moment, from the readings either side of it.
    val rise = HashMap<Moment, Double>()
    for (index in levels.indices) {
        val before = levels[maxOf(index - 1, 0)]
        val after = levels[minOf(index + 1, levels.size - 1)]
        rise[levels[index].at] = after.height - before.height
    }
    val agreement = projected.sumOf { flow -> flow.speed * (rise[flow.at] ?: 0.0) }
    return if (agreement < 0) projected.map { it.copy(speed = -it.speed) } else projected
}

/**
 * The slacks in [flows]: each moment the current crosses nought on its way from running one way by
 * at least [least] to running the other way by as much.
 *
 * The crossing is placed on a straight line drawn between the readings either side of it. A
 * current that wavers about nought without running either way by [least] makes one slack, at the
 * last crossing before it sets in its new direction.
 */
fun slacksIn(flows: List<Flow>, least: Double = LEAST_FLOW): List<Slack> {
    val slacks = ArrayList<Slack>()
    var flooding: Boolean? = null
    var crossing: Moment? = null
    for (index in flows.indices) {
        val flow = flows[index]
        if (index > 0) {
            val before = flows[index - 1]
            if ((before.speed < 0) != (flow.speed < 0)) {
                val part = before.speed / (before.speed - flow.speed)
                crossing = before.at.plusSeconds((before.at.secondsUntil(flow.at) * part).roundToLong())
            }
        }
        val now = when {
            flow.speed >= least -> true
            flow.speed <= -least -> false
            else -> continue
        }
        val crossed = crossing
        if (flooding != null && now != flooding && crossed != null) slacks += Slack(crossed, toFlood = now)
        flooding = now
        crossing = null
    }
    return slacks
}
