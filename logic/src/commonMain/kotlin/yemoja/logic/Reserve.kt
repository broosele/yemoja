package yemoja.logic

import yemoja.data.Element

/*
 * The gas a plan keeps back for a way up when something goes wrong. See ../../../../../doc.md —
 * `LOGIC-40`.
 */

/**
 * Reserve is the gas a plan must still hold at its end, for one scenario going wrong at its worst
 * moment, or why that cannot be worked out.
 */
sealed class Reserve {

    /**
     * Done is what each source must still hold when the run ends, for the sources the scenario
     * needs something from.
     *
     * Immutable.
     */
    class Done(
        /** By source key, each with the moment that sets it. A source needing nothing is absent. */
        val kept: Map<String, Kept>,
        /** Whether every source in [kept] has a size and a fill, so that its gauge can be judged. */
        val judged: Boolean,
        /**
         * The way up in trouble from the moment asking most of any source, each a second and a
         * depth, the time held at depth first; empty where no source keeps anything.
         */
        val escape: List<Pair<Int, Double>> = emptyList(),
        /**
         * The deepest moment no source left may be breathed within its own oxygen limit, so the way
         * up is costed on the leanest of them anyway, or null where every moment has one.
         */
        val beyond: Beyond? = null,
    ) : Reserve()

    /** Refused is nothing worked out, why, and the source it is about where it is about one. */
    class Refused(
        val reason: String,
        val source: String? = null,
        /**
         * The way up in trouble where it could be worked out though its gas could not, from the
         * moment whose way up is longest, and empty otherwise.
         */
        val escape: List<Pair<Int, Double>> = emptyList(),
    ) : Reserve()
}

/**
 * Kept is what one source must still hold when a run ends, and the moment that asks it.
 *
 * **It is extra gas, measured at the end.** At [second] the way up in trouble takes [needed] litres
 * from the source, and the plan itself would still have breathed some of that from there to the
 * surface. What it would not have breathed is [litres], and a source surfacing with at least that
 * much held enough at every moment. A plan's gauge at any moment is its end pressure plus what it
 * breathes after, which is why the one comparison at the end does. `LOGIC-40`.
 *
 * Immutable.
 */
class Kept(
    /** Litres at the surface to keep at the end. */
    val litres: Double,
    /** The same on the source's gauge, in bar, or null where nobody said how big it is. */
    val bar: Double?,
    /** What the plan leaves on the source's gauge at the end, in bar, or null where it has no gauge. */
    val end: Double?,
    /** Whether the plan surfaces with less than [bar]. Never true where [bar] or [end] is unknown. */
    val short: Boolean,
    /** The moment that asks the most extra of this source, in seconds from the start. */
    val second: Int,
    /** How deep the run is at [second], in metres. */
    val metres: Double,
    /** How deep the way up from [second] is costed to, in metres: nought for the surface. */
    val upTo: Double,
    /** Litres the way up from [second] takes from the source, before what the plan breathes is taken off. */
    val needed: Double,
)

/**
 * What [run] must still hold at its end to reach the surface if the sources in [lost] fail at its
 * worst moment, each remaining source breathed at its own `sac`.
 *
 * **Every moment is tried.** At each point of the run the lost sources are gone, and the way up is
 * worked out from the tissues at that moment as [completeAscent] works one out: rising at
 * [metresAMinute], taking the last stop at [lastStop], and holding the run's safety stop. A source's
 * worst moment is the one whose way up takes most from it beyond what the plan breathes from it
 * after, as [Kept] says.
 *
 * **A bailout is open to that ascent's choice**, being carried for exactly this. Where the source
 * breathed at the moment is lost, the way up starts on the richest remaining source its own limit
 * allows at that depth, or the leanest where none is allowed.
 *
 * [run] should be the whole dive with its ascent, since losing a deco gas just before switching to
 * it can be the worst moment of all. The way up begins after [problemSolvingSeconds] at the
 * moment's depth, as [sharedGasReserve]'s does. `LOGIC-40`.
 */
