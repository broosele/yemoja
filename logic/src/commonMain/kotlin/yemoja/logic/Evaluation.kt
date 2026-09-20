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
        /** How much of the oxygen clocks the run has spent, as a percentage and a count. */
        val oxygen: OxygenClock,
        /** The central nervous system's clock through the run, as a percentage. */
        val cns: Series,
        /** Oxygen tolerance units taken through the run, which is a count and not a percentage. */
        val otu: Series,
        /**
         * How long to wait before flying after it, in seconds, or null where a day would not be
         * enough.
         *
         * A cabin is an altitude, so this is the wait until the ceiling allows one. What counts as
         * a cabin is one figure, the eight thousand feet an aircraft is held to.
         */
        val noFlight: Double?,
        /**
         * How long the compartments take to come back to what the surface settles them to, in
         * seconds, or null where a day would not do it.
         */
        val desaturation: Double?,
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

    /** Refused is nothing worked out, why, and whether the why is somebody's mistake. */
    data class Refused(val reason: String, val why: Refusal) : Evaluated()
}

/**
 * Refusal is why the model has nothing to say about a run.
 *
 * The two are not the same silence, and a screen treats them differently: most recordings say
 * nothing about the model they were made with, so saying so under each of them would only train a
 * reader to ignore the place where a real fault appears.
 */
enum class Refusal {

    /** Nothing here asks for an answer. A recording with no gradient factors is not a question. */
    UNASKED,

    /** What is written says something wrong, and somebody can put it right. */
    FAULTY,
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
class Finding(
    val second: Int,
    val severity: Severity,
    val said: String,
    /**
     * The cylinder it is about, under the key its run holds it by, or null where it is about the
     * dive rather than a cylinder.
     *
     * **Named rather than named in the sentence.** A key is what a run calls a cylinder, not what
     * a reader calls one: on a dive it is the user's own word, and in a calculation it is
     * machinery. So the sentence says what happened and this says what it happened to, and
     * whatever shows it supplies the name it shows elsewhere. `GUI-40`.
     */
    val source: String? = null,
) {

    override fun toString(): String =
        "${second}s $severity: ${source?.let { "$it " }.orEmpty()}$said"
}

/** How much a [Finding] matters. */
enum class Severity {

    /** Worth knowing. */
    NOTE,

