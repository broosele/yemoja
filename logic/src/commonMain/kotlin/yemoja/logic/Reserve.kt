package yemoja.logic

import yemoja.data.Element

/*
 * The gas a plan keeps back for a way up when something goes wrong. See ../../../../../doc.md —
 * `LOGIC-40`.
 */

/**
 * Reserve is the gas a plan must keep back to reach safety in one scenario at the worst moment, or
 * why that cannot be worked out.
 */
sealed class Reserve {

    /**
     * Done is the reserve at the worst moment, and whether the plan keeps it throughout.
     *
     * Immutable.
     */
    class Done(
        /** The moment the scenario costs the most gas, in seconds from the start. */
        val worst: Int,
        /** How deep the run is at [worst], in metres. */
        val worstMetres: Double,
        /** How deep the way up from [worst] is costed to, in metres: nought for the surface. */
        val upTo: Double,
        /** The litres at the surface each source gives up on the way up from [worst]. */
        val needed: Map<String, Double>,
        /** The same on each cylinder's gauge, in bar, for the sources that say how big they are. */
        val reserve: Map<String, Double>,
        /**
         * The first moment a source holds less than the way up from there needs, or null where every
         * moment has enough.
         *
         * Only a source with a gauge can be judged, so null says nothing about one without.
         * [judged] says whether any was left out.
         */
        val shortfall: Shortfall?,
        /** Whether every source a way up breathes from has a size and a fill to judge it by. */
        val judged: Boolean,
    ) : Reserve()

    /** Refused is nothing worked out, why, and the source it is about where it is about one. */
    class Refused(val reason: String, val source: String? = null) : Reserve()
}

/**
 * Shortfall is a moment a source holds less than the way up from there would need.
 *
 * Both pressures are in bar on that source's gauge, and [upTo] is how deep that way up is costed
 * to, in metres.
 */
class Shortfall(val second: Int, val source: String, val left: Double, val needed: Double, val upTo: Double)

/**
 * What [run] must keep back to reach the surface if the sources in [lost] fail at its worst moment,
 * each remaining source breathed at its own `sac`.
 *
 * **Every moment is tried.** At each point of the run the lost sources are gone, and the way up is
 * worked out from the tissues at that moment as [completeAscent] works one out: rising at
 * [metresAMinute], taking the last stop at [lastStop], and holding the run's safety stop. The worst
 * moment is the one whose way up costs the most gas altogether.
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
): Reserve {
    checkAscent(metresAMinute, lastStop, problemSolvingSeconds)
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    val kept = run.sources.keys - lost
    if (kept.isEmpty()) return Reserve.Refused("At least one gas should remain")
    val emergency = breathing.choosing(kept)
    return reserveOver(run, breathing) { index, second, metres, tissues ->
        val ambient = ambientAt(metres, run.density, run.surface)
        val breathed = breathing.keyAt(second).takeIf { it in kept }
            ?: emergency.richestAt(ambient)
            ?: kept.minBy { breathing.mixes.getValue(it).fractionO2 }
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            breathed,
            emergency,
            run,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds),
            factor = 1.0,
            handoff = 0.0,
        )
    }
}

/**
 * What [run] must keep back for a buddy who has lost their bottom gas at its worst moment: the two
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
): Reserve {
    require(stressFactor > 0) { "a stress factor should be more than nought, but was $stressFactor" }
    checkAscent(metresAMinute, lastStop, problemSolvingSeconds)
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    // Where each diver can go on to their own deco gas, and the surface where there is none.
    val handoff = deco.mapNotNull { key ->
        run.sources[key]?.let { maximumOperatingDepth(it.gas, it.mostOxygen, run.density, run.surface) }
    }.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    return reserveOver(run, breathing) { index, second, metres, tissues ->
        if (metres <= handoff) return@reserveOver Cost.Litres(emptyMap(), metres)
        val shared = breathing.keyAt(second)
        heldThenClimbed(
            second,
            metres,
            index,
            tissues,
            shared,
            breathing.choosing(setOf(shared)),
            run,
            Ascending(metresAMinute, lastStop, problemSolvingSeconds),
            factor = SHARING * stressFactor,
            handoff = handoff,
        )
    }
}

/** Ascending is how a way up in trouble is made: how fast, how shallow the last stop, how long first. */
private class Ascending(val metresAMinute: Double, val lastStop: Double, val problemSolvingSeconds: Int)