fun lostGasReserve(
    run: Run,
    lost: Set<String>,
    metresAMinute: Double,
    lastStop: Double,
    problemSolvingSeconds: Int = 0,
    switchStops: Boolean = false,
): Reserve {
    checkAscent(metresAMinute, lastStop, problemSolvingSeconds)
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    val kept = run.sources.keys - lost
    if (kept.isEmpty()) return Reserve.Refused("At least one gas should remain")
    val emergency = breathing.choosing(kept)
    val sampled = run.sampledAtLimits()
    var beyond: Beyond? = null
    val reserve = reserveOver(sampled, breathing) { index, second, metres, tissues, anchor ->
        val ambient = ambientAt(metres, run.density, run.surface)
        val breathed = breathing.keyAt(second).takeIf { it in kept }
            ?: emergency.richestAt(ambient)
            ?: kept.minBy { breathing.mixes.getValue(it).fractionO2 }.also { leanest ->
                beyond = deeper(beyond, Beyond(second, metres, leanest, breathing.mixes.getValue(leanest).fractionO2 * ambient, breathing.mostOxygenOf(leanest)))
            }
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            anchor,
            breathed,
            emergency,
            sampled,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds, switchStops),
            factor = 1.0,
            handoff = 0.0,
        )
    }
    return withBeyond(reserve, beyond)
}

/**
 * What [run] must still hold at its end for a buddy who has lost their bottom gas at its worst moment: the two
 * of them breathe from the source breathed at that moment until a source in [deco] may be breathed,
 * each at their own `sac` times [stressFactor].
 *
 * **The buddy breathes at the same rate, carries the same deco gas, and has none of the bailouts.**
 * So the gas is costed at twice the diver's own rate times the stress, and the sharing ends where
 * the way up passes the deepest depth a deco gas may be breathed at, each diver switching to their
 * own there. That is part-way along a rise as often as at a stop: a way up owing no stop rises
 * straight through it. The way up is the model's, stops deeper than that depth included, and it
 * rises at [metresAMinute] with the last stop at [lastStop]. With no deco gas at all the two share
 * to the surface, safety stop included.
 *
 * **The two stay where they are for [problemSolvingSeconds] first**, sharing already: finding each
 * other and getting the gas going takes time at the depth it happened, and it is the dearest gas
 * of the whole way up.
 *
 * A moment already within reach of a deco gas costs nothing, the buddy switching at once. Every
 * moment is tried, as [lostGasReserve] tries them. `LOGIC-40`.
 */
fun sharedGasReserve(
    run: Run,
    deco: Set<String>,
    stressFactor: Double,
    metresAMinute: Double,
    lastStop: Double,
    problemSolvingSeconds: Int = 0,
    switchStops: Boolean = false,
): Reserve {
    require(stressFactor > 0) { "a stress factor should be more than nought, but was $stressFactor" }
    checkAscent(metresAMinute, lastStop, problemSolvingSeconds)
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    // Where each diver can go on to their own deco gas, and the surface where there is none.
    val handoff = deco.mapNotNull { key ->
        run.sources[key]?.let { maximumOperatingDepth(it.gas, it.mostOxygen, run.density, run.surface) }
    }.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val sampled = run.sampledAtLimits()
    return reserveOver(sampled, breathing) { index, second, metres, tissues, anchor ->
        if (metres <= handoff) return@reserveOver Cost.Litres(emptyMap(), metres)
        val shared = breathing.keyAt(second)
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            anchor,
            shared,
            breathing.choosing(setOf(shared)),
            sampled,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds, switchStops),
            factor = SHARING * stressFactor,
            handoff = handoff,
        )
    }
}

/**
 * What a rebreather [run] must still hold at its end, in its bailout cylinders, to reach the surface
 * on open circuit if the loop fails at its worst moment. `LOGIC-46`.
 *
 * **Only the bailouts are breathed**: the sources an ascent may not choose by itself, which is what
 * a plan's *bailout* role makes them. The way up starts on the richest of them its own limit allows
 * at the moment's depth, or the leanest where none is allowed, switches among them as an open-circuit
 * ascent does, and is breathed at each one's own `sac`. The tissues up to the moment are whatever the
 * plan breathed. Every moment on the loop is tried, as [lostGasReserve] tries them, the moment it is
 * left included; a moment off it costs nothing, there being no loop to lose.
 *
 * **The loop fails with a CO₂ hit.** The [co2HitSeconds] are spent at that depth on the bailout,
 * breathed at [co2HitFactor] times its `sac` and loading the tissues as they go. The way up after
 * them is at the usual rate.
 */
