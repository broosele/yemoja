package yemoja.logic

import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/*
 * A dive planned apart from any dive: segments, settings and gases in, the way up and what it
 * costs out. Moved out of the window's own code, `LOGIC-43`, so that something other than the
 * window — a web page compiled from this module, among others — can ask for the same calculation.
 * Nothing here draws anything; the window's Calculations tab is the one place that still does,
 * calling into exactly what is declared below.
 *
 * See ../../../../../doc.md — `LOGIC-43`.
 */

/**
 * Segment is one line of a planned run as it is typed: a change of depth, or a stay at one.
 *
 * A line that changes depth is timed by its duration or by its rate, whichever was typed last, and
 * the other is worked out from it. Both blank takes the rate from the settings. A line that stays
 * has a duration and no rate.
 *
 * Immutable.
 */
data class Segment(
    val depth: String = "",
    /** Minutes and seconds, as `2:13`, or whole minutes, as `25`. */
    val duration: String = "",
    /** Metres a minute. */
    val rate: String = "",
    /** The cylinder by its place in the list, or null to breathe what the line above breathes. */
    val gas: Int? = null,
)

/**
 * Role is what a cylinder is carried for, which decides the oxygen it is held to and whether the
 * way up may switch to it by itself.
 */
enum class Role(val label: String) {

    /** Held to *pO₂ max bottom*, and chosen by the ascent where it is the best mix allowed. */
    BOTTOM("Bottom"),

    /** Held to *pO₂ max deco*, and chosen by the ascent where it is the best mix allowed. */
    DECO("Deco"),

    /**
     * Held to *pO₂ max bottom*, and never chosen by the ascent: breathed only where a line names
     * it. Bottom rather than deco because a bailout is breathed in trouble, at the effort that
     * brought the trouble on.
     */
    BAILOUT("Bailout"),
}

/**
 * Breathed is one cylinder as it is typed: what is in it, what it is carried for, what it holds,
 * and how fast it is breathed.
 *
 * Immutable.
 */
data class Breathed(
    val gas: String = Gas.AIR.toString(),
    val role: Role = Role.BOTTOM,
    /** Litres of water it holds. */
    val size: String = "",
    /** Bar it was filled to. */
    val fill: String = "",
    /** Litres a minute at the surface. */
    val sac: String = "",
)

/**
 * Planned is a dive plan as somebody wrote it down: the lines, the cylinders and the settings.
 *
 * **Text, not numbers.** Every field is held as it would be typed, so one description is read one
 * way however it arrived: a caller sending `soon` for a duration is refused in the words the form
 * refuses it in, rather than in whatever a second parser would have said. What a field may say is
 * `manual/planning-from-a-file.md`.
 *
 * Immutable.
 */
data class Planned(
    /** The lines of the runtime, in order, the first of them leaving the surface. */
    val segments: List<Segment> = listOf(Segment()),
    /** The cylinders, in the order they are listed, which is the order they are named in. */
    val gases: List<Breathed> = listOf(Breathed()),
    val gradientLow: String = "",
    val gradientHigh: String = "",
    val bottomOxygen: String = "",
    val decoOxygen: String = "",
    val leastOxygen: String = "",
    val descentRate: String = "",
    val ascentRate: String = "",
    val safetyDepth: String = "",
    val safetyMinutes: String = "",
    val lastStop: String = "",
    /** Whether the way up stops to switch gas where no deco stop is owed. */
    val switchStops: Boolean = false,
    val stressFactor: String = "",
    /** Minutes the gas reserve spends at the depth trouble starts before the way up begins. */
    val problemMinutes: String = "",
    /** Whether the gas reserve tries losing a cylinder. */
    val lostGasScenario: Boolean = true,
    /** The cylinder lost, by its place in the list, or absent for the first deco cylinder. */
    val lostGas: Int? = null,
    /** Whether the gas reserve tries a buddy out of gas, sharing this diver's. */
    val sharedScenario: Boolean = true,
    /** In the words `water_type` uses. */
    val water: String = "salt",
    /** In bar, as `atmospheric_pressure` holds it. */
    val atmosphericPressure: String = SEA_LEVEL_SAID,
    /** When the plan begins: a date such as `2026-10-03`, and a time such as `14:30`. */
    val startDate: String = "",
    val startTime: String = "",
    /** The earlier run this one follows, or absent for a dive started clean. `GUI-43`. */
    val following: Following? = null,
)

/** What the cylinder at [index] is called, which is what a reader sees of a key. */
fun gasLabelOf(index: Int): String = "Gas ${index + 1}"

/**
 * What a line of the runtime calls the cylinder at [index]: its number in the list and what is in
 * it, so a reader choosing one need not look across at the gases.
 *
 * Example: `2: EAN50`, or `2` alone while its mix is still blank.
 */
fun gasChoiceOf(shaping: Planned, index: Int): String {
    val typed = shaping.gases.getOrNull(index)?.gas?.trim().orEmpty()
    val mix = gasOf(typed)?.toString() ?: typed
    return if (mix.isEmpty()) "${index + 1}" else "${index + 1}: $mix"
}