    /** The model says this should not have been done. */
    WARNING,
}

/**
 * Run is a dive as the model takes it: depths against time, what is breathed and when, how
 * conservative to be, and what water and air it is in. Plain figures belonging to no item, so a
 * calculation can type one in and a profile can be read into one.
 *
 * The depths run forward in time and are read as straight lines between points, as a stored series
 * is. The switches name keys of [sources]; a run with no switches breathes its only source
 * throughout. Density and surface default to sea water at sea level, which is what a table
 * assumes. A run starts fresh unless [carried] and [oxygenCarried] say what it begins with.
 *
 * Immutable.
 */
class Run(
    val depth: List<Pair<Int, Double>>,
    val sources: Map<String, Source>,
    val gradientFactorLow: Double,
    val gradientFactorHigh: Double,
    val switches: List<Pair<Int, String>> = emptyList(),
    val density: Double = NOMINAL_DENSITY,
    val surface: Double = SEA_LEVEL,
    val carried: Tissues? = null,
    val oxygenCarried: OxygenClock? = null,
) {

    init {
        require(gradientFactorLow in 0.0..1.0) {
            "the low gradient factor should be 0 to 1, but was $gradientFactorLow"
        }
        require(gradientFactorHigh in 0.0..1.0) {
            "the high gradient factor should be 0 to 1, but was $gradientFactorHigh"
        }
        for (index in 1..<depth.size) {
            require(depth[index].first > depth[index - 1].first) {
                "depths should run forwards, but ${depth[index].first} follows " +
                    "${depth[index - 1].first}"
            }
        }
    }
}

/**
 * Source is one cylinder a run breathes from: what is in it, how fast it is breathed in litres a
 * minute at the surface, how many litres it holds, and what it was filled to in bar.
 *
 * Only the gas is needed. Without a rate it costs nothing that can be counted; without a size and
 * a fill it has no gauge to read.
 *
 * Immutable.
 */
class Source(
    val gas: Gas,
    val sac: Double? = null,
    val volume: Double? = null,
    val fill: Double? = null,
)

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

/**
 * What the model makes of [run], which belongs to no dive.
 *
 * The other door to the same model. A profile is read into a run and comes through here, so a
 * plan typed into a calculation and a plan on a dive are answered by one walk and cannot disagree.
 * A run starts fresh unless it says what it carries.
 */
fun evaluate(run: Run): Evaluated {
    val depths = pointsOf(run)
        ?: return Evaluated.Refused("this run holds no depths", Refusal.UNASKED)
    // A run holding cylinders and saying nothing about which was breathed is a gap somebody can
    // close, unlike a recording that simply says nothing about the model.
    val breathed = breathedBy(run) ?: return Evaluated.Refused(
        "nothing says what was breathed on this run",
        Refusal.FAULTY,
    )
    // A computer that reports only its changes writes the switch to its deco gas and nothing about
    // the gas it went down on. Taking the first switch's gas for the time before it would breathe
    // a deco mix on the bottom, which is a confident wrong answer rather than a refusal.
    depths.firstOrNull { it.metres > SURFACE }?.let { under ->
        if (breathed.startsAt > under.second + STARTING_GAS_LATEST) {
            return Evaluated.Refused(
                "nothing says what was breathed before the first gas switch, at " +
                    "${clockOf(breathed.startsAt)}; the run was under water from " +
                    clockOf(under.second),
                Refusal.FAULTY,
            )
        }
    }
    val carried = Carried.From(
        run.carried ?: Tissues.saturated(run.surface),
        run.oxygenCarried ?: OxygenClock.CLEAR,
    )
    return walked(depths, breathed, carried, run.model, run.density, run.surface)
}

/** [evaluate] on a profile, remembering the ones already on the chain behind it. */
private fun evaluated(profile: Item, seen: Set<Item>): Evaluated =
    when (val read = runOf(profile, seen)) {
        is Read.Refused -> Evaluated.Refused(read.reason, read.why)
        is Read.Run -> evaluate(read.run)
    }

/**
 * [profile] as a run, or why it is not one.
 *
 * Everything a profile has to say before the model is asked: which model and how conservative,
 * what water, what air, what it breathed from, and what it carries from the run before it. The
 * arithmetic sees none of the reading.
 */
private fun runOf(profile: Item, seen: Set<Item>): Read {
    if (profile in seen) return Read.Refused("this run carries gas from itself", Refusal.FAULTY)
    val low = (profile.single<Double>("gradient_factor_low") as? Result.Usable)?.value
    val high = (profile.single<Double>("gradient_factor_high") as? Result.Usable)?.value
    // Both factors are needed. One of them is a setting half written down, and guessing the other
    // would put a number into a decompression answer that nobody chose.
    if (low == null || high == null) {
        return Read.Refused(
            "nothing says what model this run was worked out with, or how conservative it was",
            Refusal.UNASKED,
        )
    }
    val named = (profile.single<String>("deco_model") as? Result.Usable)?.value ?: BUHLMANN
    if (named != BUHLMANN) {
        return Read.Refused("$named is not the model built here, which is $BUHLMANN", Refusal.UNASKED)
    }
    val density = (profile.single<Double>("density") as? Result.Usable)?.value
        ?: return Read.Refused(
            "nothing says what water this run was in, so its depths are not pressures",
            Refusal.UNASKED,
        )
    val surface = (profile.single<Double>("atmospheric_pressure") as? Result.Usable)?.value
        ?: SEA_LEVEL
    val carried = carriedInto(profile, surface, seen)
    if (carried is Carried.Refused) return Read.Refused(carried.reason, Refusal.FAULTY)
    carried as Carried.From
    return Read.Run(
        Run(
            depth = depthOf(profile),
            sources = sourcesOf(profile),
            gradientFactorLow = low,
            gradientFactorHigh = high,
            switches = switchesOf(profile),
            density = density,
            surface = surface,
            carried = carried.tissues,
            oxygenCarried = carried.oxygen,
        ),
    )
}

/** Read is a profile turned into a run, or why it could not be. */
private sealed class Read {
    class Run(val run: yemoja.logic.Run) : Read()
    class Refused(val reason: String, val why: Refusal) : Read()
}

/** The walk itself, once everything it needs has been found. */
private fun walked(
    depths: List<Point>,
    breathing: Breathing,
    carried: Carried.From,
    model: Model,
    density: Double,
    surface: Double,
): Evaluated.Done {
    var tissues = carried.tissues
    var oxygen = carried.oxygen
    val breathedCns = ArrayList<Double>()
    val breathedOtu = ArrayList<Double>()
    var burnt = false
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
            val fraction = breathing.mixAt(before.second).fractionO2
            oxygen = oxygen.breathing(
                was * fraction,
                ambient * fraction,
                (point.second - before.second).toDouble(),
            )
        }
        firstStop = firstStopAfter(tissues, firstStop, model, surface)
        val allowed = allowedDepthOf(tissues, firstStop, model, density, surface)
        val factor = gradientFactorAt(
            ambientAt(allowed, density, surface),
            firstStop,
            surface,
            model.low,
            model.high,
        )
        seconds += point.second
        ceilings += allowed