fun bailoutReserve(
    run: Run,
    metresAMinute: Double,
    lastStop: Double,
    co2HitSeconds: Int = 0,
    co2HitFactor: Double = 1.0,
    switchStops: Boolean = false,
): Reserve {
    require(co2HitFactor >= 1) { "a CO₂ hit factor should be 1 or more, but was $co2HitFactor" }
    checkAscent(metresAMinute, lastStop, co2HitSeconds)
    if (run.closedCircuit == null) return Reserve.Refused("A bailout is from a rebreather, and this dive is on open circuit")
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    val drawn = run.closedCircuit.rich + run.closedCircuit.diluent
    val bailouts = run.sources.filterKeys { it !in drawn }.filterValues { !it.ascentMayChoose }.keys
    if (bailouts.isEmpty()) return Reserve.Refused("No bailout: give a cylinder the bailout role")
    val sampled = run.sampledAtLimits()
    val open = sampled.onOpenCircuit()
    val escape = breathedBy(open)?.choosing(bailouts) ?: return Reserve.Refused("nothing says what is breathed")
    var beyond: Beyond? = null
    val reserve = reserveOver(sampled, breathing) { index, second, metres, tissues, anchor ->
        // Off the loop there is no loop to lose.
        if (!breathing.onLoopAt(second)) return@reserveOver Cost.Litres(emptyMap(), metres)
        val ambient = ambientAt(metres, run.density, run.surface)
        val breathed = escape.richestAt(ambient) ?: bailouts.minBy { escape.mixes.getValue(it).fractionO2 }.also { leanest ->
            beyond = deeper(beyond, Beyond(second, metres, leanest, escape.mixes.getValue(leanest).fractionO2 * ambient, escape.mostOxygenOf(leanest)))
        }
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            anchor,
            breathed,
            escape,
            open,
            Ascending(metresAMinute, lastStop, co2HitSeconds, switchStops),
            factor = 1.0,
            handoff = 0.0,
            heldFactor = co2HitFactor,
        )
    }
    return withBeyond(reserve, beyond)
}

/** [this] with its rebreather taken away and everything else kept, for a way up on open circuit. */
private fun Run.onOpenCircuit(): Run = Run(
    depth = depth,
    sources = sources,
    gradientFactorLow = gradientFactorLow,
    gradientFactorHigh = gradientFactorHigh,
    switches = switches,
    density = density,
    surface = surface,
    carried = carried,
    oxygenCarried = oxygenCarried,
    safetyStop = safetyStop,
    ascentRate = ascentRate,
    lastStop = lastStop,
    mostNarcoticDepth = mostNarcoticDepth,
    oxygenNarcotic = oxygenNarcotic,
)

/**
 * Beyond is a moment a reserve's way up has no source within its oxygen limit: when, how deep, the
 * leanest source it is costed on anyway, and the oxygen it breathes against what it is held to, in
 * bar.
 *
 * Immutable.
 */
class Beyond(val second: Int, val metres: Double, val source: String, val oxygen: Double, val most: Double)

/** Whichever of [held] and [found] breathes further past its limit, [held] where they are equal. */
private fun deeper(held: Beyond?, found: Beyond): Beyond =
    if (held == null || found.oxygen - found.most > held.oxygen - held.most) found else held

/** How long [path] takes, in seconds, from its first point to its last. */
private fun lengthOf(path: List<Pair<Int, Double>>): Int = path.last().first - path.first().first

/** [reserve] with [beyond] said, where it was worked out. */
private fun withBeyond(reserve: Reserve, beyond: Beyond?): Reserve =
    if (reserve is Reserve.Done) Reserve.Done(reserve.kept, reserve.judged, reserve.escape, beyond) else reserve

/**
 * [this] with a point added on each side of every second its depth crosses the limits of a source:
 * the deepest depth its oxygen allows and the shallowest. `LOGIC-40`.
 *
 * A way up in trouble starts on what the moment's depth allows, so which source it starts on
 * changes only at those depths. Tried only at the run's own points, a rise from thirty metres to a
 * stop at nine passes the depth a deco gas becomes breathable untried, and that can be the
 * dearest moment of all for that gas. The points lie on the run's own straight lines, so the
 * tissues and the gas breathed come out as they were.
 */