/**
 * [typed] as the application writes a gas where it says the same thing in another case, and as
 * typed otherwise.
 *
 * Only the case is changed, so the text under a reader's cursor never moves while they type:
 * `ean50` becomes `EAN50`, while `tmx 21/35`, which would lose its space, is left alone until a
 * line shows it.
 */
fun prettyGasOf(typed: String): String {
    val written = gasOf(typed)?.toString() ?: return typed
    return if (written.equals(typed, ignoreCase = true)) written else typed
}

/** The key the cylinder at [index] sits under, as a dive's own cylinders sit under keys. */
fun gasKeyOf(index: Int): String = "g${index + 1}"

/** Which cylinder [key] is, by its place in the list the keys were minted from. */
fun gasIndexOf(key: String): Int = (key.removePrefix("g").toIntOrNull() ?: 1) - 1

/** Direction is which way a line goes, which the arrow before it says. */
enum class Direction(val arrow: String) {
    DOWN("↓"),
    UP("↑"),
    STAY("→"),
}

/**
 * Leg is one line of a run once read: from where to where, from when and for how long, on what.
 *
 * A typed line is a leg at [index] in the list typed. A line of the worked-out ascent has no place
 * there, and its [index] is `-1`.
 *
 * Immutable.
 */
data class Leg(
    val index: Int,
    val from: Double,
    val to: Double,
    val begins: Int,
    val seconds: Int,
    val gas: Int,
    /** Whether the gas follows from the line above rather than being named on this one. */
    val inherited: Boolean,
    /**
     * The rate the line was timed by, typed or taken from the settings, or null where a duration
     * timed it. Kept because the seconds are counted up to a whole one, and the rate worked back
     * from them would read as *17.9* where the reader chose 18.
     */
    val timedBy: Double? = null,
) {
    val ends: Int get() = begins + seconds

    val direction: Direction
        get() = when {
            to > from -> Direction.DOWN
            to < from -> Direction.UP
            else -> Direction.STAY
        }

    /** Metres a minute, or null for a stay, which has none. */
    val rate: Double?
        get() = when {
            direction == Direction.STAY -> null
            timedBy != null -> timedBy
            else -> abs(to - from) / (seconds / SECONDS_IN_MINUTE)
        }
}

/**
 * Whether nothing that times or places [segment] is typed.
 *
 * A gas alone is not a line of the run, but the lines below it still follow it: a reader who
 * chooses the gas first and the depth after has already said what the next lines breathe.
 */
private fun isBlank(segment: Segment): Boolean =
    segment.depth.isBlank() && segment.duration.isBlank() && segment.rate.isBlank()

/**
 * The legs [segments] lay out, and why the first that will not read does not, or null.
 *
 * A line that times itself by neither a duration nor a rate travels at [descentRate] or
 * [ascentRate]. Those are null where the settings will not read, and such a line is then untimed:
 * the settings say why, so the line need not.
 */
fun laidOf(
    segments: List<Segment>,
    gases: Int,
    descentRate: Double?,
    ascentRate: Double?,
): Pair<List<Leg>, String?> {
    val legs = ArrayList<Leg>()
    var from = 0.0
    var second = 0
    var gas = 0
    for ((index, segment) in segments.withIndex()) {
        if (isBlank(segment)) {
            segment.gas?.takeIf { it in 0..<gases }?.let { gas = it }
            continue
        }
        val line = "Line ${index + 1}"
        val to = segment.depth.trim().toDoubleOrNull()?.takeIf { it >= 0 }
            ?: return legs to if (segment.depth.isBlank()) {
                "$line needs a depth"
            } else {
                "$line depth should be 0 m or more, not ${said(segment.depth)}"
            }
        var timedBy: Double? = null
        val seconds = if (to == from) {
            if (segment.duration.isBlank()) return legs to "$line needs a duration, because it stays at ${plain(to)} m"
            durationOf(segment.duration) ?: return legs to durationWrong(line, segment.duration)
        } else if (segment.duration.isNotBlank()) {
            durationOf(segment.duration) ?: return legs to durationWrong(line, segment.duration)
        } else {
            val rate = if (segment.rate.isNotBlank()) {
                segment.rate.trim().toDoubleOrNull()?.takeIf { it > 0 }
                    ?: return legs to "$line rate should be more than 0 m/min, not ${said(segment.rate)}"
            } else {
                (if (to > from) descentRate else ascentRate) ?: return legs to null
            }
            timedBy = rate
            maxOf(ceil(abs(to - from) / rate * SECONDS_IN_MINUTE).toInt(), 1)
        }
        val named = segment.gas?.takeIf { it in 0..<gases }
        gas = named ?: gas.coerceIn(0, gases - 1)
        legs += Leg(index, from, to, second, seconds, gas, inherited = named == null, timedBy = timedBy)
        second += seconds
        from = to
    }
    return legs to null
}