        if (allowed <= 0) {
            tissues.noDecompressionSeconds(breathing.mixAt(point.second), ambient, surface, factor)
                ?.let { limits += point.second to it }
        }
        for ((key, fill) in breathing.fills) {
            val left = fill.gauge - (used[key] ?: 0.0) / fill.volume
            gauges.getValue(key) += left
            if (left <= 0 && dry.add(key)) {
                findings += Finding(point.second, Severity.WARNING, "runs out of gas by here", key)
            }
        }
        breathedCns += oxygen.percentCns
        breathedOtu += oxygen.otu
        if (oxygen.percentCns > WHOLE_CLOCK && !burnt) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "the whole of the oxygen clock is spent by here",
            )
            burnt = true
        }
        // One finding a crossing, not one a sample: a diver who stays above the ceiling for ten
        // minutes has made one mistake, and ten lines of it would bury the rest.
        if (point.metres < allowed && !above) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "above the ceiling: ${metres(allowed)} was allowed" +
                    " and ${metres(point.metres)} was taken",
            )
        }
        above = point.metres < allowed
        val oxygen = breathing.mixAt(point.second).fractionO2 * ambient
        if (oxygen > MOST_OXYGEN && !rich) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "the oxygen in ${breathing.mixAt(point.second)} is at ${bar(oxygen)} here," +
                    " over the ${bar(MOST_OXYGEN)} a diver plans to",
                breathing.keyAt(point.second),
            )
        }
        rich = oxygen > MOST_OXYGEN
    }

    return Evaluated.Done(
        seriesOf(seconds, ceilings),
        seriesOf(limits.map { it.first }, limits.map { it.second }),
        tissues,
        oxygen,
        seriesOf(seconds, breathedCns),
        seriesOf(seconds, breathedOtu),
        tissues.noFlightSeconds(surface, CABIN, model.high),
        tissues.desaturationSeconds(surface),
        used,
        gauges.mapValues { (_, left) -> seriesOf(seconds, left) },
        findings.sortedBy { it.second },
    )
}

/**
 * The deepest [gas] may be breathed before its oxygen passes [most] bar, in metres, or null for a
 * mix holding no oxygen at all.
 *
 * What divers call a mix's maximum operating depth, and what a switch is chosen by: a deco gas is
 * carried to be breathed from the depth it becomes safe at. The same figure the run is judged
 * against afterwards, so a plan built to it raises no finding about it. Sea water at sea level
 * unless told otherwise.
 *
 * Null rather than a depth without end: a mix with no oxygen is breathable nowhere, which is a
 * different answer from *anywhere*, and a form should say so rather than offer a number.
 */
