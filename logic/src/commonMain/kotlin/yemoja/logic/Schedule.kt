package yemoja.logic

import yemoja.data.Element
import yemoja.data.Series

/*
 * A dive plan calculated: every line of it, and the figures it comes to.
 *
 * See ../../../../../../ui/api/doc.md — `API-2` and `API-7`.
 */

/**
 * Calculated is a plan asked for: the schedule, or why there is none.
 *
 * A refusal is a plan that cannot be read or cannot be held — a duration that is not a duration, a
 * cylinder with no mix, a ceiling the way up cannot reach. It says which in the words the form
 * says it in, so one description is refused one way however it arrived.
 */
sealed class Calculated {

    class Done(val schedule: Schedule) : Calculated()

    class Refused(val reason: String) : Calculated()
}

/**
 * Schedule is a dive plan calculated: every line of it, and the figures it comes to.
 *
 * **The whole dive, not the way up alone.** The lines a caller described and the ones the model
 * added are one list in one order, each saying which it is, so a comparison against a table can
 * read the stops without having to work out where the typed part ended.
 *
 * Immutable.
 */
class Schedule(
    /** Every line of the runtime, in order, the first of them leaving the surface. */
    val lines: List<Line>,
    /** Each depth held on the way up, deepest first, and how long it is held. */
    val stops: List<Stop>,
    /** Surface to surface, in seconds. */
    val runtimeSeconds: Int,
    /** How long is spent holding a depth on the way up, which is what a table's column adds to. */
    val stopSeconds: Int,
    /** The deepest depth held on the way up, in metres, or absent where nothing is held. */
    val deepestStop: Double?,
    val maxDepthMetres: Double,
    /** The central nervous system's clock at the end, as a percentage. */
    val cnsPercent: Double,
    /** Oxygen tolerance units taken, which is a count and not a percentage. */
    val otu: Double,
    /** How long to wait before flying, in seconds, or absent where two days would not be enough. */
    val noFlightSeconds: Double?,
    /** How long until the compartments settle, in seconds, or absent where two days would not do. */
    val desaturationSeconds: Double?,
    /** Litres at the surface taken from each cylinder, by its number: `1`, `2`. */
    val gasUsedLitres: Map<String, Double>,
    /** What the model has to say against the plan, earliest first. */
    val warnings: List<Warning>,
)

/**
 * Line is one part of a dive: where it goes, how long it takes, and what is breathed.
 *
 * Immutable.
 */
class Line(
    val fromMetres: Double,
    val toMetres: Double,
    /** Seconds from leaving the surface. */
    val beginsAt: Int,
    val seconds: Int,
    /** `down`, `up` or `stay`. */
    val direction: String,
    /** The cylinder by its number, as `gasUsedLitres` keys it. */
    val gas: String,
    /** Whether the model added the line, rather than a caller describing it. */
    val added: Boolean,
)

/** A depth held on the way up, and how long for. Immutable. */
class Stop(val metres: Double, val seconds: Int)

/** Something the model has to say about a plan: when, how serious, and what. Immutable. */
class Warning(val second: Int, val severity: Severity, val said: String)

/**
 * The plan [planned] describes, calculated.
 *
 * **Nothing is read and nothing is written.** No logbook is needed and none is touched: a plan is
 * arithmetic over what the description says. A plan that follows an earlier run is the one
 * exception and is not answered here, the run it follows being a dive in a logbook. `API-7`.
 */
fun calculated(planned: Planned): Calculated {
    val ready = when (val shaped = shapedOf(planned)) {
        is Shaped.Ready -> shaped
        is Shaped.Wrong -> return Calculated.Refused(shaped.reason)
        is Shaped.Waiting -> return Calculated.Refused("the plan has no lines")
    }
    val done = when (val worked = workedOf(ready)) {
        is Worked.Done -> worked
        is Worked.Refused -> return Calculated.Refused(worked.reason)
    }
    return Calculated.Done(scheduleOf(ready, done))
}

/** The schedule [done] came to, with the lines [ready] was asked for marked as the caller's. */
private fun scheduleOf(ready: Shaped.Ready, done: Worked.Done): Schedule {
    val typed = ready.legs.map { lineOf(it, added = false) }
    val added = done.tail.map { lineOf(it, added = true) }
    val lines = typed + added
    val stops = done.tail
        .filter { it.direction == Direction.STAY && it.to > 0 }
        .map { Stop(it.to, it.seconds) }
    return Schedule(
        lines = lines,
        stops = stops.sortedByDescending { it.metres },
        runtimeSeconds = lines.lastOrNull()?.let { it.beginsAt + it.seconds } ?: 0,
        stopSeconds = stops.sumOf { it.seconds },
        deepestStop = stops.maxOfOrNull { it.metres },
        maxDepthMetres = lines.maxOfOrNull { maxOf(it.fromMetres, it.toMetres) } ?: 0.0,
        cnsPercent = lastOf(done.evaluated.cns),
        otu = lastOf(done.evaluated.otu),
        noFlightSeconds = done.evaluated.noFlight,
        desaturationSeconds = done.evaluated.desaturation,
        gasUsedLitres = done.evaluated.gasUsed.mapKeys { numberedOf(it.key) },
        warnings = done.evaluated.findings.map { Warning(it.second, it.severity, it.said) },
    )
}

private fun lineOf(leg: Leg, added: Boolean): Line = Line(
    fromMetres = leg.from,
    toMetres = leg.to,
    beginsAt = leg.begins,
    seconds = leg.seconds,
    direction = leg.direction.name.lowercase(),
    gas = numberedOf(gasKeyOf(leg.gas)),
    added = added,
)

/** A cylinder as a caller names it, which is its place in the list rather than the model's key. */
private fun numberedOf(key: String): String =
    (key.removePrefix("g").toIntOrNull() ?: 1).toString()

/** What a series says at the end of the run, or nought where it says nothing. */
private fun lastOf(series: Series): Double {
    val held = (series.valueAt(series.size - 1) as? Element.Usable)?.value
    return (held as? Number)?.toDouble() ?: 0.0
}