internal fun Run.sampledAtLimits(): Run {
    val limits = sources.values.flatMap { source ->
        listOfNotNull(
            maximumOperatingDepth(source.gas, source.mostOxygen, density, surface),
            minimumOperatingDepth(source.gas, source.leastOxygen, density, surface),
        )
    }.distinct()
    val points = ArrayList<Pair<Int, Double>>()
    for ((index, point) in depth.withIndex()) {
        if (index > 0) {
            val (was, from) = depth[index - 1]
            val (second, metres) = point
            val crossings = mutableSetOf<Int>()
            for (limit in limits) {
                if (metres == from || limit <= minOf(from, metres) || limit >= maxOf(from, metres)) continue
                val at = was + (limit - from) / (metres - from) * (second - was)
                // Both seconds either side, and the ones around a crossing that falls on a second,
                // so one point is shallower than the limit whichever way the run goes.
                val below = kotlin.math.floor(at).toInt()
                val above = kotlin.math.ceil(at).toInt()
                val around = if (below == above) listOf(below - 1, below, below + 1) else listOf(below, above)
                for (whole in around) {
                    if (whole > was && whole < second) crossings += whole
                }
            }
            for (whole in crossings.sorted()) points += whole to from + (metres - from) * (whole - was) / (second - was)
        }
        points += point
    }
    if (points.size == depth.size) return this
    return Run(
        depth = points,
        sources = sources,
        gradientFactorLow = gradientFactorLow,
        gradientFactorHigh = gradientFactorHigh,
        switches = switches,
        density = density,
        surface = surface,
        carried = carried,
        oxygenCarried = oxygenCarried,
        safetyStop = safetyStop,
        ascentRate = ascentRate,
        lastStop = lastStop,
        mostNarcoticDepth = mostNarcoticDepth,
        oxygenNarcotic = oxygenNarcotic,
        closedCircuit = closedCircuit,
    )
}

/** Ascending is how a way up in trouble is made: how fast, how shallow the last stop, how long first. */
private class Ascending(
    val metresAMinute: Double,
    val lastStop: Double,
    val problemSolvingSeconds: Int,
    /** Whether the way up stops to switch gas where no stop is owed, as the plan's own does. */
    val switchStops: Boolean,
)

/**
 * What the way up from [metres] at [second] costs when it begins with the problem-solving time there.
 *
 * The time is breathed from [breathed] at [factor] times its rate and loads the tissues as it goes,
 * so a minute more at forty metres can owe a stop more. The climb is worked out from where it leaves
 * them, choosing among [choosing]'s sources, and costed to [handoff]. [index] is the moment's place
 * in [run], which says how much of a safety stop is already held; the time itself counts towards
 * one where the problem happens at its depth. [anchor] is where the run's gradient factors are
 * anchored so far, which the climb keeps.
 */
private fun heldThenClimbed(
    second: Int,
    metres: Double,
    index: Int,
    tissues: Tissues,
    anchor: Double,
    breathed: String,
    choosing: Breathing,
    run: Run,
    ascending: Ascending,
    factor: Double,
    handoff: Double,
    /** Times its usual rate the time held is breathed at, where it differs from the climb's [factor]. */
    heldFactor: Double = factor,
): Cost {
    val ambient = ambientAt(metres, run.density, run.surface)
    val held = if (metres > 0) ascending.problemSolvingSeconds else 0
    val loaded = if (held > 0) {
        tissues.breathing(choosing.inspirationOf(breathed, second.toDouble()), ambient, ambient, held.toDouble())
    } else {
        tissues
    }
    val before = run.depth.subList(0, index + 1) + if (held > 0) listOf(second + held to metres) else emptyList()
    val climbed = climbed(
        From(loaded, second + held, metres, breathed, anchor),
        choosing,
        run,
        ascending.metresAMinute,
        ascending.lastStop,
        before,
        ascending.switchStops,
    ) ?: return Cost.Stuck
    val path = listOf(second to metres) + (if (held > 0) listOf(second + held to metres) else emptyList()) + climbed.points
    val up = when (val cost = costOf((second + held) to metres, breathed, climbed, run, factor, handoff)) {
        is Cost.Litres -> Cost.Litres(cost.litres, cost.upTo, path)
        is Cost.Unknown -> return Cost.Unknown(cost.source, path)
        Cost.Stuck -> return cost
    }
    if (held == 0) return up
    val rate = run.sources.getValue(breathed).sac ?: return Cost.Unknown(breathed, path)
    val litres = LinkedHashMap(up.litres)
    litres[breathed] = (litres[breathed] ?: 0.0) + rate * heldFactor * held / SECONDS_IN_MINUTE * ambient
    return Cost.Litres(litres, up.upTo, path)
}