/**
 * Seconds a duration [typed] says, as `2:13` or as minutes, or null where it says none worth a
 * line. Read as every clock in the application is read.
 */
fun durationOf(typed: String): Int? = secondsOf(typed.trim())?.takeIf { it > 0 }?.toInt()

private fun durationWrong(line: String, typed: String): String =
    "$line duration should be m:ss or minutes, such as 2:13 or 25, not ${said(typed)}"

/**
 * Conditions are the plan's settings once read.
 *
 * Immutable.
 */
class Conditions(
    val gradientLow: Double,
    val gradientHigh: Double,
    val bottomOxygen: Double,
    val decoOxygen: Double,
    /** The least oxygen any cylinder is breathed at, in bar. */
    val leastOxygen: Double,
    val descentRate: Double,
    val ascentRate: Double,
    val safetyDepth: Double,
    /** Nought where there is no safety stop. */
    val safetySeconds: Int,
    val lastStop: Double,
    /** Whether the way up stops to switch gas where no deco stop is owed. */
    val switchStops: Boolean,
    /** Kilograms a cubic metre. */
    val density: Double,
    /** Absolute, in bar. */
    val atmosphericPressure: Double,
)

/** One standard atmosphere, as a plan's atmospheric pressure starts where nothing else says. */
const val SEA_LEVEL_SAID = "1.013"

/** The plan's settings, or why the first that will not read does not. */
fun conditionsOf(shaping: Planned): Pair<Conditions?, String?> {
    val low = percentageOf(shaping.gradientLow) ?: return null to factorWrong("low", shaping.gradientLow)
    val high = percentageOf(shaping.gradientHigh) ?: return null to factorWrong("high", shaping.gradientHigh)
    if (low > high) return null to "GF low should not be higher than GF high"
    val bottom = positiveOf(shaping.bottomOxygen)
        ?: return null to numberWrong("pO₂ max bottom", "more than 0 bar", shaping.bottomOxygen)
    val deco = positiveOf(shaping.decoOxygen)
        ?: return null to numberWrong("pO₂ max deco", "more than 0 bar", shaping.decoOxygen)
    val least = positiveOf(shaping.leastOxygen)
        ?: return null to numberWrong("pO₂ min", "more than 0 bar", shaping.leastOxygen)
    val descent = positiveOf(shaping.descentRate)
        ?: return null to numberWrong("Descent rate", "more than 0 m/min", shaping.descentRate)
    val ascent = positiveOf(shaping.ascentRate)
        ?: return null to numberWrong("Ascent rate", "more than 0 m/min", shaping.ascentRate)
    val minutes = shaping.safetyMinutes.trim().toDoubleOrNull()?.takeIf { it >= 0 }
        ?: return null to numberWrong("Safety stop duration", "0 min or more", shaping.safetyMinutes)
    val safety = if (minutes == 0.0) {
        0.0
    } else {
        positiveOf(shaping.safetyDepth)
            ?: return null to numberWrong("Safety stop depth", "more than 0 m", shaping.safetyDepth)
    }
    val last = shaping.lastStop.trim().toDoubleOrNull()?.takeIf { it >= 0 }
        ?: return null to numberWrong("Last stop", "0 m or more", shaping.lastStop)
    val density = densityOfWater(shaping.water)
        ?: return null to "Water should be salt or fresh"
    val within = Settings.DEFAULT_ATMOSPHERIC_PRESSURE.range
    val atmospheric = shaping.atmosphericPressure.trim().toDoubleOrNull()?.takeIf { it in within }
        ?: return null to numberWrong(
            "Atmospheric pressure",
            "${plain(within.start)} to ${plain(within.endInclusive)} bar",
            shaping.atmosphericPressure,
        )
    return Conditions(
        gradientLow = low,
        gradientHigh = high,
        bottomOxygen = bottom,
        decoOxygen = deco,
        leastOxygen = least,
        descentRate = descent,
        ascentRate = ascent,
        safetyDepth = safety,
        safetySeconds = (minutes * SECONDS_IN_MINUTE).roundToInt(),
        lastStop = last,
        switchStops = shaping.switchStops,
        density = density,
        atmosphericPressure = atmospheric,
    ) to null
}

/** The source the cylinder [breathed] is, held to the limit its role gives it under [conditions]. */
fun sourceOf(breathed: Breathed, gas: Gas, conditions: Conditions): Source = Source(
    gas = gas,
    sac = breathed.sac.trim().toDoubleOrNull(),
    volume = breathed.size.trim().toDoubleOrNull(),
    fill = breathed.fill.trim().toDoubleOrNull(),
    mostOxygen = limitOf(breathed.role, conditions),
    ascentMayChoose = breathed.role != Role.BAILOUT,
    leastOxygen = conditions.leastOxygen,
)

/** The most oxygen a cylinder of [role] is breathed at under [conditions], in bar. */
fun limitOf(role: Role, conditions: Conditions): Double =
    if (role == Role.DECO) conditions.decoOxygen else conditions.bottomOxygen