fun maximumOperatingDepth(
    gas: Gas,
    most: Double = MOST_OXYGEN,
    density: Double = NOMINAL_DENSITY,
    surface: Double = SEA_LEVEL,
): Double? {
    require(most > 0) { "an oxygen limit should be more than nought, but was $most" }
    if (gas.percentO2 <= 0) return null
    return depthAt(most / gas.fractionO2, density, surface)
}

/**
 * Ascended is the way out of a run, or why one could not be worked out.
 */
sealed class Ascended {

    /**
     * Done is the ascent to write into the profile: the depths it passes and holds, and the
     * switches that go with them.
     *
     * Each is a second and a value, to be appended to what the profile already holds. Turning them
     * into fields is the caller's, since what a screen writes and what an importer writes go
     * through the same door and neither belongs here. Empty where the run is already at the
     * surface.
     */
    class Done(
        val depth: List<Pair<Int, Double>>,
        val switches: List<Pair<Int, String>>,
    ) : Ascended()

    /** Refused is nothing worked out, and why. */
    data class Refused(val reason: String) : Ascended()
}

/**
 * The ascent [profile] would have to make from where its depths stop, rising at [metresAMinute]
 * and holding its shallowest stop at [lastStop] metres.
 *
 * **What comes back is written into the plan rather than remembered as a recipe.** Nothing reads
 * a rate to interpret a stored profile, so a plan holds the points themselves and means the same
 * thing to everything that reads it. The cost is that a generated ascent is frozen: change a gas
 * afterwards and the stops do not move, which evaluating the plan says at once. `LOGIC-35`.
 *
 * Stops go on the threes a diver counts in, and a run that owes any takes its shallowest at
 * [lastStop]. The gas is chosen at each depth: the richest of the run's own sources whose oxygen
 * stays within what a diver plans to, which is what a deco cylinder is carried for.
 *
 * Refused for whatever [evaluate] refuses, and for a run that will not surface within a day.
 */
fun completeAscent(profile: Item, metresAMinute: Double, lastStop: Double): Ascended =
    when (val read = runOf(profile, emptySet())) {
        is Read.Refused -> Ascended.Refused(read.reason)
        is Read.Run -> completeAscent(read.run, metresAMinute, lastStop)
    }

/** [completeAscent] for a run that belongs to no dive, which is the other door to the same walk. */
fun completeAscent(run: Run, metresAMinute: Double, lastStop: Double): Ascended {
    require(metresAMinute > 0) {
        "an ascent rate should be more than nought, but was $metresAMinute"
    }
    require(lastStop >= 0) { "a last stop should be 0 or deeper, but was $lastStop" }
    val evaluated = evaluate(run)
    if (evaluated is Evaluated.Refused) return Ascended.Refused(evaluated.reason)
    val model = run.model
    val breathing = breathedBy(run) ?: return Ascended.Refused("nothing says what is breathed")
    val depths = pointsOf(run) ?: return Ascended.Refused("this run holds no depths")
    val density = run.density
    val surface = run.surface

    var tissues = (evaluated as Evaluated.Done).surfacing
    var second = depths.last().second
    var metres = depths.last().metres
    var breathed = breathing.keyAt(second)
    var firstStop = 0.0
    val points = ArrayList<Pair<Int, Double>>()
    val switches = ArrayList<Pair<Int, String>>()

    while (metres > 0) {
        if (second - depths.last().second > LONGEST_ASCENT) {
            return Ascended.Refused("this run does not reach the surface within a day")
        }
        val ambient = ambientAt(metres, density, surface)
        firstStop = firstStopAfter(tissues, firstStop, model, surface)
        val allowed = allowedDepthOf(tissues, firstStop, model, density, surface, lastStop)
        val target = if (allowed < metres) allowed else metres
        val seconds = if (target < metres) {
            (((metres - target) / metresAMinute) * SECONDS_IN_MINUTE).toInt().coerceAtLeast(1)
        } else {
            SECONDS_IN_MINUTE.toInt()
        }
        tissues = tissues.breathing(
            breathing.mixes[breathed] ?: Gas.AIR,
            ambient,
            ambientAt(target, density, surface),
            seconds.toDouble(),
        )
        second += seconds
        metres = target
        points += second to metres
        breathing.richestAt(ambientAt(metres, density, surface))?.let { richest ->
            if (richest != breathed) {
                switches += second to richest
                breathed = richest
            }
        }
    }
    return Ascended.Done(points, switches)
}

