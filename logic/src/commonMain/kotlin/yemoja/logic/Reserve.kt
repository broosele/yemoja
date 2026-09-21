package yemoja.logic

import yemoja.data.Element

/*
 * The gas a plan keeps back for a way up when something goes wrong. See ../../../../../doc.md —
 * `LOGIC-40`.
 */

/**
 * Reserve is the gas a plan must keep back to reach the surface if some of its sources are lost at
 * the worst moment, or why that cannot be worked out.
 */
sealed class Reserve {

    /**
     * Done is the reserve at the worst moment, and whether the plan keeps it throughout.
     *
     * Immutable.
     */
    class Done(
        /** The moment losing the lost sources costs the most gas, in seconds from the start. */
        val worst: Int,
        /** How deep the run is at [worst], in metres. */
        val worstMetres: Double,
        /** The litres at the surface each remaining source gives up on the way up from [worst]. */
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
 * Both pressures are in bar on that source's gauge.
 */
class Shortfall(val second: Int, val source: String, val left: Double, val needed: Double)

/**
 * What [run] must keep back to reach the surface if the sources in [lost] fail at its worst moment,
 * each remaining source breathed at [panicFactor] times its own `sac`.
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
 * it can be the worst moment of all. `LOGIC-40`.
 */
fun gasReserve(
    run: Run,
    panicFactor: Double,
    lost: Set<String>,
    metresAMinute: Double,
    lastStop: Double,
): Reserve {
    require(panicFactor > 0) { "a panic factor should be more than nought, but was $panicFactor" }
    require(metresAMinute > 0) {
        "an ascent rate should be more than nought, but was $metresAMinute"
    }
    require(lastStop >= 0) { "a last stop should be 0 or deeper, but was $lastStop" }
    val evaluated = when (val answer = evaluate(run)) {
        is Evaluated.Refused -> return Reserve.Refused(answer.reason)
        is Evaluated.Done -> answer
    }
    val breathing = breathedBy(run) ?: return Reserve.Refused("nothing says what is breathed")
    val kept = run.sources.keys - lost
    if (kept.isEmpty()) return Reserve.Refused("every cylinder is lost, so nothing is left to breathe")
    val emergency = breathing.choosing(kept)

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
        val ambient = ambientAt(metres, run.density, run.surface)
        val breathed = breathing.keyAt(second).takeIf { it in kept }
            ?: emergency.richestAt(ambient)
            ?: kept.minBy { breathing.mixes.getValue(it).fractionO2 }
        val climbed = climbed(
            From(tissues, second, metres, breathed),
            emergency,
            run,
            metresAMinute,
            lastStop,
            run.depth.subList(0, index + 1),
        ) ?: return Reserve.Refused("the way up from ${clockOf(second)} does not reach the surface within a day")
        val needed = when (val cost = costOf(point, breathed, climbed, run, panicFactor)) {
            is Cost.Unknown -> return Reserve.Refused(
                "nothing says how fast this cylinder is breathed, so its reserve cannot be counted",
                cost.source,
            )
            is Cost.Litres -> cost.litres
        }
        if (worst == null || needed.values.sum() > worst.needed.values.sum()) {
            worst = Moment(second, metres, needed)
        }
        for ((key, litres) in needed) {
            val volume = run.sources.getValue(key).volume
            val gauge = evaluated.pressures[key]?.valueAt(index) as? Element.Usable
            if (volume == null || volume <= 0 || gauge == null) {
                if (litres > 0) judged = false
                continue
            }
            val left = gauge.value as Double
            if (shortfall == null && left < litres / volume) {
                shortfall = Shortfall(second, key, left, litres / volume)
            }
        }
    }
    val at = worst ?: return Reserve.Refused("this run holds no depths")
    return Reserve.Done(
        worst = at.second,
        worstMetres = at.metres,
        needed = at.needed,
        reserve = at.needed.mapNotNull { (key, litres) ->
            run.sources.getValue(key).volume?.takeIf { it > 0 }?.let { key to litres / it }
        }.toMap(),
        shortfall = shortfall,
        judged = judged,
    )
}

/** Moment is one point of a run, and what a way up from it would cost each source. */
private class Moment(val second: Int, val metres: Double, val needed: Map<String, Double>)

/** Cost is what a way up takes from each source, or the source nobody gave a rate for. */
private sealed class Cost {
    class Litres(val litres: Map<String, Double>) : Cost()
    class Unknown(val source: String) : Cost()
}

/**
 * What [climbed] takes from each source, leaving [start] on [breathed], at [panicFactor] times each
 * source's own rate.
 *
 * A switch at a second is made on arriving there, so the stretch that ends at it is still breathed
 * from the source before.
 */
private fun costOf(
    start: Pair<Int, Double>,
    breathed: String,
    climbed: Climbed,
    run: Run,
    panicFactor: Double,
): Cost {
    val litres = LinkedHashMap<String, Double>()
    var key = breathed
    var previous = start
    for (point in climbed.points) {
        val rate = run.sources.getValue(key).sac ?: return Cost.Unknown(key)
        val minutes = (point.first - previous.first) / SECONDS_IN_MINUTE
        val mean = (
            ambientAt(previous.second, run.density, run.surface) +
                ambientAt(point.second, run.density, run.surface)
            ) / 2
        litres[key] = (litres[key] ?: 0.0) + rate * panicFactor * minutes * mean
        previous = point
        climbed.switches.lastOrNull { it.first == point.first }?.let { key = it.second }
    }
    return Cost.Litres(litres)
}

private const val SECONDS_IN_MINUTE = 60.0
