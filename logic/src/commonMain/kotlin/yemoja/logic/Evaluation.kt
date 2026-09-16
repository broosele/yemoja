package yemoja.logic

import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series

/*
 * What the decompression model says about one profile: the ceiling it was held to, how much
 * longer it could have stayed, and what it leaves in the tissues.
 *
 * A profile is given and the answer is worked out from it, so a plan and a recording are asked the
 * same question and their answers sit side by side. Nothing here is stored: `manual/decompression.md`
 * promises a user that what the model says is an opinion about a recording rather than part of it,
 * and a later version that calculates differently is then free to say something else.
 *
 * `LOGIC-37`. See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/**
 * Evaluated is what the model made of a profile, or why it could not make anything of it.
 *
 * Refused rather than empty, because the reasons are actionable: a recording with no `water_type`
 * and a plan with no gradient factors are both missing something a user can write.
 */
sealed class Evaluated {

    /**
     * Done is the model's answer, sampled at the moments the profile itself holds.
     *
     * Immutable.
     */
    class Done(
        /** The shallowest depth allowed at each moment, in metres, and nought where any is. */
        val ceiling: Series,
        /**
         * How much longer the profile could have stayed at each moment, in seconds.
         *
         * The stretches where a stop was already required are left out rather than filled with
         * nought, which is how a computer writes its own: once it is holding you to a stop there
         * is no such time.
         */
        val noDecompressionTime: Series,
        /** What the compartments hold at the end, which is what the next dive starts from. */
        val surfacing: Tissues,
        /**
         * The gas each source gives up, in litres at the surface, under the key it sits under.
         *
         * A source whose consumption nobody knows is left out rather than counted as nothing.
         */
        val gasUsed: Map<String, Double>,
        /**
         * What each source's gauge would read through the run, under the key it sits under.
         *
         * Worked out from what it was filled to and what is breathed from it, so a source with no
         * `start_pressure` or no `volume` has none of this. It runs below nought where the run
         * asks for more gas than the cylinder holds, which is what the finding beside it says.
         */
        val pressures: Map<String, Series>,
        /** What the model has to say against the profile, earliest first. */
        val findings: List<Finding>,
    ) : Evaluated()

    /** Refused is nothing worked out, and why. */
    data class Refused(val reason: String) : Evaluated()
}

/**
 * Finding is something worth saying about a profile: when it happened, how serious it is, and what
 * it is.
 *
 * Aimed at a moment rather than at a field, because what is wrong is what was done at that moment
 * and not what anybody wrote. Never a refusal: a profile that breaks its own ceiling is evaluated
 * to the end and carries the finding.
 *
 * Immutable.
 */
class Finding(val second: Int, val severity: Severity, val said: String) {

    override fun toString(): String = "${second}s $severity: $said"
}

/** How much a [Finding] matters. */
enum class Severity {

    /** Worth knowing. */
    NOTE,

    /** The model says this should not have been done. */
    WARNING,
}

/**
 * What the model makes of [profile], which may be a recording or a plan.
 *
 * The model settings are the profile's own and are never taken from a preference: changing what a
 * new plan starts with must not change what is said about a dive already made. A profile carrying
 * none is refused, which is the honest answer for a dive off a depth gauge.
 *
 * Where the profile names the run before it, that run is evaluated first and its tissues carried
 * across the surface interval. A chain that comes back on itself is refused rather than followed.
 */
fun evaluate(profile: Item): Evaluated = evaluated(profile, emptySet())

private fun evaluated(profile: Item, seen: Set<Item>): Evaluated {
    if (profile in seen) return Evaluated.Refused("this run carries gas from itself")
    val model = modelOf(profile) ?: return Evaluated.Refused(
        "nothing says what model this run was worked out with, or how conservative it was"
    )
    if (model.name != BUHLMANN) {
        return Evaluated.Refused("${model.name} is not the model built here, which is $BUHLMANN")
    }
    val density = (profile.single<Double>("density") as? Result.Usable)?.value
        ?: return Evaluated.Refused(
            "nothing says what water this run was in, so its depths are not pressures"
        )
    val surface = (profile.single<Double>("atmospheric_pressure") as? Result.Usable)?.value
        ?: SEA_LEVEL
    val depths = pointsOf(profile) ?: return Evaluated.Refused("this run holds no depths")
    val breathed = breathedBy(profile) ?: return Evaluated.Refused(
        "nothing says what was breathed on this run"
    )
    val carried = carriedInto(profile, surface, seen)
    if (carried is Carried.Refused) return Evaluated.Refused(carried.reason)

    return walked(
        depths,
        breathed,
        (carried as Carried.Tissues).tissues,
        model,
        density,
        surface,
    )
}