/**
 * The shallowest depth these tissues may be brought to, in metres, on the steps [lastStop] asks
 * for, or the bare depth where it is nought.
 *
 * **The factor is read at the depth being asked about, not the one being held.** A gradient factor
 * slides with depth, so a diver at three metres who asks whether they may surface is asking what
 * the model allows at the surface, which is the high factor — as `manual/decompression.md` says it
 * is. Reading the factor where the diver stands instead judged every ascent by a stricter number
 * than the one that applies where they are going, and stops came out half as long again as they
 * should be.
 *
 * So the answer is a fixed point: the shallowest depth whose own factor permits being there. It
 * climbs from the surface and settles in a step or two, there being one depth for each stop.
 */
private fun allowedDepthOf(
    tissues: Tissues,
    firstStop: Double,
    model: Model,
    density: Double,
    surface: Double,
    lastStop: Double = 0.0,
): Double {
    var candidate = 0.0
    repeat(STEPS_TO_SETTLE) {
        val factor = gradientFactorAt(
            ambientAt(candidate, density, surface),
            firstStop,
            surface,
            model.low,
            model.high,
        )
        val needed = depthAt(tissues.ceiling(factor), density, surface)
            .let { if (lastStop > 0) stopFor(it, lastStop) else it }
        if (needed <= candidate) return candidate
        candidate = needed
    }
    return candidate
}

/** How many times the depth allowed is asked for before it is taken to have settled. */
private const val STEPS_TO_SETTLE = 12

/**
 * The depth an ascent may come up to, in metres: the ceiling rounded to the threes a diver counts
 * in, and never shallower than [lastStop] while anything is owed at all.
 */
private fun stopFor(ceiling: Double, lastStop: Double): Double {
    if (ceiling <= 0) return 0.0
    val stop = kotlin.math.ceil(ceiling / STOP_STEP) * STOP_STEP
    return if (stop < lastStop) lastStop else stop
}

/**
 * The pressure the low gradient factor is anchored at, once a stop is owed: the deepest the low
 * factor's ceiling has reached since, or [firstStop] where nothing is owed yet.
 *
 * **Owed at the high factor, not the low.** The low one says how deep the first stop is taken; it
 * says nothing about whether there is one. At 30/75 the low factor's ceiling passes the surface
 * long before a stop is owed at all, and anchoring it then drew a ceiling beside an hour of time
 * left on a dive that owed nothing. A computer holds the low factor back until a stop exists, and
 * so does this. `LOGIC-37`.
 */
private fun firstStopAfter(tissues: Tissues, firstStop: Double, model: Model, surface: Double): Double {
    if (firstStop <= surface && tissues.ceiling(model.high) <= surface) return firstStop
    val held = tissues.ceiling(model.low)
    return if (held > firstStop) held else firstStop
}

/** Model is how conservative a run is worked out: the two gradient factors. */
private class Model(val low: Double, val high: Double)

private val Run.model: Model get() = Model(gradientFactorLow, gradientFactorHigh)

/** Point is one depth in a profile: when, and how deep. */
private class Point(val second: Int, val metres: Double)

/** Carried is what the tissues hold when a run begins, or why that cannot be worked out. */
private sealed class Carried {
    class From(val tissues: Tissues, val oxygen: OxygenClock) : Carried()
    class Refused(val reason: String) : Carried()
}

