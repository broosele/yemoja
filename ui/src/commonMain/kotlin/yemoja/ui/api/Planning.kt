package yemoja.ui.api

import yemoja.data.Element
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.logic.Operation
import yemoja.logic.Outcome
import yemoja.logic.planKeyOf
import yemoja.logic.Severity
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.ui.gui.Direction
import yemoja.ui.gui.Leg
import yemoja.ui.gui.Planned
import yemoja.ui.gui.Shaped
import yemoja.ui.gui.Worked
import yemoja.ui.gui.diveFieldsOf
import yemoja.ui.gui.gasKeyOf
import yemoja.ui.gui.newDiveOf
import yemoja.ui.gui.onDiveOf
import yemoja.ui.gui.planFieldsOf
import yemoja.ui.gui.shapedOf
import yemoja.ui.gui.workedOf

/*
 * The dive planner as functions: one that answers, and one that writes.
 *
 * See ../../../../../../ui/api/doc.md — `API-2` and `API-7`. Both take the description `API-6`
 * settles, and both run the window's own calculation rather than one of their own.
 */

/**
 * Calculated is a plan asked for: the schedule, or why there is none.
 *
 * A refusal is a plan that cannot be read or cannot be held — a duration that is not a duration, a
 * cylinder with no mix, a ceiling the way up cannot reach. It says which in the words the form
 * says it in, so one description is refused one way however it arrived.
 */
internal sealed class Calculated {

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
internal class Schedule(
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
internal class Line(
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
internal class Stop(val metres: Double, val seconds: Int)

/** Something the model has to say about a plan: when, how serious, and what. Immutable. */
internal class Warning(val second: Int, val severity: Severity, val said: String)

/**
 * The plan [planned] describes, calculated.
 *
 * **Nothing is read and nothing is written.** No logbook is needed and none is touched: a plan is
 * arithmetic over what the description says. A plan that follows an earlier run is the one
 * exception and is not answered here, the run it follows being a dive in a logbook. `API-7`.
 */
internal fun calculated(planned: Planned): Calculated {
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

/**
 * Prepared is a plan ready to be written: the key it sits under and the fields it carries, or why it
 * is not ready.
 *
 * The one place those fields are built, so a plan the window saves, one this writes and one an
 * agent stages are the same plan. `API-9`.
 */
internal sealed class Prepared {

    class Plan(
        val key: String,
        /** The profile's own fields, which sit under [key] in the dive's `profiles`. */
        val fields: Map<String, Stored>,
        /** What the plan says about the dive itself: its start, and the dive it follows. */
        val dive: Map<String, Stored>,
    ) : Prepared()

    class Refused(val reason: String) : Prepared()
}

/**
 * [planned] as the fields a plan is written as, calculated first.
 *
 * [universe] is absent where there is no logbook, which a plan following an earlier run needs and
 * nothing else does.
 */
internal fun preparedOf(universe: Universe?, planned: Planned, name: String): Prepared {
    val ready = when (val shaped = shapedOf(planned, universe)) {
        is Shaped.Ready -> shaped
        is Shaped.Wrong -> return Prepared.Refused(shaped.reason)
        is Shaped.Waiting -> return Prepared.Refused("the plan has no lines")
    }
    val done = when (val worked = workedOf(ready)) {
        is Worked.Done -> worked
        is Worked.Refused -> return Prepared.Refused(worked.reason)
    }
    return Prepared.Plan(
        key = planKeyOf(name),
        fields = planFieldsOf(planned, ready.conditions, done.whole),
        dive = diveFieldsOf(planned, universe),
    )
}

/**
 * Saved is what writing a plan into a logbook came to: the dive it sits on, or why it does not.
 */
internal sealed class Saved {

    /** The dive's id, and the key the plan sits under within it. */
    class Done(val dive: String, val key: String) : Saved()

    class Refused(val reason: String) : Saved()
}

/**
 * Writes [planned] into [universe] as a plan, on the dive called [dive] or on a new one.
 *
 * **This is the half that changes data**, and the only one: everything else about a plan is
 * `calculated`. It is the same write the window's *Save as new dive* and *Attach to existing dive*
 * make, so a plan written here is a plan the window can open. `API-7`.
 *
 * [name] is what the plan is called within the dive, *Plan A* by default, and a plan that will not
 * calculate is refused rather than written: a schedule nobody can read is not worth keeping.
 */
internal fun saved(
    universe: Universe,
    planned: Planned,
    dive: String? = null,
    name: String = FIRST_PLAN,
): Saved {
    val made = when (val read = preparedOf(universe, planned, name)) {
        is Prepared.Refused -> return Saved.Refused(read.reason)
        is Prepared.Plan -> read
    }
    val held = dive?.let {
        universe.logbook[it] ?: return Saved.Refused("$it is not in this logbook")
    }
    if (held != null && held.description != Types.DIVE) {
        return Saved.Refused("$dive is a ${held.description.name} rather than a dive")
    }
    val key = made.key
    val changes = if (held == null) {
        newDiveOf(key, made.fields, made.dive)
    } else {
        onDiveOf(held, key, made.fields, made.dive)
    }
    return when (val outcome = universe.change(Operation.EDIT, *changes.toTypedArray())) {
        is Outcome.Refused -> Saved.Refused(outcome.reason)
        is Outcome.Done -> Saved.Done(
            dive = dive ?: outcome.added.firstOrNull() ?: return Saved.Refused("nothing was added"),
            key = key,
        )
    }
}

/** What a plan is called where nobody names it: the first letter free, `GUI-44`. */
internal const val FIRST_PLAN = "Plan A"