/** The walk itself, once everything it needs has been found. */
private fun walked(
    depths: List<Point>,
    breathing: Breathing,
    carried: Tissues,
    model: Model,
    density: Double,
    surface: Double,
): Evaluated.Done {
    var tissues = carried
    var firstStop = 0.0
    val seconds = ArrayList<Int>()
    val ceilings = ArrayList<Double>()
    val limits = ArrayList<Pair<Int, Double>>()
    val findings = ArrayList<Finding>()
    val used = HashMap<String, Double>()
    val gauges = breathing.fills.mapValues { ArrayList<Double>() }
    var above = false
    var rich = false
    val dry = HashSet<String>()

    for ((index, point) in depths.withIndex()) {
        val ambient = ambientAt(point.metres, density, surface)
        if (index > 0) {
            val before = depths[index - 1]
            val was = ambientAt(before.metres, density, surface)
            val key = breathing.keyAt(before.second)
            val minutes = (point.second - before.second) / SECONDS_IN_MINUTE
            tissues = tissues.breathing(
                breathing.mixAt(before.second),
                was,
                ambient,
                (point.second - before.second).toDouble(),
            )
            breathing.rates[key]?.let { rate ->
                used[key] = (used[key] ?: 0.0) + rate * minutes * (was + ambient) / 2
            }
        }
        val held = tissues.ceiling(model.low)
        if (held > surface && held > firstStop) firstStop = held
        val factor = gradientFactorAt(ambient, firstStop, surface, model.low, model.high)
        val ceiling = tissues.ceiling(factor)
        seconds += point.second
        ceilings += depthAt(ceiling, density, surface)

        if (ceiling <= surface) {
            tissues.noDecompressionSeconds(breathing.mixAt(point.second), ambient, surface, factor)
                ?.let { limits += point.second to it }
        }
        for ((key, fill) in breathing.fills) {
            val left = fill.gauge - (used[key] ?: 0.0) / fill.volume
            gauges.getValue(key) += left
            if (left <= 0 && dry.add(key)) {
                findings += Finding(point.second, Severity.WARNING, "$key is empty by here")
            }
        }
        // One finding a crossing, not one a sample: a diver who stays above the ceiling for ten
        // minutes has made one mistake, and ten lines of it would bury the rest.
        if (ambient < ceiling && !above) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "above the ceiling: ${metres(depthAt(ceiling, density, surface))} was allowed" +
                    " and ${metres(point.metres)} was taken",
            )
        }
        above = ambient < ceiling
        val oxygen = breathing.mixAt(point.second).fractionO2 * ambient
        if (oxygen > MOST_OXYGEN && !rich) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "the oxygen in ${breathing.mixAt(point.second)} is at ${bar(oxygen)} here," +
                    " over the ${bar(MOST_OXYGEN)} a diver plans to",
            )
        }
        rich = oxygen > MOST_OXYGEN
    }

    return Evaluated.Done(
        seriesOf(seconds, ceilings),
        seriesOf(limits.map { it.first }, limits.map { it.second }),
        tissues,
        used,
        gauges.mapValues { (_, left) -> seriesOf(seconds, left) },
        findings.sortedBy { it.second },
    )
}

/** Model is what a profile says it was worked out with: the name, and the two factors. */
private class Model(val name: String, val low: Double, val high: Double)

/** Point is one depth in a profile: when, and how deep. */
private class Point(val second: Int, val metres: Double)

/** Carried is what the tissues hold when a run begins, or why that cannot be worked out. */
private sealed class Carried {
    class Tissues(val tissues: yemoja.logic.Tissues) : Carried()
    class Refused(val reason: String) : Carried()
}

/**
 * What [profile] says it was worked out with, or null where it says too little.
 *
 * Both factors are needed. One of them is a setting half written down, and guessing the other
 * would put a number into a decompression answer that nobody chose.
 */
private fun modelOf(profile: Item): Model? {
    val low = (profile.single<Double>("gradient_factor_low") as? Result.Usable)?.value ?: return null
    val high = (profile.single<Double>("gradient_factor_high") as? Result.Usable)?.value
        ?: return null
    val named = (profile.single<String>("deco_model") as? Result.Usable)?.value ?: BUHLMANN
    return Model(named, low, high)
}

/** Every depth of [profile] that could be read, earliest first, or null where there are none. */
private fun pointsOf(profile: Item): List<Point>? {
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series ?: return null
    val points = (0..<depth.size).mapNotNull { at ->
        ((depth.valueAt(at) as? Element.Usable)?.value as? Double)
            ?.let { Point(depth.secondAt(at), it) }
    }
    return points.ifEmpty { null }
}

/**
 * Breathing is what a run takes its gas from: which source at each moment, what is in each, how
 * fast it is breathed, and what it was filled to.
 *
 * Immutable.
 */
private class Breathing(
    private val mixes: Map<String, Gas>,
    private val switches: List<Pair<Int, String>>,
    /** Litres a minute at the surface, for the sources that say. */
    val rates: Map<String, Double>,
    /** What each source was filled to and how big it is, for the sources that say both. */
    val fills: Map<String, Fill>,
) {

    /** The source breathed at [second]: the one the last switch at or before it names. */
    fun keyAt(second: Int): String =
        switches.lastOrNull { it.first <= second }?.second ?: switches.first().second

    /** What is in the source breathed at [second], and air where the source does not say. */
    fun mixAt(second: Int): Gas = mixes[keyAt(second)] ?: Gas.AIR
}

