package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series

/*
 * How much gas was breathed, brought back to the surface: a recording's SAC through time, and a
 * gas source's over the time it was breathed.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md, `LOGIC-33`.
 */

/**
 * Stretch is part of a recording breathed from one gas source, between two readings of its
 * pressure: when it began and ended, the litres used at the surface's pressure, and the ambient
 * pressure it was breathed at on average, in bar.
 *
 * Immutable.
 */
internal class Stretch(
    val key: String,
    val from: Int,
    val to: Int,
    val litres: Double,
    val ambient: Double,
) {

    /** Litres a minute at the surface, over this stretch alone. */
    val sac: Double get() = litres / ((to - from) / SECONDS_IN_MINUTE) / ambient
}

/**
 * Every stretch of [profile] a SAC can be worked out over, earliest first.
 *
 * A stretch runs between two pressure readings of one source, and counts only where that source
 * was being breathed the whole way: a switch inside it would put another cylinder's breathing
 * into this one's figure. What was breathed is what the switches say, and on a recording with no
 * switches it is the dive's only source where it has exactly one. A source with no volume has no
 * litres to give, and is passed over.
 *
 * **Ideal gas, and no more.** The drop in bar times the cylinder's volume is the gas used at the
 * surface's pressure; a real gas at 200 bar packs a few percent more than that, which no logbook
 * figure a diver compares against corrects for either.
 */
internal fun stretchesOf(profile: Item): List<Stretch> {
    val sources = profile.rootOf("gas_sources")?.let { entriesOf(it, "gas_sources") }
        ?: return emptyList()
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series ?: return emptyList()
    val switches = (profile.read("gas_switches") as? Result.Usable)?.value as? Series
    val held = (profile.keyedSeries<Double>("pressures") as? Result.Usable)?.value.orEmpty()
    val breathing = breathingOf(switches, sources.keys)
    val water = (profile.single<Double>("density") as? Result.Usable)?.value ?: NOMINAL_DENSITY
    val surface = (profile.single<Double>("atmospheric_pressure") as? Result.Usable)?.value
        ?: SEA_LEVEL
    val out = ArrayList<Stretch>()
    for ((key, entry) in held) {
        val pressures = (entry as? Element.Usable)?.value ?: continue
        val volume = sources[key]?.let { (it.single<Double>("volume") as? Result.Usable)?.value }
            ?: continue
        for (at in 1..<pressures.size) {
            val from = pressures.secondAt(at - 1)
            val to = pressures.secondAt(at)
            val before = (pressures.valueAt(at - 1) as? Element.Usable)?.value as? Double
                ?: continue
            val after = (pressures.valueAt(at) as? Element.Usable)?.value as? Double
                ?: continue
            if (to <= from || !breathing(key, from, to)) continue
            val metres = meanDepth(depth, from, to) ?: continue
            val ambient = surface + water * GRAVITY * metres / PASCALS_IN_BAR
            out += Stretch(key, from, to, (before - after) * volume, ambient)
        }
    }
    return out.sortedBy { it.from }
}

/**
 * A recording's SAC through time, a point at the start of each stretch, or absent where no
 * stretch can be worked out. `LOGIC-33`.
 */
internal fun profilesSac(profile: Item): Result<Any> {
    val stretches = stretchesOf(profile).distinctBy { it.from }
    if (stretches.isEmpty()) return Result.Absent
    val series = Series(
        stretches.map { it.from }.toIntArray(),
        stretches.map { Element.Usable(it.sac as Any) },
    )
    return Result.Usable(series, Result.Origin.DERIVED)
}

/**
 * A gas source's SAC over the time it was breathed, from the dive's primary recording.
 *
 * The gas it gave over every stretch it was breathed, divided by those stretches' minutes each
 * weighted by the ambient pressure they were breathed at. That is the recording's SAC averaged
 * the way a figure over time is, rather than a mean of its points, which would count a short
 * stretch as much as a long one. Absent where the recording gives none, for the user to write.
 * `LOGIC-33`.
 */