/** How many divers breathe from one cylinder while it is shared. */
private const val SHARING = 2.0

private fun checkAscent(metresAMinute: Double, lastStop: Double, problemSolvingSeconds: Int) {
    require(problemSolvingSeconds >= 0) {
        "problem-solving time should be 0 seconds or more, but was $problemSolvingSeconds"
    }
    require(metresAMinute > 0) {
        "an ascent rate should be more than nought, but was $metresAMinute"
    }
    require(lastStop >= 0) { "a last stop should be 0 or deeper, but was $lastStop" }
}

/**
 * The reserve [run] needs, trying [cost] at every point and keeping, for each source, the moment
 * that asks most beyond what the plan breathes from there.
 *
 * [cost] is given the point's place, its second and depth, the tissues there and the depth the
 * gradient factors are anchored at so far, and says what a way up from that moment takes from
 * each source.
 */
private fun reserveOver(
    run: Run,
    breathing: Breathing,
    cost: (index: Int, second: Int, metres: Double, tissues: Tissues, anchor: Double) -> Cost,
): Reserve {
    val evaluated = when (val answer = evaluate(run)) {
        is Evaluated.Refused -> return Reserve.Refused(answer.reason)
        is Evaluated.Done -> answer
    }
    val after = usedAfter(run, breathing)
    var tissues = run.carried ?: Tissues.saturated(run.surface)
    var anchor = 0.0
    val kept = LinkedHashMap<String, Moment>()
    // The first source with no rate, and the longest way up found while it went uncosted.
    var unknown: String? = null
    var longest: List<Pair<Int, Double>>? = null
    for ((index, point) in run.depth.withIndex()) {
        val (second, metres) = point
        if (index > 0) {
            val (was, from) = run.depth[index - 1]
            tissues = breathedOver(tissues, breathing, was, from, second, metres, run)
        }
        anchor = firstStopAfter(tissues, anchor, run.model, run.surface)
        val litres = when (val answer = cost(index, second, metres, tissues, anchor)) {
            is Cost.Litres -> answer
            // Every moment is still tried, for the way up a missing rate leaves undrawn otherwise.
            is Cost.Unknown -> {
                unknown = unknown ?: answer.source
                if (answer.path.size > 1 && (longest == null || lengthOf(answer.path) > lengthOf(longest!!))) longest = answer.path
                continue
            }
            Cost.Stuck -> return Reserve.Refused(
                "No way up from ${clockOf(second)} within 24 hours",
            )
        }
        for ((key, needed) in litres.litres) {
            val extra = needed - (after[index][key] ?: 0.0)
            if (extra <= TIE) continue
            val most = kept[key]?.extra
            // A tie goes to the later moment, being nearer the end the reserve is held at.
            if (most == null || extra >= most - TIE) {
                kept[key] = Moment(second, metres, litres.upTo, needed, extra, litres.path)
            }
        }
    }
    if (run.depth.isEmpty()) {
        return Reserve.Refused("this recording holds no depths, so nothing can be worked out from it")
    }
    unknown?.let { return Reserve.Refused("Cannot be calculated: SAC missing", it, longest.orEmpty()) }
    var judged = true
    val answer = kept.mapValues { (key, moment) ->
        val volume = run.sources.getValue(key).volume?.takeIf { it > 0 }
        val end = evaluated.pressures[key]?.let { series ->
            (series.valueAt(series.size - 1) as? Element.Usable)?.value as? Double
        }
        if (volume == null || end == null) judged = false
        val bar = volume?.let { moment.extra / it }
        Kept(
            litres = moment.extra,
            bar = bar,
            end = end,
            short = bar != null && end != null && end < bar,
            second = moment.second,
            metres = moment.metres,
            upTo = moment.upTo,
            needed = moment.needed,
        )
    }
    return Reserve.Done(answer, judged, kept.values.maxByOrNull { it.extra }?.path.orEmpty())
}

