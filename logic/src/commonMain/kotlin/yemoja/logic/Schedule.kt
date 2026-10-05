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

    class Refused(
        val reason: String,
        /** The lines laid before the one at fault, as a form shows them while it is corrected. */
        val lines: List<Line> = emptyList(),
        /** Every line, by its number counted from 1, that stays at its depth and gives no duration. */
        val needsDuration: List<Int> = emptyList(),
    ) : Calculated()
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
    /** What each gas-reserve scenario asks of the cylinders, absent where it is switched off. */
    val reserves: Map<Scenario, ReserveAnswer>,
    /** The decompression ceiling through the dive in metres, empty where it never leaves the surface. */
    val ceiling: List<SchedulePoint>,
    /** How much longer the dive could stay at each moment, in seconds; a stretch owing a stop is left out. */
    val noDecompressionSeconds: List<SchedulePoint>,
    /** How long the way up from each moment would take, in seconds. */
    val timeToSurfaceSeconds: List<SchedulePoint>,
    /** GF99 through the dive: the excess over ambient as a share of the M-value's, as a percentage. */
    val gradientFactorNow: List<SchedulePoint>,
    /** The equivalent narcotic depth of the gas breathed through the dive, in metres. */
    val narcoticDepth: List<SchedulePoint>,
    /** `oc` or `ccr`, as the plan said. */
    val diveMode: String,
    /** The setpoint in force through the dive, in bar, and empty on open circuit. */
    val setpointSeries: List<SchedulePoint>,
    /** The oxygen breathed through the dive, in bar. */
    val oxygenSeries: List<SchedulePoint>,
    /** The central nervous system's clock through the dive, as a percentage. */
    val cnsSeries: List<SchedulePoint>,
    /** Oxygen tolerance units taken through the dive. */
    val otuSeries: List<SchedulePoint>,
    /** Each cylinder's gauge through the dive in bar, by its number, for the ones that say how big they are. */
    val pressures: Map<String, List<SchedulePoint>>,
)

/** SchedulePoint is one moment of a worked-out series: the second it belongs to, and the value then. Immutable. */
class SchedulePoint(val second: Int, val value: Double)

/**
 * ReserveAnswer is what one gas-reserve scenario came to, or why it could not be worked out.
 *
 * Immutable.
 */
sealed class ReserveAnswer {

    class Done(
        /** What each cylinder must still hold at the end of the dive, by its number. A cylinder needing nothing is absent. */
        val kept: Map<String, ReserveKept>,
        /** The scenario in one sentence, as the window's contingency line says it. */
        val said: String,
        /** The cylinders that end the dive with less than they keep, in a sentence, or absent where none does. */
        val shortfall: String?,
        /** The cylinders costed in litres only, having no size or no fill, in a sentence, or absent where every one is judged. */
        val unchecked: String?,
    ) : ReserveAnswer()

    class Refused(val reason: String) : ReserveAnswer()
}

/**
 * ReserveKept is what one cylinder must still hold at the end of the dive, and the moment that asks it.
 *
 * Immutable.
 */
