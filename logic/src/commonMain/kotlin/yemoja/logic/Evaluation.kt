package yemoja.logic

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

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
         * How long to wait before flying after it, in seconds, or null where two days would not
         * be enough.
         *
         * A cabin is an altitude, so this is the wait until the ceiling allows one. What counts as
         * a cabin is one figure, the eight thousand feet an aircraft is held to.
         *
         * **This is not the published guideline, which is usually longer.** DAN recommends at
         * least 12 hours after a single dive without stops, at least 18 hours after several dives
         * in a day or several days of diving, and substantially longer than 18 hours after a dive
         * with stops. Those minimums come from trials rather than a tissue model, and after a dive
         * without stops this figure is often an hour or two. Nothing here applies them, so a screen
         * showing this figure decides whether to show them beside it.
         */
        val noFlight: Double?,
        /**
         * How long the compartments take to come back to what the surface settles them to, in
         * seconds, or null where two days would not do it.
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
        /** Works out [timeToSurface] the first time it is asked for. */
        timeToSurfaceOf: () -> Series,
        /**
         * The gradient factor the compartments stand at in each moment's own water, as a
         * percentage: what divers call GF99. Nought or below is no supersaturation. `LOGIC-37`.
         */
        val gradientFactorNow: Series,
    ) : Evaluated() {

        /**
         * How long the way up would take from each moment, in seconds: the time to surface.
         *
         * Each is an ascent worked out as `completeAscent` works one out, rising at the run's own
         * rate and taking its own last stop, or at [TTS_METRES_A_MINUTE] and [TTS_LAST_STOP] where
         * it names neither, as a recording does. A moment within [TTS_EVERY] seconds of the last
         * one worked out is passed over, and so is one from which no way up is found within a day.
         *
         * **Worked out when first read**, an ascent a moment being the dearest thing here: what
         * reads only the rest of the answer, a dive followed or a gas reserve, does not pay for it.
         * `LOGIC-37`.
         */
        val timeToSurface: Series by lazy(timeToSurfaceOf)
    }

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
 * [safetyStop] and [ascentRate] are a plan's. An ascent holds the safety stop and a run that does
 * not is warned about, and a run rising faster than [ascentRate] metres a minute is warned about
 * where it does. A recording sets neither and is judged as it always was. `LOGIC-37`.
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
    val safetyStop: SafetyStop? = null,
    val ascentRate: Double? = null,
    /** The depth a plan's way up takes its shallowest stop at, in metres, or null for a recording. */
    val lastStop: Double? = null,
) {

    init {
        require(ascentRate == null || ascentRate > 0) {
            "an ascent rate should be more than nought, but was $ascentRate"
        }
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
 * SafetyStop is a depth a plan holds on the way up whether or not the model asks for it, and the
 * least time it is held there.
 *
 * **A minimum, not an extra stop.** A deco stop at the same depth counts towards it, so one that is
 * already longer is left alone and a shorter one is lengthened. Nought seconds is no safety stop.
 * It is owed only by a run that went deeper than it.
 *
 * Immutable.
 */
class SafetyStop(val metres: Double, val seconds: Int) {

    init {
        require(metres > 0) { "a safety stop should be deeper than the surface, but was $metres" }
        require(seconds >= 0) { "a safety stop should last 0 seconds or more, but was $seconds" }
    }
}

/**
 * Source is one cylinder a run breathes from: what is in it, how fast it is breathed in litres a
 * minute at the surface, how many litres it holds, and what it was filled to in bar.
 *
 * Only the gas is needed. Without a rate it costs nothing that can be counted; without a size and
 * a fill it has no gauge to read.
 *
 * [mostOxygen] is the partial pressure this cylinder is held to, in bar, and is what a warning
 * about its oxygen and the ascent's choice of it are both read against. [leastOxygen] is the least
 * it may be breathed at, which a warning about a hypoxic mix breathed too shallow reads. A cylinder
 * the ascent may not choose is breathed only where a switch names it, which is how a bailout is
 * carried. A recording sets none of them, and is judged against the defaults. `LOGIC-37`.
 *
 * Immutable.
 */
class Source(
    val gas: Gas,
    val sac: Double? = null,
    val volume: Double? = null,
    val fill: Double? = null,
    val mostOxygen: Double = MOST_OXYGEN,
    val ascentMayChoose: Boolean = true,
    val leastOxygen: Double = LEAST_OXYGEN,
) {

    init {
        require(mostOxygen > 0) { "an oxygen limit should be more than nought, but was $mostOxygen" }
        require(leastOxygen > 0) { "an oxygen minimum should be more than nought, but was $leastOxygen" }
    }
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

/**
 * What the model makes of [run], which belongs to no dive.
 *
 * The other door to the same model. A profile is read into a run and comes through here, so a
 * plan typed into a calculation and a plan on a dive are answered by one walk and cannot disagree.
 * A run starts fresh unless it says what it carries.
 */
fun evaluate(run: Run): Evaluated {
    val depths = pointsOf(run)
        ?: return Evaluated.Refused(
            "this recording holds no depths, so nothing can be worked out from it",
            Refusal.UNASKED
        )
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
    return walked(
        run,
        depths,
        breathed,
        carried,
        run.model,
        run.density,
        run.surface,
        run.safetyStop,
        run.ascentRate,
    )
}

/** [evaluate] on a profile, remembering the ones already on the chain behind it. */
private fun evaluated(profile: Item, seen: Set<Item>): Evaluated =
    when (val read = runOf(profile, seen)) {
        is RunResult.Refused -> Evaluated.Refused(read.reason, read.why)
        is RunResult.Run -> evaluate(read.run)
    }

/**
 * [profile] as a run, or why it is not one.
 *
 * Everything a profile has to say before the model is asked: which model and how conservative,
 * what water, what air, what it breathed from, and what it carries from the run before it. The
 * arithmetic sees none of the reading.
 */
private fun runOf(profile: Item, seen: Set<Item>): RunResult {
    if (profile in seen) return RunResult.Refused(
        "this dive is set to follow itself, so the gas it starts with cannot be worked out",
        Refusal.FAULTY
    )
    val low = (profile.single<Double>("gradient_factor_low") as? Result.Usable)?.value
    val high = (profile.single<Double>("gradient_factor_high") as? Result.Usable)?.value
    // Both factors are needed. One of them is a setting half written down, and guessing the other
    // would put a number into a decompression answer that nobody chose.
    if (low == null || high == null) {
        return RunResult.Refused(
            "this recording should say which model it was calculated with and how conservative it was",
            Refusal.UNASKED,
        )
    }
    val named = (profile.single<String>("deco_model") as? Result.Usable)?.value ?: BUHLMANN
    if (named != BUHLMANN) {
        return RunResult.Refused("$named is not the model built here, which is $BUHLMANN", Refusal.UNASKED)
    }
    val density = (profile.single<Double>("density") as? Result.Usable)?.value
        ?: return RunResult.Refused(
            "this recording should say whether the water was salt or fresh, without which a depth is not a pressure",
            Refusal.UNASKED,
        )
    val surface = (profile.single<Double>("atmospheric_pressure") as? Result.Usable)?.value
        ?: SEA_LEVEL
    val carried = carriedInto(profile, surface, seen)
    if (carried is Carried.Refused) return RunResult.Refused(carried.reason, Refusal.FAULTY)
    carried as Carried.From
    return RunResult.Run(
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

/** RunResult is a profile turned into a run, or why it could not be. */
private sealed class RunResult {
    class Run(val run: yemoja.logic.Run) : RunResult()
    class Refused(val reason: String, val why: Refusal) : RunResult()
}

/** The walk itself, once everything it needs has been found. */
private fun walked(
    run: Run,
    depths: List<Point>,
    breathing: Breathing,
    carried: Carried.From,
    model: Model,
    density: Double,
    surface: Double,
    safetyStop: SafetyStop?,
    ascentRate: Double?,
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
    // The moments a time to surface is worked out from, kept for when it is asked for.
    val surfacingFrom = ArrayList<Triple<Int, Tissues, Double>>()
    val factorsNow = ArrayList<Double>()
    val findings = ArrayList<Finding>()
    val used = HashMap<String, Double>()
    val gauges = breathing.fills.mapValues { ArrayList<Double>() }
    var above = false
    var rich = false
    var lean = false
    var hurried = false
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
            // Once a crossing, as the ceiling is: a fast rise drawn as three segments is one.
            if (ascentRate != null) {
                val rate = (before.metres - point.metres) / minutes
                if (rate > ascentRate && !hurried) {
                    findings += Finding(
                        before.second,
                        Severity.WARNING,
                        "Ascent ${metres(rate)}/min should be at most ${metres(ascentRate)}/min",
                    )
                }
                hurried = rate > ascentRate
            }
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
        factorsNow += tissues.gradientFactorIn(ambient) * PERCENT
        val sinceLast = point.second - (surfacingFrom.lastOrNull()?.let { depths[it.first].second } ?: Int.MIN_VALUE / 2)
        if (sinceLast >= TTS_EVERY || index == depths.lastIndex) surfacingFrom += Triple(index, tissues, firstStop)

        if (allowed <= 0) {
            tissues.noDecompressionSeconds(breathing.mixAt(point.second), ambient, surface, factor)
                ?.let { limits += point.second to it }
        }
        for ((key, fill) in breathing.fills) {
            val left = fill.gauge - (used[key] ?: 0.0) / fill.volume
            gauges.getValue(key) += left
            if (left <= 0 && dry.add(key)) {
                findings += Finding(
                    point.second,
                    Severity.WARNING,
                    "empty, it needs more volume, a higher start pressure or a lower SAC",
                    key,
                )
            }
        }
        breathedCns += oxygen.percentCns
        breathedOtu += oxygen.otu
        if (oxygen.percentCns > WHOLE_CLOCK && !burnt) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "CNS should stay below 100 %",
            )
            burnt = true
        }
        // One finding a crossing, not one a sample: a diver who stays above the ceiling for ten
        // minutes has made one mistake, and ten lines of it would bury the rest.
        if (point.metres < allowed && !above) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "Depth ${metres(point.metres)} should be at least ${metres(allowed)}, the ceiling",
            )
        }
        above = point.metres < allowed
        val oxygen = breathing.mixAt(point.second).fractionO2 * ambient
        val most = breathing.mostOxygenOf(breathing.keyAt(point.second))
        if (oxygen > most && !rich) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "pO₂ ${bar(oxygen)} should be at most ${bar(most)}",
                breathing.keyAt(point.second),
            )
        }
        rich = oxygen > most
        // A hypoxic mix breathed too shallow, once a crossing as a rich one breathed too deep is.
        val least = breathing.leastOxygenOf(breathing.keyAt(point.second))
        if (oxygen < least && !lean) {
            findings += Finding(
                point.second,
                Severity.WARNING,
                "pO₂ ${bar(oxygen)} should be at least ${bar(least)}",
                breathing.keyAt(point.second),
            )
        }
        lean = oxygen < least
    }
    safetyStopFinding(depths, safetyStop)?.let { findings += it }

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
        {
            val surfacings = surfacingFrom.mapNotNull { (index, at, anchor) ->
                timeToSurfaceFrom(run, breathing, depths, index, at, anchor)?.let { depths[index].second to it }
            }
            seriesOf(surfacings.map { it.first }, surfacings.map { it.second })
        },
        seriesOf(seconds, factorsNow),
    )
}