/** Every depth [profile] could read, earliest first, as a run holds them. */
private fun depthOf(profile: Item): List<Pair<Int, Double>> {
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series ?: return emptyList()
    return (0..<depth.size).mapNotNull { at ->
        ((depth.valueAt(at) as? Element.Usable)?.value as? Double)?.let { depth.secondAt(at) to it }
    }
}

/** The depths of [run] as the walk takes them, or null where it holds none. */
private fun pointsOf(run: Run): List<Point>? =
    run.depth.map { (second, metres) -> Point(second, metres) }.ifEmpty { null }

/**
 * Breathing is what a run takes its gas from: which source at each moment, what is in each, how
 * fast it is breathed, and what it was filled to.
 *
 * Immutable.
 */
private class Breathing(
    val mixes: Map<String, Gas>,
    private val switches: List<Pair<Int, String>>,
    /** Litres a minute at the surface, for the sources that say. */
    val rates: Map<String, Double>,
    /** What each source was filled to and how big it is, for the sources that say both. */
    val fills: Map<String, Fill>,
) {

    /** When the first switch says what is breathed, in seconds; nothing says before it. */
    val startsAt: Int get() = switches.first().first

    /**
     * The source breathed at [second]: the one the last switch at or before it names.
     *
     * Before the first switch it is that switch's source, which is only right because
     * [evaluated] refuses a run whose first switch comes well after it has left the surface.
     */
    fun keyAt(second: Int): String =
        switches.lastOrNull { it.first <= second }?.second ?: switches.first().second

    /** What is in the source breathed at [second], and air where the source does not say. */
    fun mixAt(second: Int): Gas = mixes[keyAt(second)] ?: Gas.AIR

    /**
     * The source worth breathing at [ambient] bar: the richest whose oxygen stays within what a
     * diver plans to, or null where none of them does.
     *
     * Richest rather than nearest, because that is what a deco gas is carried for. Helium breaks
     * no tie: two mixes of one oxygen fraction are as good as each other here, and the one written
     * first is taken.
     */
    fun richestAt(ambient: Double): String? = mixes.entries
        .filter { it.value.fractionO2 * ambient <= MOST_OXYGEN }
        .maxByOrNull { it.value.fractionO2 }
        ?.key
}

/** Fill is what a cylinder was filled to, in bar of gauge pressure, and the litres it holds. */
private class Fill(val gauge: Double, val volume: Double)

/**
 * What [run] breathes from, or null where nothing says.
 *
 * The switches say which source, and a run with no switches breathes its only one, which is how an
 * ordinary single-cylinder dive is written. A switch naming a source the run does not have is
 * passed over rather than followed.
 */
private fun breathedBy(run: Run): Breathing? {
    if (run.sources.isEmpty()) return null
    val mixes = run.sources.mapValues { (_, source) -> source.gas }
    val rates = run.sources.mapNotNull { (key, source) -> source.sac?.let { key to it } }.toMap()
    val fills = run.sources.mapNotNull { (key, source) ->
        val gauge = source.fill
        val volume = source.volume
        if (gauge == null || volume == null || volume <= 0) null else key to Fill(gauge, volume)
    }.toMap()
    val written = run.switches.filter { (_, key) -> key in run.sources }
    if (written.isEmpty()) {
        val only = run.sources.keys.singleOrNull() ?: return null
        return Breathing(mixes, listOf(0 to only), rates, fills)
    }
    return Breathing(mixes, written, rates, fills)
}

/**
 * The sources [profile] breathes from, as a run holds them: its own where it keeps any, and the
 * dive's otherwise.
 *
 * A source with no `gas_type` is air, that being what a cylinder nobody said anything about holds.
 * Its `sac` is what it is breathed at: written on a plan, and worked out from the pressures of a
 * recording. One that says nothing costs nothing, since a figure nobody gave is not a figure of
 * nought.
 */
