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

/*
 * A dive's recordings as graphs: what is drawn, worked out here and holding no screen in it.
 *
 * Depth over time is the graph, the primary recording drawn to be read and any other recording
 * of the same dive laid over it thinly; the rest of what a computer wrote, temperature, cylinder
 * pressures, no-deco time and CNS, is a small graph each under it, on the same axis of minutes.
 *
 * See ../../../../../../gui/doc.md — `GUI-4`.
 */

/** Point is one sample placed on a graph: a minute along, and a value in the graph's unit. */
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
 * Graph is one chart of a recording: a title, the unit up its side, the lines on it, and whether
 * its axis runs downward, as depth's does.
 */
internal class Graph(
    val title: String,
    val unit: String,
    val lines: List<Line>,
    val down: Boolean = false,
)

/**
 * The graphs of [dive]: depth first, and then whatever else its primary recording holds.
 *
 * The primary recording is the one the dive names, or the first where it names none. Empty
 * where the dive has no recording, or none with a depth series.
 */
internal fun graphsOf(dive: Item): List<Graph> {
    val profiles = profilesOf(dive)
    if (profiles.isEmpty()) return emptyList()
    val primaryKey = primaryKeyOf(dive)?.takeIf { it in profiles } ?: profiles.keys.first()
    val primary = profiles.getValue(primaryKey)
    val depth = seriesOf(primary, "depth") ?: return emptyList()
    val graphs = ArrayList<Graph>()

    val depthLines = ArrayList<Line>()
    depthLines += Line("Depth", pointsOf(depth))
    seriesOf(primary, "decostop")?.let {
        depthLines += Line("Deco stop", pointsOf(it), main = false, stepped = true)
    }
    for ((key, other) in profiles) {
        if (key == primaryKey) continue
        seriesOf(other, "depth")?.let { depthLines += Line(key, pointsOf(it), main = false) }
    }
    graphs += Graph("Depth", "m", depthLines, down = true)

    seriesOf(primary, "temperature")?.let {
        graphs += Graph("Temperature", "°C", listOf(Line("Temperature", pointsOf(it))))
    }
    val pressures = keyedSeriesOf(primary, "pressures")
    if (pressures.isNotEmpty()) {
        val lines = pressures.map { (key, series) -> Line(key, pointsOf(series)) }
        graphs += Graph("Pressure", "bar", lines)
    }
    seriesOf(primary, "no_deco_time")?.let {
        val line = Line("No-deco time", pointsOf(it, scale = 1.0 / 60.0))
        graphs += Graph("No-deco time", "min", listOf(line))
    }
    seriesOf(primary, "cns")?.let { graphs += Graph("CNS", "%", listOf(Line("CNS", pointsOf(it)))) }
    return graphs
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

@Suppress("UNCHECKED_CAST")
private fun profilesOf(dive: Item): Map<String, OwnedItem> {
    val read = (dive.read("profiles") as? Result.Usable)?.value as? Map<String, Element<Any>>
    return read.orEmpty().mapNotNull { (key, element) ->
        ((element as? Element.Usable)?.value as? OwnedItem)?.let { key to it }
    }.toMap()
}

private fun primaryKeyOf(dive: Item): String? =
    ((dive.read("primary_profile") as? Result.Usable)?.value as? KeyReference)?.key

private fun seriesOf(profile: Item, field: String): Series? =
    (profile.read(field) as? Result.Usable)?.value as? Series

@Suppress("UNCHECKED_CAST")
private fun keyedSeriesOf(profile: Item, field: String): Map<String, Series> {
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