/**
 * How long the way up from the point at [index] of [depths] would take, in seconds, with [tissues]
 * as they stand there and the gradient factors anchored at [anchor]. Nought at the surface, and
 * null where no way up is found within a day.
 */
private fun timeToSurfaceFrom(
    run: Run,
    breathing: Breathing,
    depths: List<Point>,
    index: Int,
    tissues: Tissues,
    anchor: Double,
): Double? {
    val point = depths[index]
    if (point.metres <= SURFACE) return 0.0
    val climbed = climbed(
        From(tissues, point.second, point.metres, breathing.keyAt(point.second), anchor),
        breathing,
        run,
        run.ascentRate ?: TTS_METRES_A_MINUTE,
        run.lastStop ?: TTS_LAST_STOP,
        depths.subList(0, index + 1).map { it.second to it.metres },
    ) ?: return null
    return (climbed.points.lastOrNull()?.first?.minus(point.second) ?: 0).toDouble()
}

/**
 * The ascent rate a time to surface assumes where the run names none, as a recording does: nine
 * metres a minute, the rate Bühlmann's tables were made with. A fixed figure rather than a
 * setting, so what is said about a dive already done does not move when a setting does.
 */
const val TTS_METRES_A_MINUTE = 9.0

/** The last stop a time to surface assumes where the run names none: three metres. */
const val TTS_LAST_STOP = 3.0

