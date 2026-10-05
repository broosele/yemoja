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
    ) : Reserve()

    /** Refused is nothing worked out, why, and the source it is about where it is about one. */
    class Refused(val reason: String, val source: String? = null) : Reserve()
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
    return reserveOver(run, breathing) { index, second, metres, tissues, anchor ->
        val ambient = ambientAt(metres, run.density, run.surface)
        val breathed = breathing.keyAt(second).takeIf { it in kept }
            ?: emergency.richestAt(ambient)
            ?: kept.minBy { breathing.mixes.getValue(it).fractionO2 }
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            anchor,
            breathed,
            emergency,
            run,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds, switchStops),
            factor = 1.0,
            handoff = 0.0,
        )
    }
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
    return reserveOver(run, breathing) { index, second, metres, tissues, anchor ->
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
            run,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds, switchStops),
            factor = SHARING * stressFactor,
            handoff = handoff,
        )
    }
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
    val up = costOf((second + held) to metres, breathed, climbed, run, factor, handoff)
    if (up !is Cost.Litres || held == 0) return up
    val rate = run.sources.getValue(breathed).sac ?: return Cost.Unknown(breathed)
    val litres = LinkedHashMap(up.litres)
    litres[breathed] = (litres[breathed] ?: 0.0) + rate * factor * held / SECONDS_IN_MINUTE * ambient
    return Cost.Litres(litres, up.upTo)
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
    for ((index, point) in run.depth.withIndex()) {
        val (second, metres) = point
        if (index > 0) {
            val (was, from) = run.depth[index - 1]
            tissues = breathedOver(tissues, breathing, was, from, second, metres, run)
        }
        anchor = firstStopAfter(tissues, anchor, run.model, run.surface)
        val litres = when (val answer = cost(index, second, metres, tissues, anchor)) {
            is Cost.Litres -> answer
            is Cost.Unknown -> return Reserve.Refused(
                "Cannot be calculated: SAC missing",
                answer.source,
            )
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
                kept[key] = Moment(second, metres, litres.upTo, needed, extra)
            }
        }
    }
    if (run.depth.isEmpty()) {
        return Reserve.Refused("this recording holds no depths, so nothing can be worked out from it")
    }
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
    return Reserve.Done(answer, judged)
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
private class Moment(val second: Int, val metres: Double, val upTo: Double, val needed: Double, val extra: Double)

/** Cost is what a way up takes from each source, or why it cannot be counted. */
private sealed class Cost {

    /** The litres each source gives up, and how deep the way up was costed to. */
    class Litres(val litres: Map<String, Double>, val upTo: Double) : Cost()

    /** A source breathed on the way that nobody gave a rate for. */
    class Unknown(val source: String) : Cost()

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