internal fun sourcesSac(source: Item): Result<Any> {
    val owner = (source as? OwnedItem)?.parent ?: return Result.Absent
    val key = entriesOf(owner, "gas_sources").entries.firstOrNull { it.value === source }?.key
        ?: return Result.Absent
    // A source a profile keeps is that profile's, and a dive's is read from the recording it is
    // worked from. A plan's own has no pressures behind it, so nothing is worked out and what the
    // user wrote stands.
    val profile = if (owner.description == Types.DIVE) {
        (primaryProfile(owner) as? Result.Usable)?.value ?: return Result.Absent
    } else {
        owner
    }
    val mine = stretchesOf(profile).filter { it.key == key }
    if (mine.isEmpty()) return Result.Absent
    val litres = mine.sumOf { it.litres }
    val weighted = mine.sumOf { (it.to - it.from) / SECONDS_IN_MINUTE * it.ambient }
    return Result.Usable(litres / weighted, Result.Origin.DERIVED)
}

/**
 * Whether a source was breathed from one second to another, by the switches of a recording.
 *
 * The source breathed at a moment is the one the last switch at or before it names. With no
 * switches at all, the only source is breathed throughout where there is exactly one. Before the
 * first switch, nothing says what was breathed, and nothing is worked out.
 */
private fun breathingOf(switches: Series?, keys: Set<String>): (String, Int, Int) -> Boolean {
    if (switches == null || switches.size == 0) {
        val only = keys.singleOrNull()
        return { key, _, _ -> key == only }
    }
    val times = (0..<switches.size).map { switches.secondAt(it) }
    val named = (0..<switches.size).map {
        ((switches.valueAt(it) as? Element.Usable)?.value as? KeyReference)?.key
    }
    return { key, from, to ->
        val last = times.indexOfLast { it <= from }
        last >= 0 && named[last] == key && times.none { it in (from + 1)..<to }
    }
}

/**
 * The average depth between two seconds, read along the line between samples, or absent where the
 * depth series says nothing about that stretch.
 */
private fun meanDepth(depth: Series, from: Int, to: Int): Double? {
    val points = (0..<depth.size).mapNotNull { at ->
        ((depth.valueAt(at) as? Element.Usable)?.value as? Double)?.let { depth.secondAt(at) to it }
    }
    if (points.isEmpty()) return null
    fun at(second: Int): Double {
        if (second <= points.first().first) return points.first().second
        if (second >= points.last().first) return points.last().second
        val after = points.indexOfFirst { it.first >= second }
        val (t0, d0) = points[after - 1]
        val (t1, d1) = points[after]
        return d0 + (d1 - d0) * (second - t0) / (t1 - t0)
    }
    val inside = points.map { it.first }.filter { it in (from + 1)..<to }
    val corners = listOf(from) + inside + listOf(to)
    var area = 0.0
    for (index in 1..<corners.size) {
        val a = corners[index - 1]
        val b = corners[index]
        area += (at(a) + at(b)) / 2 * (b - a)
    }
    return area / (to - from)
}

/** The keyed owned items [name] of [item] that could be read, by key. */
private fun entriesOf(item: Item, name: String): Map<String, OwnedItem> =
    ((item.keyed<OwnedItem>(name) as? Result.Usable)?.value.orEmpty())
        .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }
        .toMap()

private const val SECONDS_IN_MINUTE = 60.0

/** Standard gravity, which is what turns a column of water into a pressure. */
private const val GRAVITY = 9.80665

private const val PASCALS_IN_BAR = 100_000.0

/** The atmosphere at sea level in bar, where the dive says nothing about its own. */
private const val SEA_LEVEL = 1.01325

/**
 * What water is taken to weigh where the recording gives no density: the EN 13319 figure, which
 * is the one most computers turn pressure into depth with.
 */
private const val NOMINAL_DENSITY = 1020.0