/** Shaped is a run built from what was typed, or why there is none yet, with the lines read so far. */
sealed class Shaped {

    abstract val legs: List<Leg>

    /** A run to complete and evaluate, holding the typed lines alone. */
    class Ready(val run: Run, override val legs: List<Leg>, val conditions: Conditions) : Shaped()

    /** Nothing is typed, so the form waits rather than complains. */
    class Waiting(override val legs: List<Leg>) : Shaped()

    class Wrong(val reason: String, override val legs: List<Leg>) : Shaped()
}

/**
 * The run [shaping] describes, down to the end of its last typed line.
 *
 * **The switches are where the gas changes between lines**, and the first is always at nought:
 * a run that says nothing about what it went in on is refused, and rightly. `LOGIC-37`.
 *
 * The run carries nothing unless [residual] hands it something: a chained case of a plan file
 * starts from what its earlier case left. `LOGIC-43`.
 */
fun shapedOf(shaping: Planned, universe: Universe? = null, residual: Residual.Done? = null): Shaped {
    val (conditions, unreadable) = conditionsOf(shaping)
    val (legs, wrong) = laidOf(
        shaping.segments,
        shaping.gases.size,
        conditions?.descentRate ?: positiveOf(shaping.descentRate),
        conditions?.ascentRate ?: positiveOf(shaping.ascentRate),
    )
    if (wrong != null) return Shaped.Wrong(wrong, legs)
    if (unreadable != null || conditions == null) return Shaped.Wrong(unreadable ?: "", legs)
    if (legs.isEmpty()) return Shaped.Waiting(legs)
    val sources = LinkedHashMap<String, Source>()
    for ((index, breathed) in shaping.gases.withIndex()) {
        val gas = gasOf(breathed.gas) ?: return Shaped.Wrong(
            if (breathed.gas.isBlank()) {
                "${gasLabelOf(index)} is missing its mix"
            } else {
                "${gasLabelOf(index)} should be a mix such as AIR, EAN32 or TMX18/45, not ${said(breathed.gas)}"
            },
            legs,
        )
        sources[gasKeyOf(index)] = sourceOf(breathed, gas, conditions)
    }
    // A start that will not read, or a run followed that cannot be, is the plan's fault as a
    // setting that will not read is: the model cannot say what the dive starts from.
    val followed = followedOf(shaping, universe)
    if (followed is Followed.Wrong) return Shaped.Wrong(followed.reason, legs)
    (startOf(shaping) as? Start.Wrong)?.let { return Shaped.Wrong(it.reason, legs) }
    val left = (followed as? Followed.After)?.residual ?: residual
    val points = listOf(0 to 0.0) + legs.map { it.ends to it.to }
    val switches = ArrayList<Pair<Int, String>>()
    for (leg in legs) {
        val key = gasKeyOf(leg.gas)
        if (switches.lastOrNull()?.second != key) switches += (if (switches.isEmpty()) 0 else leg.begins) to key
    }
    return Shaped.Ready(
        Run(
            depth = points,
            sources = sources,
            gradientFactorLow = conditions.gradientLow,
            gradientFactorHigh = conditions.gradientHigh,
            switches = switches,
            density = conditions.density,
            surface = conditions.atmosphericPressure,
            safetyStop = if (conditions.safetySeconds > 0) {
                SafetyStop(conditions.safetyDepth, conditions.safetySeconds)
            } else {
                null
            },
            ascentRate = conditions.ascentRate,
            lastStop = conditions.lastStop,
            carried = left?.tissues,
            oxygenCarried = left?.oxygen,
        ),
        legs,
        conditions,
    )
}

/** Worked is what the model makes of a ready run: the way up it adds, and the whole dive evaluated. */
sealed class Worked {

    class Done(val tail: List<Leg>, val whole: Run, val evaluated: Evaluated.Done) : Worked()

    class Refused(val reason: String) : Worked()
}

/** The ascent [ready] is completed with, and what the model makes of the dive with it on the end. */
fun workedOf(ready: Shaped.Ready): Worked {
    val conditions = ready.conditions
    val ascended = when (val ascent = completeAscent(ready.run, conditions.ascentRate, conditions.lastStop, conditions.switchStops)) {
        is Ascended.Refused -> return Worked.Refused(ascent.reason)
        is Ascended.Done -> ascent
    }
    val whole = withAscent(ready.run, ascended)
    return when (val evaluated = evaluate(whole)) {
        is Evaluated.Refused -> Worked.Refused(evaluated.reason)
        is Evaluated.Done -> Worked.Done(tailOf(ready.run, ascended), whole, evaluated)
    }
}

/**
 * The cylinder the lost-gas scenario loses: the one chosen, or the first deco cylinder where none
 * is, or null where there is neither.
 */
fun Planned.lostIndex(): Int? =
    lostGas?.takeIf { it in gases.indices } ?: gases.indexOfFirst { it.role == Role.DECO }.takeIf { it >= 0 }