/**
 * The fewest seconds between two moments a time to surface is worked out from: ten, so a computer
 * sampling every two seconds costs a fifth of the ascents, and a plan, whose points lie further
 * apart, loses none.
 */
const val TTS_EVERY = 10

private const val PERCENT = 100.0

/**
 * What [depths] owe [safetyStop] and did not hold, or null where they owe nothing or held it.
 *
 * Only a run that reaches the surface is judged, since one ending under water may still be about
 * to stop.
 */
private fun safetyStopFinding(depths: List<Point>, safetyStop: SafetyStop?): Finding? {
    if (safetyStop == null || safetyStop.seconds <= 0) return null
    if (depths.last().metres > SURFACE) return null
    val held = heldAt(depths.map { it.second to it.metres }, safetyStop.metres) ?: return null
    if (held >= safetyStop.seconds) return null
    // Said where the run leaves the stop's depth for the last time, which is where it went wrong.
    val left = depths.last { it.metres >= safetyStop.metres }
    return Finding(
        left.second,
        Severity.WARNING,
        "Safety stop at ${metres(safetyStop.metres)} should last ${clockOf(safetyStop.seconds)}, " +
                "not ${clockOf(held)}",
    )
}

/**
 * The seconds [points] hold at [metres] after they were last deeper than it, or null where they
 * never were.
 *
 * Holding means a stretch that begins and ends at that depth, so the rise to it is not counted.
 */
