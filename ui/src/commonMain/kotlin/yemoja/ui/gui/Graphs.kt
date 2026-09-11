package yemoja.ui.gui

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

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

/** Overlay is one thing the right axis can show: its title, its unit, and its line. */
internal class Overlay(val title: String, val unit: String, val line: Line)

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
    val sources = keyedOf(dive, "gas_sources")
    for ((key, series) in keyedSeriesOf(profile, "pressures")) {
        val tank = sources[key]?.let { entryLabelOf(key, it) } ?: key
        overlays += Overlay("$tank pressure", "bar", Line(tank, pointsOf(series)))
    }
    seriesOf(profile, "no_deco_time")?.let {
        overlays += Overlay("No-deco time", "min", Line("No-deco time", noDecoOf(it)))
    }
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

/** Minutes of no-deco time beyond which a plot says no more, which is where computers stop. */
internal const val NO_DECO_CAP = 99.0

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