/**
 * Whether the lost-gas scenario is tried: a cylinder is lost, and *None* was not chosen. A plan with
 * no deco gas and nothing chosen loses none, so its reserve has no lost-gas scenario to try.
 */
fun Planned.lostGasTried(): Boolean = lostGasScenario && lostIndex() != null

/**
 * Scenario is one way a dive can go wrong that the gas reserve is kept back for. `LOGIC-40`.
 *
 * The longer explanation shown beside each in the window is the window's own copy, not carried
 * here: this is the one place `logic` would otherwise reach for display text, and a plan asked for
 * from outside the window has no use for it.
 */
enum class Scenario(val label: String) {

    /** The cylinders ticked *Lost* are gone, and the way up is to the surface at the usual rate. */
    LOST_GAS("Lost"),

    /** A buddy has lost their bottom gas, and the two share this diver's up to a deco gas. */
    SHARED("Buddy out of gas"),
}

/** Reckoning is what one scenario of the gas reserve came to, or why it came to nothing. */
sealed class Reckoning {

    class Done(val reserve: Reserve.Done) : Reckoning()

    class Wrong(val reason: String) : Reckoning()
}

/**
 * Reckoned is what each scenario of the gas reserve came to, with null for one switched off.
 *
 * Immutable.
 */
class Reckoned(val scenarios: Map<Scenario, Reckoning?>) {

    /** The scenarios that were worked out, each with its reserve. */
    val done: Map<Scenario, Reserve.Done>
        get() = scenarios.mapNotNull { (scenario, it) -> (it as? Reckoning.Done)?.let { scenario to it.reserve } }
            .toMap()
}

/**
 * The gas [done] must keep back in each scenario [shaping] has switched on.
 *
 * The stress factor is read apart from the other settings, so one typed wrong leaves the shared
 * scenario unsaid and everything else answered. `LOGIC-40`.
 */
fun reckonedOf(shaping: Planned, done: Worked.Done, conditions: Conditions): Reckoned {
    fun reckoning(reserve: Reserve): Reckoning = when (reserve) {
        is Reserve.Done -> Reckoning.Done(reserve)
        // A cylinder named is one whose rate is missing: said with everything else it lacks, and
        // for every cylinder lacking a rate, so one fix is not followed by the next complaint.
        is Reserve.Refused -> Reckoning.Wrong(
            if (reserve.source == null) reserve.reason else missingSaid(shaping) ?: reserve.reason,
        )
    }

    val keys = shaping.gases.indices
    // Both scenarios begin with it, so one typed wrong leaves both unsaid.
    val problem = problemSecondsOf(shaping)
    if (problem == null) {
        val wrong = Reckoning.Wrong(numberWrong("Problem solving time", "0 minutes or more", shaping.problemMinutes))
        return Reckoned(
            mapOf(
                Scenario.LOST_GAS to wrong.takeIf { shaping.lostGasTried() },
                Scenario.SHARED to wrong.takeIf { shaping.sharedScenario },
            ),
        )
    }
    val lost = shaping.lostIndex()?.takeIf { shaping.lostGasTried() }
    val lostGas = lost?.let {
        reckoning(
            lostGasReserve(
                done.whole,
                setOf(gasKeyOf(it)),
                conditions.ascentRate,
                conditions.lastStop,
                problem,
                conditions.switchStops,
            ),
        )
    }
    val shared = if (shaping.sharedScenario) {
        val factor = shaping.stressFactor.trim().toDoubleOrNull()?.takeIf { it >= 1 }
        if (factor == null) {
            Reckoning.Wrong(numberWrong("Stress factor", "1 or more", shaping.stressFactor))
        } else {
            val deco = keys.filter { shaping.gases[it].role == Role.DECO }.map { gasKeyOf(it) }.toSet()
            reckoning(
                sharedGasReserve(
                    done.whole,
                    deco,
                    factor,
                    conditions.ascentRate,
                    conditions.lastStop,
                    problem,
                    conditions.switchStops,
                ),
            )
        }
    } else {
        null
    }
    return Reckoned(mapOf(Scenario.LOST_GAS to lostGas, Scenario.SHARED to shared))
}

/**
 * What the cylinder under [key] must still hold at the end of the dive: the most any scenario
 * switched on asks of it, since the cylinder has to meet each of them.
 *
 * In bar, rounded up, or in litres where nobody said how big it is. Nothing for a cylinder no
 * scenario needs anything from.
 */
fun keptSaid(reckoned: Reckoned, key: String): String {
    val kept = reckoned.done.values.mapNotNull { it.kept[key] }
    kept.mapNotNull { it.bar }.maxOrNull()?.let { return "${ceil(it).toInt()} bar" }
    return kept.maxOfOrNull { it.litres }?.let { "${ceil(it).toInt()} L" }.orEmpty()
}

/** Whether the cylinder under [key] ends the dive with less than any scenario switched on keeps. */
fun isShort(reckoned: Reckoned, key: String): Boolean =
    reckoned.done.values.any { it.kept[key]?.short == true }

