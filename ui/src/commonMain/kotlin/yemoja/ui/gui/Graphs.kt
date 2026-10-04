package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong

/*
 * A recording as a graph: what is drawn, worked out here and holding no screen in it.
 *
 * One graph per recording, under its tab: depth over time up the left, and up the right one
 * other thing the computer wrote, chosen from what it wrote — temperature, a cylinder's pressure,
 * no-deco time, CNS.
 *
 * See ../../../../../../gui/doc.md — `GUI-4`.
 */

/** Point is one sample placed on a graph: a minute along, and a value in the line's unit. */
internal class Point(val minute: Double, val value: Double)

/**
 * Line is one series on a graph: what to call it, its points, whether it is the one to read,
 * and whether it steps rather than slopes between points, as a deco stop does.
 */
internal class Line(
    val label: String,
    val points: List<Point>,
    val main: Boolean = true,
    val stepped: Boolean = false,
)

/**
 * Overlay is one thing the right axis can show: its title, its unit, and its line.
 *
 * Several lines rather than one, because a reading may stop existing part way through a dive
 * and start again, and a line drawn across that stretch is a reading nobody took. `GUI-4`.
 */
internal class Overlay(
    val title: String,
    val unit: String,
    val lines: List<Line>,
    /** The gas source a pressure belongs to, named as its switches are, or null for any other reading. */
    val gas: String? = null,
) {

    constructor(title: String, unit: String, line: Line, gas: String? = null) : this(title, unit, listOf(line), gas)
}

/** What a mark on the depth line is: a gas switched to, or an alarm the computer gave. */
internal enum class Marking { SWITCH, ALARM }

/** Event is one moment on the depth line worth a word: when, what, and which of the two. */
internal class Event(val minute: Double, val label: String, val marking: Marking)

/**
 * The events on [profile]'s depth line: every gas switch, named as the source switched to is,
 * and every alarm, named as the computer gave it, in the order they came.
 */
internal fun eventsOf(dive: Item, profile: Item): List<Event> {
    val marks = ArrayList<Event>()
    // The run's own cylinders, or the dive's where it keeps none. `RECON-7`.
    val sources = keyedOf(profile.rootOf("gas_sources") ?: dive, "gas_sources")
    seriesOf(profile, "gas_switches")?.let { switches ->
        for (at in 0..<switches.size) {
            val key = ((switches.valueAt(at) as? Element.Usable)?.value as? KeyReference)?.key
                ?: continue
            val named = sources[key]?.let { entryLabelOf(key, it) } ?: prettyOf(key)
            marks += Event(switches.secondAt(at) / 60.0, named, Marking.SWITCH)
        }
    }
    seriesOf(profile, "alarms")?.let { alarms ->
        for (at in 0..<alarms.size) {
            val said = (alarms.valueAt(at) as? Element.Usable)?.value?.toString() ?: continue
            marks += Event(alarms.secondAt(at) / 60.0, prettyOf(said), Marking.ALARM)
        }
    }
    return marks.sortedBy { it.minute }
}

/**
 * The depth at [minute] along [line], between the samples either side of it; the nearest end
 * beyond the first or the last, and nothing on a line with no points.
 */
internal fun depthAt(line: Line, minute: Double): Double? {
    val points = line.points
    if (points.isEmpty()) return null
    if (minute <= points.first().minute) return points.first().value
    if (minute >= points.last().minute) return points.last().value
    val after = points.indexOfFirst { it.minute >= minute }
    val a = points[after - 1]
    val b = points[after]
    if (b.minute == a.minute) return b.value
    return a.value + (b.value - a.value) * (minute - a.minute) / (b.minute - a.minute)
}

/**
 * The value [line] reads at [minute], or null outside the minutes it covers.
 *
 * A stepped line holds each value until its next point, and any other line slopes between the
 * points either side. Unlike [depthAt], nothing is read beyond either end, because a reading
 * that has stopped did not go on at its last value.
 */
internal fun valueAt(line: Line, minute: Double): Double? {
    val points = line.points
    if (points.isEmpty() || minute < points.first().minute || minute > points.last().minute) return null
    if (line.stepped) return points.last { it.minute <= minute }.value
    return depthAt(line, minute)
}

/**
 * GraphReading is one line of the box a click on a graph opens: what it is, what it read, and
 * whether it is the pressure of the gas being breathed.
 */
internal class GraphReading(val label: String, val said: String, val underlined: Boolean = false)

/**
 * Everything a graph holds at [minute], for the box a click opens: the time, every depth line, and
 * every overlay, the ones not on the right axis included. `GUI-4`.
 *
 * The gas breathed has no line of its own. Its pressure is underlined instead, being the cylinder
 * a reader is watching.
 *
 * A line beside the depth that reads nought is left out, since a ceiling or a deco stop of nought
 * is no stop. An overlay with no reading at that minute is left out too.
 */