private fun sourcesOf(profile: Item): Map<String, Source> {
    val root = profile.rootOf("gas_sources") ?: return emptyMap()
    return ((root.keyed<OwnedItem>("gas_sources") as? Result.Usable)?.value.orEmpty())
        .mapNotNull { (key, entry) -> (entry as? Element.Usable)?.let { key to it.value } }
        .associate { (key, source) ->
            key to Source(
                gas = (source.single<Gas>("gas_type") as? Result.Usable)?.value ?: Gas.AIR,
                sac = (source.read("sac") as? Result.Usable)?.value as? Double,
                volume = (source.read("volume") as? Result.Usable)?.value as? Double,
                fill = (source.single<Double>("start_pressure") as? Result.Usable)?.value,
            )
        }
}

/** The gas switches [profile] could read, as a run holds them. */
private fun switchesOf(profile: Item): List<Pair<Int, String>> {
    val switches = (profile.read("gas_switches") as? Result.Usable)?.value as? Series
        ?: return emptyList()
    return (0..<switches.size).mapNotNull { at ->
        ((switches.valueAt(at) as? Element.Usable)?.value as? KeyReference)
            ?.let { switches.secondAt(at) to it.key }
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
        ?: return Carried.From(Tissues.saturated(surface), OxygenClock.CLEAR)
    val id = named.id ?: return Carried.Refused("the run before this one names no dive")
    val dive = profile.set[id] ?: return Carried.Refused("$id is not in this logbook")
    val earlier = ((dive.keyed<OwnedItem>("profiles") as? Result.Usable)?.value.orEmpty())
        .let { (it[named.key] as? Element.Usable)?.value }
        ?: return Carried.Refused("$id has no run called ${named.key}")
    val interval = (owner(profile)?.single<Double>("surface_interval") as? Result.Usable)?.value
        ?: return Carried.Refused("nothing says how long the surface interval before this was")

    return when (val ran = evaluated(earlier, seen + profile)) {
        is Evaluated.Refused -> Carried.Refused("the run before this one: ${ran.reason}")
        is Evaluated.Done -> Carried.From(
            ran.surfacing.breathing(Gas.AIR, surface, surface, interval),
            // The clock runs backwards on the surface, which is what a surface interval is for.
            ran.oxygen.breathing(
                surface * Gas.AIR.fractionO2,
                surface * Gas.AIR.fractionO2,
                interval,
            ),
        )
    }
}

/** The dive a profile belongs to, or null where it belongs to nothing. */
private fun owner(profile: Item): Item? = (profile as? OwnedItem)?.parent

/** A series from times and values that were worked out together, and so are the same length. */
private fun seriesOf(seconds: List<Int>, values: List<Double>): Series =
    Series(seconds.toIntArray(), values.map { Element.Usable(it as Any) })

/** Seconds into a run as a reader counts them, minutes and seconds: `24:00`. */
private fun clockOf(second: Int): String =
    "${second / 60}:${(second % 60).toString().padStart(2, '0')}"

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
 *
 * Public so that a form offering a switch and the finding that judges one are held to a single
 * figure. A screen inventing its own would let a plan be built that the model then objects to.
 */
const val MOST_OXYGEN = 1.6

private const val SECONDS_IN_MINUTE = 60.0

/**
 * How long after a run goes under its first gas switch may still say the gas it went down on, in
 * seconds.
 *
 * A computer writes the starting gas at its first sample or the one after, and one sampling every
 * five seconds is already under a metre before it has. A minute takes in any of them, and no gas
 * is breathed long enough in a minute's descent for which one it was to matter. A switch later
 * than that is a change, and what came before it is unrecorded.
 */
private const val STARTING_GAS_LATEST = 60

/** The whole of the central nervous system's single-exposure limit, as a percentage. */
private const val WHOLE_CLOCK = 100.0

/**
 * The pressure inside an aircraft, in bar.
 *
 * Eight thousand feet, which is the cabin altitude aircraft are held to and what a diver is really
 * asking about when they ask when they may fly.
 */
private const val CABIN = 0.7565

/** The step a stop is taken on, in metres: three, six, nine, as a diver counts them. */
private const val STOP_STEP = 3.0

/** How long an ascent may take before it is called one that does not come up. */
private const val LONGEST_ASCENT = 24 * 60 * 60