/** When the moment that sets a reserve is, and how deep. */
fun worstSaid(kept: Kept): String =
    "${clockOf(kept.second)} (${plain(kept.metres)} m)"

/**
 * Why no reserve can be worked out: every cylinder the reserve may breathe that has no rate, each
 * with everything it lacks. Null where none lacks a rate.
 *
 * Example: `Cannot be calculated (missing for Gas 1: SAC, volume, start pressure)`.
 */
fun missingSaid(shaping: Planned): String? {
    val lost = shaping.lostIndex()?.takeIf { shaping.lostGasTried() }
    val missing = shaping.gases.withIndex().filter { (index, breathed) ->
        index != lost && breathed.sac.trim().toDoubleOrNull() == null
    }.map { (index, breathed) -> "${gasLabelOf(index)}: ${lackedBy(breathed).joinToString(", ")}" }
    if (missing.isEmpty()) return null
    return "Cannot be calculated (missing for ${missing.joinToString("; ")})"
}

/** What of its rate, its volume and its start pressure [breathed] does not say. */
private fun lackedBy(breathed: Breathed): List<String> = listOfNotNull(
    "SAC".takeIf { breathed.sac.trim().toDoubleOrNull() == null },
    "volume".takeIf { breathed.size.trim().toDoubleOrNull() == null },
    "start pressure".takeIf { breathed.fill.trim().toDoubleOrNull() == null },
)

/**
 * What one scenario came to, in a sentence: what each cylinder's reserve needs to be, what the
 * scenario assumes, and the moment that sets it.
 *
 * A scenario that needs nothing says so, and why where the reason is a deco gas the buddy can go
 * to at once, rather than a worst moment at the surface that means nothing. The worst moment is
 * said once where every cylinder's is the same, and beside each where they differ.
 *
 * Example: `Gas 1 reserve needs to be 54 bar for 2:00 at depth, then surfacing without Gas 2 at
 * normal SAC, worst at 25:00 (40 m)`.
 */
fun scenarioSaid(scenario: Scenario, reserve: Reserve.Done, shaping: Planned): String {
    val kept = reserve.kept.entries.sortedBy { gasIndexOf(it.key) }
    if (kept.isEmpty()) {
        return when (scenario) {
            Scenario.LOST_GAS -> "No reserve needed"
            Scenario.SHARED -> decoReachedSaid(shaping)
                ?.let { "No sharing needed: each diver switches to $it at once" } ?: "No sharing needed"
        }
    }
    val worsts = kept.map { worstSaid(it.value) }.distinct()
    val needs = kept.mapIndexed { index, (key, it) ->
        val held = it.bar?.let { bar -> "${ceil(bar).toInt()} bar" } ?: "${ceil(it.litres).toInt()} L"
        "${gasLabelOf(gasIndexOf(key))} reserve" + (if (index == 0) " needs to be" else "") + " $held" +
            (if (worsts.size > 1) ", worst at ${worstSaid(it)}" else "")
    }
    val held = problemSecondsOf(shaping)?.takeIf { it > 0 }?.let { clockOf(it) }
    val assumed = when (scenario) {
        Scenario.LOST_GAS -> {
            val lost = shaping.lostIndex()?.let { gasLabelOf(it) } ?: "the lost gas"
            (held?.let { "$it at depth, then " } ?: "") + "surfacing without $lost at normal SAC"
        }

        Scenario.SHARED -> "two divers sharing " + (held?.let { "$it at depth, then " } ?: "") +
                "${upToSaid(kept.maxOf { it.value.upTo })}, each at ${shaping.stressFactor.trim()} × SAC"
    }
    return "${needs.joinToString(" and ")} for $assumed" + worsts.singleOrNull()?.let { ", worst at $it" }.orEmpty()
}

/** The problem-solving time [shaping] asks for, in whole seconds, or null where it will not read. */
fun problemSecondsOf(shaping: Planned): Int? =
    shaping.problemMinutes.trim().toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * SECONDS_IN_MINUTE).roundToInt() }

/** The deco cylinder a buddy goes to first, being the one breathable deepest, as its line names it. */
private fun decoReachedSaid(shaping: Planned): String? {
    val conditions = conditionsOf(shaping).first ?: return null
    return shaping.gases.withIndex().filter { it.value.role == Role.DECO }
        .maxByOrNull { (_, breathed) ->
            gasOf(breathed.gas)?.let {
                maximumOperatingDepth(it, most = conditions.decoOxygen, density = conditions.density, surface = conditions.atmosphericPressure)
            } ?: -1.0
        }?.let { gasChoiceOf(shaping, it.index) }
}

// To a tenth, as the MOD beside the deco gas is, so the two read as the same depth.
private fun upToSaid(metres: Double?): String =
    if (metres == null || metres <= 0) "to the surface" else "to ${plain((metres * 10).roundToInt() / 10.0)} m"

