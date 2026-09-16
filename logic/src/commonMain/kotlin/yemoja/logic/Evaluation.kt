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

    return walked(depths, breathed, (carried as Carried.Tissues).tissues, model, density, surface)
}

/** The walk itself, once everything it needs has been found. */
private fun walked(
    depths: List<Point>,
    breathed: (Int) -> Gas,
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
    var above = false

    for ((index, point) in depths.withIndex()) {
        if (index > 0) {
            val before = depths[index - 1]
            tissues = tissues.breathing(
                breathed(before.second),
                ambientAt(before.metres, density, surface),
                ambientAt(point.metres, density, surface),
                (point.second - before.second).toDouble(),
            )
        }
        val ambient = ambientAt(point.metres, density, surface)
        val held = tissues.ceiling(model.low)
        if (held > surface && held > firstStop) firstStop = held
        val factor = gradientFactorAt(ambient, firstStop, surface, model.low, model.high)
        val ceiling = tissues.ceiling(factor)
        seconds += point.second
        ceilings += depthAt(ceiling, density, surface)

        if (ceiling <= surface) {
            tissues.noDecompressionSeconds(breathed(point.second), ambient, surface, factor)
                ?.let { limits += point.second to it }
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
    }

    return Evaluated.Done(
        seriesOf(seconds, ceilings),
        seriesOf(limits.map { it.first }, limits.map { it.second }),
        tissues,
        findings,
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
 * What was being breathed at each second of [profile], or null where nothing says.
 *
 * The switches say it, and the source breathed at a moment is the one the last switch at or before
 * it names. A run with no switches breathes its only source where it has exactly one, which is how
 * an ordinary single-cylinder dive is written. A source with no `gas_type` is air, that being what
 * a cylinder nobody said anything about holds.
 */
private fun breathedBy(profile: Item): ((Int) -> Gas)? {
    val root = profile.rootOf("gas_sources") ?: return null
    val sources = ((root.keyed<OwnedItem>("gas_sources") as? Result.Usable)?.value.orEmpty())
        .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }
        .toMap()
    if (sources.isEmpty()) return null
    val mixes = sources.mapValues { (_, source) ->
        (source.single<Gas>("gas_type") as? Result.Usable)?.value ?: Gas.AIR
    }
    val switches = (profile.read("gas_switches") as? Result.Usable)?.value as? Series
    if (switches == null || switches.size == 0) {
        val only = mixes.values.singleOrNull() ?: return null
        return { only }
    }
    val times = (0..<switches.size).map { switches.secondAt(it) }
    val named = (0..<switches.size).map {
        ((switches.valueAt(it) as? Element.Usable)?.value as? KeyReference)?.key
    }
    return here@{ second ->
        val last = times.indexOfLast { it <= second }
        if (last < 0) mixes.getValue(mixes.keys.first()) // Before the first switch, what it names.
        else mixes[named[last]] ?: return@here Gas.AIR
    }
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

/** The one model built here, and what `deco_model` says when a computer was running it. */
private const val BUHLMANN = "buhlmann"