internal fun readingsAt(depth: List<Line>, overlays: List<Overlay>, events: List<Event>, minute: Double): List<GraphReading> {
    val readings = ArrayList<GraphReading>()
    val second = (minute * 60).roundToLong()
    readings += GraphReading("Time", "${second / 60}:${(second % 60).toString().padStart(2, '0')}")
    for (line in depth) {
        val value = valueAt(line, minute) ?: continue
        if (!line.main && value <= 0.0) continue
        readings += GraphReading(line.label, "${readingOf(value)} m")
    }
    val breathed = events.lastOrNull { it.marking == Marking.SWITCH && it.minute <= minute }?.label
    for (overlay in overlays) {
        val value = overlay.lines.firstNotNullOfOrNull { valueAt(it, minute) } ?: continue
        val unit = if (overlay.unit.isEmpty()) "" else " ${overlay.unit}"
        readings += GraphReading(overlay.title, readingOf(value) + unit, breathed != null && overlay.gas == breathed)
    }
    return readings
}

/** A reading to a tenth, its nought dropped where it is whole. */
private fun readingOf(value: Double): String {
    val tenths = (value * 10).roundToLong()
    val whole = tenths / 10
    val tenth = kotlin.math.abs(tenths % 10)
    return if (tenth == 0L) "$whole" else "${if (tenths < 0 && whole == 0L) "-" else ""}$whole.$tenth"
}

/**
 * The depth side of a recording's graph: the depth itself, and the deco stops stepped across
 * it where there are any. Empty where the recording holds no depth.
 */
internal fun depthLinesOf(profile: Item): List<Line> {
    val depth = seriesOf(profile, "depth") ?: return emptyList()
    val lines = ArrayList<Line>()
    lines += Line("Depth", pointsOf(depth))
    seriesOf(profile, "decostop")?.let {
        lines += Line("Deco stop", pointsOf(it), main = false, stepped = true)
    }
    return lines
}

/**
 * What the right axis of [profile]'s graph can show, in the order offered: the temperature, a
 * pressure per cylinder, the no-deco time, the CNS and the OTU, whichever the recording holds.
 *
 * A cylinder is named as its gas source on [dive] is: by the cylinder it was, where the source
 * names one, and by its key otherwise.
 */
internal fun overlaysOf(dive: Item, profile: Item): List<Overlay> {
    val overlays = ArrayList<Overlay>()
    seriesOf(profile, "temperature")?.let {
        overlays += Overlay("Temperature", "°C", Line("Temperature", pointsOf(it)))
    }
    // The run's own cylinders, or the dive's where it keeps none. `RECON-7`.
    val sources = keyedOf(profile.rootOf("gas_sources") ?: dive, "gas_sources")
    for ((key, series) in keyedSeriesOf(profile, "pressures")) {
        val source = sources[key]?.let { entryLabelOf(key, it) }
        val tank = source ?: key
        overlays += Overlay("$tank pressure", "bar", Line(tank, pointsOf(series)), gas = source ?: prettyOf(key))
    }
    seriesOf(profile, "no_deco_time")?.let {
        val stops = seriesOf(profile, "decostop")?.let { series -> pointsOf(series) }.orEmpty()
        val stretches = stretchesOf(noDecoOf(it), stops)
        // NDL as a diver writes it, beside the CNS and the OTU, which are written the same way.
        overlays += Overlay("NDL", "min", stretches.map { stretch -> Line("NDL", stretch) })
    }
    // Worked out rather than recorded, from the pressure of whichever cylinder was breathed.
    seriesOf(profile, "sac")?.let { overlays += Overlay("SAC", "l/min", Line("SAC", pointsOf(it))) }
    seriesOf(profile, "cns")?.let { overlays += Overlay("CNS", "%", Line("CNS", pointsOf(it))) }
    seriesOf(profile, "otu")?.let { overlays += Overlay("OTU", "", Line("OTU", pointsOf(it))) }
    return overlays
}

/**
 * A no-deco series as a plot reads it: in minutes, capped, and without the zeros a computer
 * reads before it has calculated anything.
 *
 * A computer marks *no limit* with a number: one writes 99 minutes, another 598. Plotted as
 * written, the marker is the whole axis and the real values lie squashed under it, so anything
 * above the cap reads as the cap, which is what the diver's screen showed. The zeros before the
 * first positive value are the same case as for `deco`: not yet calculated, not zero.
 */
internal fun noDecoOf(series: Series): List<Point> =
    pointsOf(series, 1.0 / 60.0).dropWhile { it.value <= 0.0 }
        .map { Point(it.minute, minOf(it.value, NO_DECO_CAP)) }

/**
 * The stretches of [points] in which the reading existed, cut wherever a stop stood.
 *
 * A computer shows either how much longer a diver may stay or how deep they may not come
 * above, never both: the two are one reading seen two ways, and a computer holding a diver to
 * a stop writes no no-deco time at all. Read straight across, that gap becomes a line climbing
 * from nothing back to the limit, which is a reading nobody took. `GUI-4`.
 *
 * A stop of nought is no stop: some computers write one before they have calculated anything.
 */