/**
 * The litres the plan itself breathes from each source after each point of [run], as `evaluate`
 * counts them: each stretch at its source's rate and the mean of the pressures at its ends.
 */
private fun usedAfter(run: Run, breathing: Breathing): List<Map<String, Double>> {
    val stretches = run.depth.zipWithNext().map { (from, to) ->
        val key = breathing.keyAt(from.first)
        val rate = breathing.rates[key]
        val litres = if (rate == null) 0.0 else {
            val minutes = (to.first - from.first) / SECONDS_IN_MINUTE
            rate * minutes * (ambientAt(from.second, run.density, run.surface) + ambientAt(to.second, run.density, run.surface)) / 2
        }
        key to litres
    }
    val after = ArrayList<Map<String, Double>>(run.depth.size)
    var running = HashMap<String, Double>()
    after += running.toMap()
    for ((key, litres) in stretches.asReversed()) {
        running = HashMap(running).apply { this[key] = (this[key] ?: 0.0) + litres }
        after += running.toMap()
    }
    return after.asReversed()
}

/**
 * Moment is one point of a run, what a way up from it takes from one source, and how much of that
 * the plan would not have breathed anyway.
 */
private class Moment(
    val second: Int,
    val metres: Double,
    val upTo: Double,
    val needed: Double,
    val extra: Double,
    /** The way up from it. */
    val path: List<Pair<Int, Double>>,
)

/** Cost is what a way up takes from each source, or why it cannot be counted. */
private sealed class Cost {

    /** The litres each source gives up, and how deep the way up was costed to. */
    class Litres(
        val litres: Map<String, Double>,
        val upTo: Double,
        /** The way up as a second and a depth each, the time held first, and empty where there is none. */
        val path: List<Pair<Int, Double>> = emptyList(),
    ) : Cost()

    /** A source breathed on the way that nobody gave a rate for. */
    class Unknown(
        val source: String,
        /** The way up that could not be costed, which is still drawn. */
        val path: List<Pair<Int, Double>> = emptyList(),
    ) : Cost()

    /** A way up that does not reach the surface within a day. */
    data object Stuck : Cost()
}

/**
 * What [climbed] takes from each source, leaving [start] on [breathed], at [factor] times each
 * source's own rate, up to the depth [handoff] and no further.
 *
 * A rise that passes [handoff] is costed to where it crosses, read as a straight line as every
 * stretch of a run is. A switch at a second is made on arriving there, so the stretch that ends at
 * it is still breathed from the source before.
 */
private fun costOf(
    start: Pair<Int, Double>,
    breathed: String,
    climbed: Climbed,
    run: Run,
    factor: Double,
    handoff: Double,
): Cost {
    val litres = LinkedHashMap<String, Double>()
    var key = breathed
    var previous = start
    for (next in climbed.points) {
        if (previous.second <= handoff) break
        val point = if (next.second >= handoff) {
            next
        } else {
            val share = (previous.second - handoff) / (previous.second - next.second)
            (previous.first + share * (next.first - previous.first)).toInt() to handoff
        }
        val rate = run.sources.getValue(key).sac ?: return Cost.Unknown(key)
        val minutes = (point.first - previous.first) / SECONDS_IN_MINUTE
        val mean = (
            ambientAt(previous.second, run.density, run.surface) +
                ambientAt(point.second, run.density, run.surface)
            ) / 2
        litres[key] = (litres[key] ?: 0.0) + rate * factor * minutes * mean
        previous = point
        climbed.switches.lastOrNull { it.first == point.first }?.let { key = it.second }
    }
    return Cost.Litres(litres, previous.second.coerceAtLeast(handoff))
}

private const val SECONDS_IN_MINUTE = 60.0

/** How close two costs in litres are to count as the same. */
private const val TIE = 1e-6