/**
 * What the way up from [metres] at [second] costs when it begins with the problem-solving time there.
 *
 * The time is breathed from [breathed] at [factor] times its rate and loads the tissues as it goes,
 * so a minute more at forty metres can owe a stop more. The climb is worked out from where it leaves
 * them, choosing among [choosing]'s sources, and costed to [handoff]. [index] is the moment's place
 * in [run], which says how much of a safety stop is already held; the time itself counts towards
 * one where the problem happens at its depth.
 */
private fun heldThenClimbed(
    second: Int,
    metres: Double,
    index: Int,
    tissues: Tissues,
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
        tissues.breathing(choosing.mixes.getValue(breathed), ambient, ambient, held.toDouble())
    } else {
        tissues
    }
    val before = run.depth.subList(0, index + 1) + if (held > 0) listOf(second + held to metres) else emptyList()
    val climbed = climbed(
        From(loaded, second + held, metres, breathed),
        choosing,
        run,
        ascending.metresAMinute,
        ascending.lastStop,
        before,
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
 * The reserve [run] needs, trying [cost] at every point and keeping the worst.
 *
 * [cost] is given the point's place, its second and depth, and the tissues there, and says what a
 * way up from that moment takes from each source.
 */
private fun reserveOver(
    run: Run,
    breathing: Breathing,
    cost: (index: Int, second: Int, metres: Double, tissues: Tissues) -> Cost,
): Reserve {
    val evaluated = when (val answer = evaluate(run)) {
        is Evaluated.Refused -> return Reserve.Refused(answer.reason)
        is Evaluated.Done -> answer
    }
    var tissues = run.carried ?: Tissues.saturated(run.surface)
    var worst: Moment? = null
    var shortfall: Shortfall? = null
    var judged = true
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
        val litres = when (val answer = cost(index, second, metres, tissues)) {
            is Cost.Litres -> answer
            is Cost.Unknown -> return Reserve.Refused(
                "Cannot be calculated: SAC missing",
                answer.source,
            )
            Cost.Stuck -> return Reserve.Refused(
                "No way up from ${clockOf(second)} within 24 hours",
            )
        }
        // A tie goes to the later moment, since the cylinder holds least then: from anywhere on a
        // flat bottom the way up to a deco gas can cost the same.
        val total = litres.litres.values.sum()
        val most = worst?.needed?.values?.sum()
        if (most == null || total > most || (total > 0 && total >= most - TIE)) {
            worst = Moment(second, metres, litres.upTo, litres.litres)
        }
        for ((key, needed) in litres.litres) {
            val volume = run.sources.getValue(key).volume
            val gauge = evaluated.pressures[key]?.valueAt(index) as? Element.Usable
            if (volume == null || volume <= 0 || gauge == null) {
                if (needed > 0) judged = false
                continue
            }
            val left = gauge.value as Double
            if (shortfall == null && left < needed / volume) {
                shortfall = Shortfall(second, key, left, needed / volume, litres.upTo)
            }
        }
    }
    val at = worst ?: return Reserve.Refused("this run holds no depths")
    return Reserve.Done(
        worst = at.second,
        worstMetres = at.metres,
        upTo = at.upTo,
        needed = at.needed,
        reserve = at.needed.mapNotNull { (key, litres) ->
            run.sources.getValue(key).volume?.takeIf { it > 0 }?.let { key to litres / it }
        }.toMap(),
        shortfall = shortfall,
        judged = judged,
    )
}

/** Moment is one point of a run, and what a way up from it would cost each source. */
private class Moment(val second: Int, val metres: Double, val upTo: Double, val needed: Map<String, Double>)

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