/**
 * The cylinders that end the dive with less than [scenario] keeps, and what they end with, or null
 * where none does.
 *
 * Example: `Gas 1 reserve violation: needs to be 60 bar, but 40 bar is left (surfacing without
 * the lost gas)`.
 */
fun shortfallSaid(scenario: Scenario, reserve: Reserve.Done): String? {
    val short = reserve.kept.entries.filter { it.value.short }.sortedBy { gasIndexOf(it.key) }
    if (short.isEmpty()) return null
    val why = when (scenario) {
        Scenario.LOST_GAS -> "surfacing without the lost gas"
        Scenario.SHARED -> "two divers sharing ${upToSaid(short.maxOf { it.value.upTo })}"
    }
    return short.joinToString("; ") { (key, kept) ->
        // A gauge the plan has already run below nought is empty, not a negative pressure.
        val left = floor(kept.end ?: 0.0).toInt().coerceAtLeast(0)
        "${gasLabelOf(gasIndexOf(key))} reserve violation: needs to be ${ceil(kept.bar ?: 0.0).toInt()} bar, " +
            "but $left bar is left"
    } + " ($why)"
}

/** The cylinders a reserve breathes that have no size or no start pressure, so cannot be checked. */
fun uncheckedSaid(reckoned: Reckoned, shaping: Planned): String? {
    val unchecked = reckoned.done.values.filter { !it.judged }.flatMap { it.kept.keys }
        .map { gasIndexOf(it) }.distinct().sorted().filter {
            val breathed = shaping.gases[it]
            breathed.size.trim().toDoubleOrNull() == null || breathed.fill.trim().toDoubleOrNull() == null
        }
    if (unchecked.isEmpty()) return null
    return unchecked.joinToString("; ") { index ->
        val lacked = lackedBy(shaping.gases[index]).filter { it != "SAC" }
        "${gasLabelOf(index)}: reserve in litres only (missing: ${lacked.joinToString(", ")})"
    }
}

/** [run] with [ascent] on the end of it, and nothing else about it changed. */
fun withAscent(run: Run, ascent: Ascended.Done): Run = Run(
    depth = run.depth + ascent.depth,
    sources = run.sources,
    gradientFactorLow = run.gradientFactorLow,
    gradientFactorHigh = run.gradientFactorHigh,
    switches = run.switches + ascent.switches,
    density = run.density,
    surface = run.surface,
    carried = run.carried,
    oxygenCarried = run.oxygenCarried,
    safetyStop = run.safetyStop,
    ascentRate = run.ascentRate,
    lastStop = run.lastStop,
)

/**
 * The lines the worked-out [ascent] is shown as, below the typed ones.
 *
 * **The ascent does not repeat the point it leaves from**, so the run's own last point begins the
 * first line. The model writes a point a minute while it holds a stop, and those minutes are one
 * line. A rise through several depths is one line too, unless a gas is switched on the way, since
 * a switch is what a reader has to see.
 */
fun tailOf(run: Run, ascent: Ascended.Done): List<Leg> {
    val start = run.depth.lastOrNull() ?: return emptyList()
    val points = listOf(start) + ascent.depth
    val switches = run.switches + ascent.switches
    val legs = ArrayList<Leg>()
    for (at in 1..<points.size) {
        val (began, from) = points[at - 1]
        val (ends, to) = points[at]
        if (ends <= began) continue
        val key = switches.lastOrNull { it.first <= began }?.second ?: continue
        val leg = Leg(-1, from, to, began, ends - began, gasIndexOf(key), inherited = true)
        val last = legs.lastOrNull()
        val switched = ascent.switches.any { it.first == began }
        if (last != null && !switched && last.gas == leg.gas && last.direction == leg.direction) {
            legs[legs.lastIndex] = last.copy(to = to, seconds = last.seconds + leg.seconds)
        } else {
            legs += leg
        }
    }
    return legs
}

/**
 * Whether [leg] goes deeper than its cylinder may be breathed, at the limit its role gives under
 * [conditions]. A mix with no oxygen may be breathed nowhere, so any line on one is too deep.
 */
fun tooDeepFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean {
    if (conditions == null) return false
    val breathed = shaping.gases.getOrNull(leg.gas) ?: return false
    val gas = gasOf(breathed.gas) ?: return false
    val deepest = maximumOperatingDepth(
        gas,
        most = limitOf(breathed.role, conditions),
        density = conditions.density,
        surface = conditions.atmosphericPressure,
    )
        ?: return true
    return maxOf(leg.from, leg.to) > deepest
}

/**
 * Whether [leg] comes shallower than its cylinder may be breathed, where the mix is hypoxic and its
 * oxygen falls below the plan's *pO₂ min*. A mix with no oxygen is caught by [tooDeepFor] already.
 */