/** Fill is what a cylinder was filled to, in bar of gauge pressure, and the litres it holds. */
private class Fill(val gauge: Double, val volume: Double)

/**
 * What [profile] breathes from, or null where nothing says.
 *
 * The switches say which source, and a run with no switches breathes its only one, which is how an
 * ordinary single-cylinder dive is written. A source with no `gas_type` is air, that being what a
 * cylinder nobody said anything about holds.
 *
 * A source's `sac` is what it is breathed at: written on a plan, and worked out from the pressures
 * of a recording. One that says nothing costs nothing, since a figure nobody gave is not a figure
 * of nought.
 */
private fun breathedBy(profile: Item): Breathing? {
    val root = profile.rootOf("gas_sources") ?: return null
    val sources = ((root.keyed<OwnedItem>("gas_sources") as? Result.Usable)?.value.orEmpty())
        .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }
        .toMap()
    if (sources.isEmpty()) return null
    val mixes = sources.mapValues { (_, source) ->
        (source.single<Gas>("gas_type") as? Result.Usable)?.value ?: Gas.AIR
    }
    val rates = sources.mapNotNull { (key, source) ->
        ((source.read("sac") as? Result.Usable)?.value as? Double)?.let { key to it }
    }.toMap()
    val fills = sources.mapNotNull { (key, source) ->
        val gauge = (source.single<Double>("start_pressure") as? Result.Usable)?.value
        val volume = (source.read("volume") as? Result.Usable)?.value as? Double
        if (gauge == null || volume == null || volume <= 0) null else key to Fill(gauge, volume)
    }.toMap()

    val switches = (profile.read("gas_switches") as? Result.Usable)?.value as? Series
    val written = (0..<(switches?.size ?: 0)).mapNotNull { at ->
        ((switches!!.valueAt(at) as? Element.Usable)?.value as? KeyReference)
            ?.takeIf { it.key in sources }
            ?.let { switches.secondAt(at) to it.key }
    }
    if (written.isEmpty()) {
        val only = sources.keys.singleOrNull() ?: return null
        return Breathing(mixes, listOf(0 to only), rates, fills)
    }
    return Breathing(mixes, written, rates, fills)
}

/**
 * What the tissues hold when [profile] begins: the run before it, carried across the surface
 * interval, or air breathed long enough to settle.
 *
 * The interval is the dive's own, which it works out from the dive before it. Where a run before
 * is named and the interval is not known, nothing is worked out rather than the two being run
 * together as though the user never surfaced.
 */
private fun carriedInto(profile: Item, surface: Double, seen: Set<Item>): Carried {
    val before = profile.read("previous_profile")
    if (before is Result.Unusable) return Carried.Refused("the run before this one: ${before.reason}")
    val named = (before as? Result.Usable)?.value as? KeyReference
        ?: return Carried.Tissues(Tissues.saturated(surface))
    val id = named.id ?: return Carried.Refused("the run before this one names no dive")
    val dive = profile.set[id] ?: return Carried.Refused("$id is not in this logbook")
    val earlier = ((dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value.orEmpty())
        .let { (it[named.key] as? Element.Usable)?.value }
        ?: return Carried.Refused("$id has no run called ${named.key}")
    val interval = (owner(profile)?.single<Double>("surface_interval") as? Result.Usable)?.value
        ?: return Carried.Refused("nothing says how long the surface interval before this was")

    return when (val ran = evaluated(earlier, seen + profile)) {
        is Evaluated.Refused -> Carried.Refused("the run before this one: ${ran.reason}")
        is Evaluated.Done ->
            Carried.Tissues(ran.surfacing.breathing(Gas.AIR, surface, surface, interval))
    }
}

/** The dive a profile belongs to, or null where it belongs to nothing. */
private fun owner(profile: Item): Item? = (profile as? OwnedItem)?.parent

/** A series from times and values that were worked out together, and so are the same length. */
private fun seriesOf(seconds: List<Int>, values: List<Double>): Series =
    Series(seconds.toIntArray(), values.map { Element.Usable(it as Any) })

/** A depth as a finding says it, to a tenth of a metre, which is as fine as anyone reads one. */
private fun metres(depth: Double): String {
    val tenths = (depth * 10).toLong()
    return "${tenths / 10}.${tenths % 10} m"
}

/** A pressure as a finding says it, to a hundredth of a bar. */
private fun bar(pressure: Double): String {
    val hundredths = (pressure * 100).toLong()
    return "${hundredths / 100}.${(hundredths % 100).toString().padStart(2, '0')} bar"
}

/** The one model built here, and what `deco_model` says when a computer was running it. */
private const val BUHLMANN = "buhlmann"

/**
 * The most oxygen a diver plans to breathe, in bar.
 *
 * The figure agencies teach for a decompression stop, and the one a gas is chosen against. More
 * than this is not an error in the recording: it is what the run did, and a finding says so.
 */
private const val MOST_OXYGEN = 1.6

private const val SECONDS_IN_MINUTE = 60.0