internal fun heldAt(points: List<Pair<Int, Double>>, metres: Double): Int? {
    val deepest = points.indexOfLast { it.second > metres }
    if (deepest < 0) return null
    var held = 0
    for (index in deepest + 2..points.lastIndex) {
        val (was, from) = points[index - 1]
        val (now, to) = points[index]
        if (from == metres && to == metres) held += now - was
    }
    return held
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
 * The shallowest [gas] may be breathed before its oxygen falls below [least] bar, in metres, or null
 * for a mix holding no oxygen at all.
 *
 * What divers call a mix's minimum operating depth, which only a hypoxic mix has: breathed any
 * shallower, it cannot keep a diver conscious. Nought for a mix breathable at the surface. Sea
 * water at sea level unless told otherwise.
 *
 * The walk warns wherever a run breathes a mix shallower than this, at [LEAST_OXYGEN], as it warns
 * of one breathed deeper than its maximum.
 */
fun minimumOperatingDepth(
    gas: Gas,
    least: Double = LEAST_OXYGEN,
    density: Double = NOMINAL_DENSITY,
    surface: Double = SEA_LEVEL,
): Double? {
    require(least > 0) { "an oxygen minimum should be more than nought, but was $least" }
    if (gas.percentO2 <= 0) return null
    return depthAt(least / gas.fractionO2, density, surface)
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
     *
     * The point the ascent leaves from is the run's last and is not repeated here, so a caller
     * drawing the ascent as stretches begins the first at that point. A run owing no stop comes
     * back as one surfacing point.
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
 * [lastStop]. The gas is chosen at each depth: the richest of the run's own sources the ascent may
 * choose whose oxygen stays within that source's own limit, which is what a deco cylinder is
 * carried for. A bailout is never chosen, and one already breathed is left only for a richer mix.
 *
 * Refused for whatever [evaluate] refuses, and for a run that will not surface within a day.
 */
fun completeAscent(profile: Item, metresAMinute: Double, lastStop: Double): Ascended =
    when (val read = runOf(profile, emptySet())) {
        is RunResult.Refused -> Ascended.Refused(read.reason)
        is RunResult.Run -> completeAscent(read.run, metresAMinute, lastStop)
    }

/** [completeAscent] for a run that belongs to no dive, which is the other door to the same walk. */
fun completeAscent(run: Run, metresAMinute: Double, lastStop: Double, switchStops: Boolean = false): Ascended {
    require(metresAMinute > 0) {
        "an ascent rate should be more than nought, but was $metresAMinute"
    }
    require(lastStop >= 0) { "a last stop should be 0 or deeper, but was $lastStop" }
    val evaluated = evaluate(run)
    if (evaluated is Evaluated.Refused) return Ascended.Refused(evaluated.reason)
    val breathing = breathedBy(run) ?: return Ascended.Refused("nothing says what is breathed")
    val depths =
        pointsOf(run) ?: return Ascended.Refused("this recording holds no depths, so nothing can be worked out from it")
    val end = depths.last()
    val climbed = climbed(
        From(
            (evaluated as Evaluated.Done).surfacing,
            end.second,
            end.metres,
            breathing.keyAt(end.second),
            anchorOf(run, breathing),
        ),
        breathing,
        run,
        metresAMinute,
        lastStop,
        run.depth,
        switchStops,
    ) ?: return Ascended.Refused("No way up was found within 24 hours. Check the depths and the gases")
    return Ascended.Done(climbed.points, climbed.switches)
}

/**
 * Where [run]'s gradient factors are anchored by its last point, walked as [evaluate] walks it, or
 * nought where nothing has been owed.
 *
 * An ascent finished from part-way up takes this, so it is the rest of the ascent the run began.
 */
internal fun anchorOf(run: Run, breathing: Breathing): Double {
    var tissues = run.carried ?: Tissues.saturated(run.surface)
    var anchor = 0.0
    for ((index, point) in run.depth.withIndex()) {
        val (second, metres) = point
        if (index > 0) {
            val (was, from) = run.depth[index - 1]
            tissues = tissues.breathing(
                breathing.mixAt(was),
                ambientAt(from, run.density, run.surface),
                ambientAt(metres, run.density, run.surface),
                (second - was).toDouble(),
            )
        }
        anchor = firstStopAfter(tissues, anchor, run.model, run.surface)
    }
    return anchor
}

/** From is where an ascent begins: the tissues, the moment, the depth, and the source breathed. */
internal class From(
    val tissues: Tissues,
    val second: Int,
    val metres: Double,
    val breathed: String,
    /**
     * The depth the gradient factors are anchored at so far, or nought where nothing has been owed.
     *
     * An ascent begun part-way through a dive takes the dive's anchor. Started from nought, it
     * would anchor at its own starting point, shallower than the dive's first stop, and hold
     * longer than the dive it branches from.
     */
    val firstStop: Double = 0.0,
)

/** Climbed is an ascent's points and switches, each a second and a value. */
internal class Climbed(val points: List<Pair<Int, Double>>, val switches: List<Pair<Int, String>>)

/**
 * The way up from [from], choosing among [breathing]'s sources, under [run]'s model, water, air
 * and safety stop, or null where it does not surface within a day.
 *
 * [before] is the run that led to [from], which says how much of the safety stop is already held.
 * The ascent a plan is completed with and the one a lost-gas reserve is costed on both come from
 * here, so they cannot disagree about where a stop goes.
 *
 * **A richer gas is switched to where the ascent stops anyway**: at a stop the model owes, or at
 * the surface. Where [switchStops] says so, the ascent also stops at the deepest depth on the
 * stops' grid where a richer gas it may choose comes within its own limit, switches there, and
 * holds a minute for the switch unless a stop is owed there already. Without it a dive owing no
 * stop deeper than that depth passes it and stays on its bottom gas. `LOGIC-35`.
 */
internal fun climbed(
    from: From,
    breathing: Breathing,
    run: Run,
    metresAMinute: Double,
    lastStop: Double,
    before: List<Pair<Int, Double>>,
    switchStops: Boolean = false,
): Climbed? {
    val model = run.model
    val density = run.density
    val surface = run.surface
    var tissues = from.tissues
    var second = from.second
    var metres = from.metres
    var breathed = from.breathed
    var firstStop = from.firstStop
    val points = ArrayList<Pair<Int, Double>>()
    val switches = ArrayList<Pair<Int, String>>()
    // What the safety stop still needs, counting what the run already held at its depth.
    val safety = run.safetyStop?.takeIf { it.seconds > 0 && metres >= it.metres }
    var owed = safety?.let { stop ->
        heldAt(before, stop.metres)?.let { held -> stop.seconds - held } ?: 0
    } ?: 0

    while (metres > 0) {
        if (second - from.second > LONGEST_ASCENT) return null
        val ambient = ambientAt(metres, density, surface)
        firstStop = firstStopAfter(tissues, firstStop, model, surface)
        val stopping = safety != null && owed > 0 && metres >= safety.metres
        val allowed = allowedDepthOf(tissues, firstStop, model, density, surface, lastStop).let { held ->
            if (held < metres) return@let held
            // A stop is left once the tissues would be within the ceiling on arriving at the next,
            // counting the gas given off on the way up. That is the question evaluate asks of the
            // point arrived at, and asking it of the tissues before the rise held each stop up to a
            // minute longer than the model needs.
            val above = stopAbove(metres, lastStop)
            val reached = tissues.breathing(
                breathing.mixes[breathed] ?: Gas.AIR,
                ambient,
                ambientAt(above, density, surface),
                riseSeconds(metres, above, metresAMinute).toDouble(),
            )
            val anchored = firstStopAfter(reached, firstStop, model, surface)
            if (allowedDepthOf(reached, anchored, model, density, surface, lastStop) <= above) above else held
        }
        val owedFloor = if (safety != null && stopping) max(allowed, safety.metres) else allowed
        // A stop to switch gas, where one is asked for and lies between here and the next one owed.
        val switching = if (switchStops) {
            breathing.switchDepth(breathed, metres, owedFloor, STOP_STEP, density, surface)
        } else {
            null
        }
        val floor = switching ?: owedFloor
        val target = if (floor < metres) floor else metres
        val seconds = when {
            target < metres -> riseSeconds(metres, target, metresAMinute)
            // Held for the safety stop alone, so for what it still needs rather than a minute.
            allowed < metres -> min(owed, SECONDS_IN_MINUTE.toInt())
            else -> SECONDS_IN_MINUTE.toInt()
        }
        if (safety != null && target == metres && metres == safety.metres) owed -= seconds
        tissues = tissues.breathing(
            breathing.mixes[breathed] ?: Gas.AIR,
            ambient,
            ambientAt(target, density, surface),
            seconds.toDouble(),
        )
        second += seconds
        metres = target
        points += second to metres
        val arrived = ambientAt(metres, density, surface)
        var switched = false
        breathing.richestAt(arrived)?.let { richest ->
            if (richest != breathed && breathing.worthSwitching(breathed, richest, arrived)) {
                switches += second to richest
                breathed = richest
                switched = true
            }
        }
        // A stop made for the switch alone is held for it; one owed there already holds anyway.
        if (switched && switching != null && metres == switching) {
            tissues = tissues.breathing(breathing.mixes[breathed] ?: Gas.AIR, arrived, arrived, SWITCH_SECONDS.toDouble())
            second += SWITCH_SECONDS
            points += second to metres
            if (safety != null && metres == safety.metres) owed -= SWITCH_SECONDS
        }
    }
    return Climbed(points, switches)
}

/**
 * The shallowest depth these tissues may be brought to, in metres, on the steps [lastStop] asks
 * for, or the bare depth where it is nought.
 *
 * **The factor is read at the depth being asked about, not the one being held.** A gradient factor
 * slides with depth, so a diver at three metres who asks whether they may surface is asking what
 * the model allows at the surface, which is the high factor — as `manual/decompression.md` says it
 * is. Reading the factor where the diver stands instead judged every ascent by a stricter number
 * than the one that applies where they are going, and a sliding pair's stops came out about a
 * third longer than they should be.
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

/** How long rising from [metres] to [target] takes at [metresAMinute], never under a second. */
private fun riseSeconds(metres: Double, target: Double, metresAMinute: Double): Int =
    ceil((metres - target) / metresAMinute * SECONDS_IN_MINUTE).toInt().coerceAtLeast(1)

/** The next depth an ascent held at [metres] may rise to: the stop above, or the surface from the last. */
private fun stopAbove(metres: Double, lastStop: Double): Double {
    val step = ceil(metres / STOP_STEP) * STOP_STEP - STOP_STEP
    return when {
        step >= lastStop -> step
        metres > lastStop -> lastStop
        else -> 0.0
    }
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
internal fun firstStopAfter(tissues: Tissues, firstStop: Double, model: Model, surface: Double): Double {
    if (firstStop <= surface && tissues.ceiling(model.high) <= surface) return firstStop
    val held = tissues.ceiling(model.low)
    return if (held > firstStop) held else firstStop
}

/** Model is how conservative a run is worked out: the two gradient factors. */
internal class Model(val low: Double, val high: Double)

internal val Run.model: Model get() = Model(gradientFactorLow, gradientFactorHigh)

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
internal class Breathing(
    val mixes: Map<String, Gas>,
    private val switches: List<Pair<Int, String>>,
    /** Litres a minute at the surface, for the sources that say. */
    val rates: Map<String, Double>,
    /** What each source was filled to and how big it is, for the sources that say both. */
    val fills: Map<String, Fill>,
    /** The oxygen each source is held to, in bar. */
    private val mostOxygen: Map<String, Double>,
    /** The sources an ascent may switch to without being told. */
    private val choosable: Set<String>,
    /** The least oxygen each source may be breathed at, in bar. */
    private val leastOxygen: Map<String, Double>,
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

    /** The same sources and switches, with only [keys] open to an ascent's choice. */
    fun choosing(keys: Set<String>): Breathing =
        Breathing(mixes, switches, rates, fills, mostOxygen, keys, leastOxygen)

    /**
     * The deepest depth on a grid of [step] metres, shallower than [metres] and deeper than
     * [shallowest], at which a source richer than [breathed] that an ascent may choose comes within
     * its own oxygen limit, or null where none does.
     */
    fun switchDepth(
        breathed: String,
        metres: Double,
        shallowest: Double,
        step: Double,
        density: Double,
        surface: Double,
    ): Double? {
        val now = mixes[breathed]?.fractionO2 ?: return null
        return mixes.filter { (key, mix) -> key in choosable && mix.fractionO2 > now }
            .mapNotNull { (key, mix) -> maximumOperatingDepth(mix, mostOxygenOf(key), density, surface) }
            .map { kotlin.math.floor(it / step) * step }
            .filter { it < metres && it > shallowest }
            .maxOrNull()
    }

    /** The oxygen [key] is held to, in bar, and [MOST_OXYGEN] for a source nobody named. */
    fun mostOxygenOf(key: String): Double = mostOxygen[key] ?: MOST_OXYGEN

    /** The least oxygen [key] may be breathed at, in bar, and [LEAST_OXYGEN] for one nobody named. */
    fun leastOxygenOf(key: String): Double = leastOxygen[key] ?: LEAST_OXYGEN

    /**
     * The source worth breathing at [ambient] bar: the richest the ascent may choose whose oxygen
     * stays within its own limit, or null where none of them does.
     *
     * Richest rather than nearest, because that is what a deco gas is carried for. Helium breaks
     * no tie: two mixes of one oxygen fraction are as good as each other here, and the one written
     * first is taken.
     */
    fun richestAt(ambient: Double): String? = mixes.entries
        .filter { it.key in choosable && it.value.fractionO2 * ambient <= mostOxygenOf(it.key) }
        .maxByOrNull { it.value.fractionO2 }
        ?.key

    /**
     * Whether an ascent breathing [breathed] should move to [richest] on arriving at [ambient] bar.
     *
     * Only for a richer mix, or to leave one already over its own limit. Otherwise a run the user
     * switched to a bailout richer than anything the ascent may choose would be switched straight
     * back to a leaner mix.
     */
    fun worthSwitching(breathed: String, richest: String, ambient: Double): Boolean {
        val now = mixes[breathed] ?: return true
        val next = mixes[richest] ?: return false
        return next.fractionO2 > now.fractionO2 || now.fractionO2 * ambient > mostOxygenOf(breathed)
    }
}

/** Fill is what a cylinder was filled to, in bar of gauge pressure, and the litres it holds. */
internal class Fill(val gauge: Double, val volume: Double)

/**
 * What [run] breathes from, or null where nothing says.
 *
 * The switches say which source, and a run with no switches breathes its only one, which is how an
 * ordinary single-cylinder dive is written. A switch naming a source the run does not have is
 * passed over rather than followed.
 */
internal fun breathedBy(run: Run): Breathing? {
    if (run.sources.isEmpty()) return null
    val mixes = run.sources.mapValues { (_, source) -> source.gas }
    val rates = run.sources.mapNotNull { (key, source) -> source.sac?.let { key to it } }.toMap()
    val fills = run.sources.mapNotNull { (key, source) ->
        val gauge = source.fill
        val volume = source.volume
        if (gauge == null || volume == null || volume <= 0) null else key to Fill(gauge, volume)
    }.toMap()
    val most = run.sources.mapValues { (_, source) -> source.mostOxygen }
    val least = run.sources.mapValues { (_, source) -> source.leastOxygen }
    val choosable = run.sources.filterValues { it.ascentMayChoose }.keys
    val written = run.switches.filter { (_, key) -> key in run.sources }
    if (written.isEmpty()) {
        val only = run.sources.keys.singleOrNull() ?: return null
        return Breathing(mixes, listOf(0 to only), rates, fills, most, choosable, least)
    }
    return Breathing(mixes, written, rates, fills, most, choosable, least)
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
    return carriedAcross(earlier, interval, surface, seen + profile)
}

/**
 * What [earlier] leaves in the tissues and on the oxygen clocks after [interval] seconds breathing
 * air at [surface] bar, or why it cannot be worked out. [seen] is the chain behind it so far.
 *
 * A saved plan's evaluation and the planner's both come here, so a plan follows an earlier run the
 * same way whether it is typed or saved.
 */
private fun carriedAcross(earlier: Item, interval: Double, surface: Double, seen: Set<Item>): Carried =
    when (val ran = evaluated(earlier, seen)) {
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

/**
 * Residual is what an earlier run leaves in the tissues and on the oxygen clocks when a later one
 * begins, or why that cannot be worked out.
 */
sealed class Residual {

    /** What a run following it starts from: its `carried` and its `oxygenCarried`. */
    class Done(val tissues: Tissues, val oxygen: OxygenClock) : Residual()

    data class Refused(val reason: String) : Residual()
}

/**
 * What [earlier] leaves after [intervalSeconds] on the surface breathing air at [surface] bar, for
 * a run that belongs to no dive yet.
 *
 * [earlier] is evaluated as it would be anywhere, the chain behind it included, and the interval
 * is spent as a saved plan's is. `LOGIC-37`.
 */
fun residualAfter(earlier: Item, intervalSeconds: Double, surface: Double = SEA_LEVEL): Residual {
    require(intervalSeconds >= 0) {
        "a surface interval should be 0 seconds or more, but was $intervalSeconds"
    }
    return when (val carried = carriedAcross(earlier, intervalSeconds, surface, emptySet())) {
        is Carried.From -> Residual.Done(carried.tissues, carried.oxygen)
        is Carried.Refused -> Residual.Refused(carried.reason)
    }
}

/**
 * What a run that came to [ran] leaves after [intervalSeconds] on the surface breathing air at
 * [surface] bar, for a later run that belongs to no dive either.
 *
 * The same crossing as a saved plan's, for a run that was never saved: a chained case of a plan
 * file comes here, its earlier case already evaluated, so nothing is evaluated twice. `LOGIC-43`.
 */
fun residualAfter(ran: Evaluated.Done, surface: Double, intervalSeconds: Double): Residual.Done {
    require(intervalSeconds >= 0) {
        "a surface interval should be 0 seconds or more, but was $intervalSeconds"
    }
    return Residual.Done(
        ran.surfacing.breathing(Gas.AIR, surface, surface, intervalSeconds),
        // The clock runs backwards on the surface, which is what a surface interval is for.
        ran.oxygen.breathing(
            surface * Gas.AIR.fractionO2,
            surface * Gas.AIR.fractionO2,
            intervalSeconds,
        ),
    )
}

/** The dive a profile belongs to, or null where it belongs to nothing. */
private fun owner(profile: Item): Item? = (profile as? OwnedItem)?.parent

/** A series from times and values that were worked out together, and so are the same length. */
private fun seriesOf(seconds: List<Int>, values: List<Double>): Series =
    Series(seconds.toIntArray(), values.map { Element.Usable(it as Any) })

/**
 * Seconds into a run as a reader counts them, minutes and seconds: `24:00`.
 *
 * Not internal: the planner, `LOGIC-43`, reads a clock the same way from the `ui` module, and a
 * second copy of six characters is not worth keeping apart.
 */
fun clockOf(second: Int): String =
    "${second / 60}:${(second % 60).toString().padStart(2, '0')}"

/**
 * A depth as a finding says it, to a tenth of a metre, which is as fine as anyone reads one, and
 * without a tenth where there is none: `9 m`, `8.4 m`.
 */
private fun metres(depth: Double): String {
    val tenths = (depth * 10).toLong()
    return if (tenths % 10 == 0L) "${tenths / 10} m" else "${tenths / 10}.${tenths % 10} m"
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

/**
 * The least oxygen a mix is breathed at, in bar, below which a hypoxic mix may not keep a diver
 * conscious.
 *
 * The user's choice, and the cautious end of what agencies teach: 0.16 is the figure most often
 * given for a diver at work, and less is tolerated at rest. A form lets it be typed over.
 */
const val LEAST_OXYGEN = 0.18

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

/** How long a stop made only to switch gas is held, in seconds. */
private const val SWITCH_SECONDS = 60

/** How long an ascent may take before it is called one that does not come up. */
private const val LONGEST_ASCENT = 24 * 60 * 60