class ReserveKept(
    val litres: Double,
    /** On the cylinder's own gauge, or absent where it does not say how big it is. */
    val bar: Double?,
    /** What the plan leaves on the gauge, or absent where the cylinder has no size or fill. */
    val endBar: Double?,
    /** Whether the plan ends with less than [bar]. */
    val short: Boolean,
    /** The moment that asks the most of this cylinder, in seconds from the start, and how deep. */
    val worstSeconds: Int,
    val worstMetres: Double,
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
class Warning(
    val second: Int,
    val severity: Severity,
    /** The whole of it in one sentence, the cylinder and the moment included, as the window lists it. */
    val said: String,
    /** The cylinder it is about, by its number, or absent where it is about the dive. */
    val gas: String?,
)

/**
 * The plan [planned] describes, calculated.
 *
 * **Nothing is read and nothing is written.** No logbook is needed and none is touched: a plan is
 * arithmetic over what the description says. A plan that follows a dive in a logbook is the one
 * exception and is not answered here; one that follows an earlier case of the same file is, by
 * `calculatedAll`. `API-7`.
 */
fun calculated(planned: Planned): Calculated = answeredOf(planned, null).answer

/**
 * Every case in [cases], in file order, a follower starting from what the case it follows left.
 *
 * The one list comes back in the same order, a refusal where a case has no answer. A case
 * following one that was refused is refused with it: what it would start from cannot be worked
 * out. `LOGIC-43`.
 */
fun calculatedAll(cases: List<Case>): List<Calculated> {
    val answered = ArrayList<Answered>(cases.size)
    for (case in cases) {
        val follows = case.follows
        if (follows == null) {
            answered += answeredOf(case.planned, null)
            continue
        }
        val earlier = answered[follows.earlier].done
        if (earlier == null) {
            val reason = "${case.name} follows ${cases[follows.earlier].name}, which did not calculate"
            answered += Answered(Calculated.Refused(reason), null)
            continue
        }
        val left = residualAfter(earlier.evaluated, earlier.whole.surface, follows.intervalSeconds)
        answered += answeredOf(case.planned, left)
    }
    return answered.map { it.answer }
}

/** One plan answered, with the completed run kept so a case following it can start from it. */
private class Answered(val answer: Calculated, val done: Worked.Done?)

private fun answeredOf(planned: Planned, residual: Residual.Done?): Answered {
    val ready = when (val shaped = shapedOf(planned, residual = residual)) {
        is Shaped.Ready -> shaped
        is Shaped.Wrong -> return Answered(
            Calculated.Refused(
                shaped.reason,
                shaped.legs.map { lineOf(it, added = false) },
                planned.segments.indices.filter { needsDuration(planned.segments, it) }.map { it + 1 },
            ),
            null,
        )
        is Shaped.Waiting -> return Answered(Calculated.Refused("the runtime is empty"), null)
    }
    val done = when (val worked = workedOf(ready)) {
        is Worked.Done -> worked
        is Worked.Refused -> return Answered(Calculated.Refused(worked.reason), null)
    }
    val reckoned = reckonedOf(planned, done, ready.conditions)
    return Answered(Calculated.Done(scheduleOf(planned, ready, done, reckoned)), done)
}

/** The schedule [done] came to, with the lines [ready] was asked for marked as the caller's. */
private fun scheduleOf(planned: Planned, ready: Shaped.Ready, done: Worked.Done, reckoned: Reckoned): Schedule {
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
        warnings = done.evaluated.findings.map { Warning(it.second, it.severity, warningSaid(it), it.source?.let(::numberedOf)) },
        reserves = reckoned.scenarios.mapNotNull { (scenario, reckoning) ->
            reckoning?.let { scenario to reserveAnswerOf(scenario, it, planned) }
        }.toMap(),
        ceiling = ceilingOf(done.evaluated.ceiling),
        noDecompressionSeconds = sampledOf(done.evaluated.noDecompressionTime),
        timeToSurfaceSeconds = sampledOf(done.evaluated.timeToSurface),
        gradientFactorNow = sampledOf(done.evaluated.gradientFactorNow),
        narcoticDepth = sampledOf(done.evaluated.narcoticDepth),
        diveMode = if (ready.conditions.loop == null) OPEN_CIRCUIT else CLOSED_CIRCUIT,
        setpointSeries = sampledOf(done.evaluated.setpoint),
        oxygenSeries = sampledOf(done.evaluated.oxygenPressure),
        cnsSeries = sampledOf(done.evaluated.cns),
        otuSeries = sampledOf(done.evaluated.otu),
        pressures = done.evaluated.pressures.entries.associate { (key, series) ->
            numberedOf(key) to sampledOf(series)
        },
    )
}

/** [series] as points, keeping the moments it holds and passing over anything unusable. */
private fun sampledOf(series: Series): List<SchedulePoint> =
    (0..<series.size).mapNotNull { at ->
        val value = ((series.valueAt(at) as? Element.Usable)?.value as? Number)?.toDouble()
            ?: return@mapNotNull null
        SchedulePoint(series.secondAt(at), value)
    }

/** The ceiling as points, or empty where it never rises: a flat nought along the surface says nothing. */
private fun ceilingOf(series: Series): List<SchedulePoint> {
    val points = sampledOf(series)
    return if (points.any { it.value > 0 }) points else emptyList()
}

/**
 * [finding] as the window lists it: the cylinder by its label, what is wrong, what to do, and when.
 *
 * Example: `Gas 1 runs empty: more volume, a higher start pressure or a lower SAC would keep it at
 * 27:14`.
 */
private fun warningSaid(finding: Finding): String {
    val named = finding.source?.let { "${gasLabelOf(gasIndexOf(it))} " }.orEmpty()
    return "$named${finding.what}: ${finding.how} at ${clockOf(finding.second)}"
}

private fun reserveAnswerOf(scenario: Scenario, reckoning: Reckoning, planned: Planned): ReserveAnswer = when (reckoning) {
    is Reckoning.Wrong -> ReserveAnswer.Refused(reckoning.reason)
    is Reckoning.Done -> {
        ReserveAnswer.Done(
            said = scenarioSaid(scenario, reckoning.reserve, planned),
            shortfall = shortfallSaid(scenario, reckoning.reserve),
            unchecked = uncheckedSaid(Reckoned(mapOf(scenario to reckoning)), planned),
            kept = reckoning.reserve.kept.entries.associate { (key, kept) ->
                numberedOf(key) to ReserveKept(
                    litres = kept.litres,
                    bar = kept.bar,
                    endBar = kept.end,
                    short = kept.short,
                    worstSeconds = kept.second,
                    worstMetres = kept.metres,
                )
            },
        )
    }
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