fun tooShallowFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean {
    if (conditions == null) return false
    val breathed = shaping.gases.getOrNull(leg.gas) ?: return false
    val gas = gasOf(breathed.gas) ?: return false
    val shallowest =
        minimumOperatingDepth(gas, least = conditions.leastOxygen, density = conditions.density, surface = conditions.atmosphericPressure)
            ?: return false
    return minOf(leg.from, leg.to) < shallowest
}

/** Whether [leg] breathes its cylinder anywhere it may not be breathed, too deep or too shallow. */
fun gasWrongFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean =
    tooDeepFor(leg, shaping, conditions) || tooShallowFor(leg, shaping, conditions)

/**
 * The seconds at which [run] is above the ceiling [evaluated] worked out for it.
 *
 * Compared at the run's own points, as the model compares them when it warns, so a line shown in
 * red and the warning under the plan are one judgement. `LOGIC-37`.
 */
fun aboveCeilingAt(run: Run, evaluated: Evaluated.Done): Set<Int> {
    val ceilings = HashMap<Int, Double>()
    for (at in 0..<evaluated.ceiling.size) {
        val value = (evaluated.ceiling.valueAt(at) as? Element.Usable)?.value as? Number ?: continue
        ceilings[evaluated.ceiling.secondAt(at)] = value.toDouble()
    }
    return run.depth.filter { (second, metres) -> metres < (ceilings[second] ?: 0.0) }.map { it.first }.toSet()
}

/**
 * Whether [leg] is above the ceiling at any of [above]: at a point it reaches, or for a stay at the
 * point it begins from too. A rise begun from a point above the ceiling leaves the fault with the
 * line that reached it.
 */
fun breaksCeiling(leg: Leg, above: Set<Int>): Boolean = above.any {
    it in (leg.begins + 1)..leg.ends || (leg.direction == Direction.STAY && it == leg.begins)
}

/** The runtime a line is shown with: the whole minute it ends in, counted up. */
fun runtimeSaid(leg: Leg): String = "${ceil(leg.ends / SECONDS_IN_MINUTE).toInt()}:"

// clockOf already exists in Evaluation.kt, in this same package, and reads the same way.

/** A rate to a tenth of a metre a minute. */
fun rateSaid(rate: Double): String = plain((rate * 10).roundToInt() / 10.0)

/** How deep [breathed] may be breathed under [conditions], as the table says it, or nothing. */
fun deepestSaid(breathed: Breathed, conditions: Conditions?): String {
    val gas = gasOf(breathed.gas) ?: return ""
    if (conditions == null) return ""
    val most = limitOf(breathed.role, conditions)
    val deepest = maximumOperatingDepth(gas, most = most, density = conditions.density, surface = conditions.atmosphericPressure)
        ?: return ""
    return "${plain((deepest * 10).roundToInt() / 10.0)} m"
}

/** The cylinder [typed] names, or null where it names none. */
private fun gasOf(typed: String): Gas? = try {
    Gas.parse(typed)
} catch (refused: ValueFormatException) {
    null
} catch (refused: IllegalArgumentException) {
    null
}

// --- Reading a setting.

/** A percentage from 1 to 100 as a proportion, or absent where [typed] is not one. `GUI-41`. */
private fun percentageOf(typed: String): Double? =
    typed.trim().removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it >= 1 && it <= PERCENT }
        ?.let { it / PERCENT }

private fun positiveOf(typed: String): Double? = typed.trim().toDoubleOrNull()?.takeIf { it > 0 }

/** What to say of a gradient factor that will not do. */
private fun factorWrong(which: String, typed: String): String = numberWrong("GF $which", "1 to 100 %", typed)

/** What to say of a setting that will not do: that it is missing, or what it should be instead. */
private fun numberWrong(what: String, rule: String, typed: String): String =
    if (typed.isBlank()) "$what is missing" else "$what should be $rule, not ${said(typed)}"

/** Something typed, quoted, or *nothing* where nothing was. */
internal fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

/**
 * A number as a person writes it: `18`, not `18.0`, and `9.5` where there is a fraction.
 *
 * Not internal: the window's own display code, in `ui`, formats a figure the same way, `GUI-43`.
 */
fun plain(value: Double, decimals: Int = 3): String {
    val scale = 10.0.pow(decimals)
    val rounded = (value * scale).roundToLong() / scale
    return if (rounded == rounded.roundToLong().toDouble()) {
        rounded.roundToLong().toString()
    } else {
        rounded.toString()
    }
}

/** Seconds from `m:ss`, or from a bare number of minutes; absent where it is neither. */
internal fun secondsOf(clock: String): Long? {
    val sign = if (clock.startsWith("-")) -1 else 1
    val parts = clock.removePrefix("-").split(':')
    return when (parts.size) {
        1 -> parts[0].toDoubleOrNull()?.let { kotlin.math.round(it * 60).toLong() * sign }
        2 -> {
            val minutes = parts[0].toLongOrNull() ?: return null
            val seconds = parts[1].toLongOrNull() ?: return null
            if (seconds !in 0..59) return null
            (minutes * 60 + seconds) * sign
        }

        else -> null
    }
}

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0