internal fun stretchesOf(points: List<Point>, stops: List<Point>): List<List<Point>> {
    if (points.isEmpty()) return emptyList()
    val standing = stops.filter { it.value > 0.0 }.map { it.minute }
    val stretches = ArrayList<List<Point>>()
    var run = ArrayList<Point>()
    for (point in points) {
        val before = run.lastOrNull()
        if (before != null && standing.any { it > before.minute && it < point.minute }) {
            stretches += run
            run = ArrayList()
        }
        run += point
    }
    stretches += run
    return stretches
}

/** Minutes of no-deco time beyond which a plot says no more, which is where computers stop. */
internal const val NO_DECO_CAP = 99.0

/**
 * The room an axis leaves beyond the values it covers, as a fraction of what they span.
 *
 * A reading that hardly changes would otherwise be drawn along the plot's edge, where it reads
 * as a border rather than as a line: a dive in water of six degrees throughout draws its
 * temperature as a second axis under the graph.
 */
internal const val AXIS_ROOM = 0.05

/**
 * The range an axis covers for [values]: the least to the greatest with [AXIS_ROOM] at each
 * end. A reading that never changed is given a range around it, there being nothing to scale
 * by, and no reading at all the range nought to one.
 */
internal fun rangeOf(values: List<Double>): ClosedFloatingPointRange<Double> {
    if (values.isEmpty()) return 0.0..1.0
    val low = values.min()
    val high = values.max()
    if (high <= low) return (low - 1.0)..(high + 1.0)
    val room = (high - low) * AXIS_ROOM
    return (low - room)..(high + room)
}

/**
 * Reach is how far a set of graphs reaches, so that graphs of one dive drawn one after another read
 * against one scale: the longest of them, the deepest, and the lowest and highest of each reading.
 *
 * Immutable.
 */
internal class Reach(
    val minutes: Double,
    val deepest: Double,
    /** Each reading's lowest and highest value, under the title its overlay is given. */
    val readings: Map<String, List<Double>>,
)

/**
 * How far the recordings [profiles] of [dive] reach between them.
 *
 * Only what they recorded: what the model works out is worked out for the one on show, and a
 * reading only one profile has is scaled as that profile alone would scale it.
 */
internal fun reachOf(dive: Item, profiles: List<Item>): Reach {
    var minutes = 0.0
    var deepest = 0.0
    val readings = LinkedHashMap<String, MutableList<Double>>()
    for (profile in profiles) {
        for (line in depthLinesOf(profile)) {
            for (point in line.points) {
                minutes = maxOf(minutes, point.minute)
                if (line.main) deepest = maxOf(deepest, point.value)
            }
        }
        for (overlay in overlaysOf(dive, profile)) {
            val values = overlay.lines.flatMap { it.points }
            if (values.isEmpty()) continue
            minutes = maxOf(minutes, values.maxOf { it.minute })
            val held = readings.getOrPut(overlay.title) { mutableListOf() }
            held += listOf(values.minOf { it.value }, values.maxOf { it.value })
        }
    }
    return Reach(minutes, deepest, readings.mapValues { (_, it) -> listOf(it.min(), it.max()) })
}

/**
 * Where to put the marks along an axis from [low] to [high], about [wanted] of them, at values
 * a reader would choose: steps of one, two or five times a power of ten.
 */
internal fun ticksOf(low: Double, high: Double, wanted: Int): List<Double> {
    if (high <= low) return listOf(low)
    val raw = (high - low) / wanted
    val magnitude = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 5.0, 10.0).map { it * magnitude }.first { it >= raw }
    val ticks = ArrayList<Double>()
    var tick = ceil(low / step) * step
    while (tick <= high + step / 1000) {
        ticks += tick
        tick += step
    }
    return ticks
}

// Each reader asks the description first: `read` refuses a field the type does not have, and
// what is asked for a graph is asked of any keyed entry, a gas source among them.

@Suppress("UNCHECKED_CAST")
private fun keyedOf(item: Item, field: String): Map<String, OwnedItem> {
    if (item.description[field] == null) return emptyMap()
    val read = (item.read(field) as? Result.Usable)?.value as? Map<String, Element<Any>>
    return read.orEmpty().mapNotNull { (key, element) ->
        ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
    }.toMap()
}

private fun seriesOf(profile: Item, field: String): Series? {
    if (profile.description[field] == null) return null
    return (profile.read(field) as? Result.Usable)?.value as? Series
}

@Suppress("UNCHECKED_CAST")
private fun keyedSeriesOf(profile: Item, field: String): Map<String, Series> {
    if (profile.description[field] == null) return emptyMap()
    val read = (profile.read(field) as? Result.Usable)?.value as? Map<String, Element<Any>>
    return read.orEmpty().mapNotNull { (key, element) ->
        ((element as? Element.Usable)?.value as? Series)?.let { key to it }
    }.toMap()
}

/**
 * A series as points, its seconds as minutes and each value scaled; a sample that would not
 * read is left out.
 */
private fun pointsOf(series: Series, scale: Double = 1.0): List<Point> =
    (0..<series.size).mapNotNull { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value as? Number
            ?: return@mapNotNull null
        Point(series.secondAt(at) / 60.0, value.toDouble() * scale)
    }
