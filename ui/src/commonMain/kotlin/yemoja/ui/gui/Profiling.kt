package yemoja.ui.gui

import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.Ascended
import yemoja.logic.Evaluated
import yemoja.logic.Reserve
import yemoja.logic.NumberSetting
import yemoja.logic.Run
import yemoja.logic.SafetyStop
import yemoja.logic.Universe
import yemoja.logic.Settings
import yemoja.logic.Source
import yemoja.logic.completeAscent
import yemoja.logic.densityOfWater
import yemoja.logic.evaluate
import yemoja.logic.lostGasReserve
import yemoja.logic.sharedGasReserve
import yemoja.logic.maximumOperatingDepth
import yemoja.logic.minimumOperatingDepth
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * A dive planned in the Calculations tab, belonging to no dive: segments, settings and gases in,
 * the way up and what it costs out.
 *
 * The model is the logic layer's, `LOGIC-37`, reached through a `Run` rather than through an item,
 * so a plan typed here and a plan on a dive are answered by one walk. Nothing here is stored.
 *
 * See ../../../../../../gui/doc.md — `GUI-43`.
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
internal data class Segment(
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
internal enum class Role(val label: String) {

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
internal data class Breathed(
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
 * Shaping is the plan form while the tab holds it: the lines, the settings and the cylinders.
 *
 * Each setting is typed here for this plan alone, starting from what the settings hold. `GUI-42`.
 *
 * Not immutable.
 */
internal class Shaping {
    val segments = mutableStateListOf(Segment())

    /** The cylinders, in the order they are listed, which is the order they are named in. */
    val gases = mutableStateListOf(Breathed())

    var gradientLow: String by mutableStateOf("")
    var gradientHigh: String by mutableStateOf("")
    var bottomOxygen: String by mutableStateOf("")
    var decoOxygen: String by mutableStateOf("")
    var leastOxygen: String by mutableStateOf("")
    var descentRate: String by mutableStateOf("")
    var ascentRate: String by mutableStateOf("")
    var safetyDepth: String by mutableStateOf("")
    var safetyMinutes: String by mutableStateOf("")
    var lastStop: String by mutableStateOf("")
    var panicFactor: String by mutableStateOf("")

    /** Minutes the gas reserve spends at the depth trouble starts before the way up begins. */
    var problemMinutes: String by mutableStateOf("")

    /** Whether the gas reserve tries losing a cylinder: false where *Gas lost* says *None*. */
    var lostGasScenario: Boolean by mutableStateOf(true)

    /**
     * The cylinder the lost-gas scenario loses, by its place in the list, or null for the first
     * deco cylinder. One, since losing two cylinders at once is not a scenario anybody plans for.
     */
    var lostGas: Int? by mutableStateOf(null)

    /** Whether the gas reserve tries a buddy out of gas, sharing this diver's. */
    var sharedScenario: Boolean by mutableStateOf(true)

    /** In the words `water_type` uses. */
    var water: String by mutableStateOf(Settings.DEFAULT_WATER_TYPE.default)

    /** When the plan begins, as typed: a date such as `2026-10-03`, and a time such as `14:30`. */
    var startDate: String by mutableStateOf("")
    var startTime: String by mutableStateOf("")

    /** The earlier run the plan follows, or null for none, which is where it starts. `GUI-43`. */
    var following: Following? by mutableStateOf(null)

    /** Whether the form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false
}

/** Fills every setting of the plan from [settings], or from the defaults where there is no logbook. */
internal fun Shaping.prefill(settings: Settings?) {
    fun shown(setting: NumberSetting): String =
        shownOf(setting, if (settings == null) setting.default else settings.number(setting))
    gradientLow = shown(Settings.DEFAULT_GF_LOW)
    gradientHigh = shown(Settings.DEFAULT_GF_HIGH)
    bottomOxygen = shown(Settings.DEFAULT_BOTTOM_PO2)
    decoOxygen = shown(Settings.DEFAULT_DECO_PO2)
    leastOxygen = shown(Settings.DEFAULT_MIN_PO2)
    descentRate = shown(Settings.DEFAULT_DESCENT_RATE)
    ascentRate = shown(Settings.DEFAULT_ASCENT_RATE)
    safetyDepth = shown(Settings.DEFAULT_SAFETY_STOP_DEPTH)
    safetyMinutes = shown(Settings.DEFAULT_SAFETY_STOP_DURATION)
    lastStop = shown(Settings.DEFAULT_LAST_STOP)
    panicFactor = shown(Settings.DEFAULT_PANIC_FACTOR)
    problemMinutes = shown(Settings.DEFAULT_PROBLEM_SOLVING_TIME)
    water = settings?.choice(Settings.DEFAULT_WATER_TYPE) ?: Settings.DEFAULT_WATER_TYPE.default
    prefilled = true
}

/** What the cylinder at [index] is called, which is what a reader sees of a key. */
internal fun gasLabelOf(index: Int): String = "Gas ${index + 1}"

/**
 * What a line of the runtime calls the cylinder at [index]: its number in the list and what is in
 * it, so a reader choosing one need not look across at the gases.
 *
 * Example: `2: EAN50`, or `2` alone while its mix is still blank.
 */
internal fun gasChoiceOf(shaping: Planned, index: Int): String {
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
internal fun prettyGasOf(typed: String): String {
    val written = gasOf(typed)?.toString() ?: return typed
    return if (written.equals(typed, ignoreCase = true)) written else typed
}

/** The key the cylinder at [index] sits under, as a dive's own cylinders sit under keys. */
internal fun gasKeyOf(index: Int): String = "g${index + 1}"

/** Which cylinder [key] is, by its place in the list the keys were minted from. */
internal fun gasIndexOf(key: String): Int = (key.removePrefix("g").toIntOrNull() ?: 1) - 1

// --- Changing the lists.

/** Puts an empty line directly below the line at [index]. */
internal fun Shaping.addSegment(index: Int) {
    segments.add(index + 1, Segment())
}

/** Takes out the line at [index], unless it is the last: a plan always has a line to type in. */
internal fun Shaping.removeSegment(index: Int) {
    if (segments.size > 1) segments.removeAt(index)
}

/**
 * Puts a deco cylinder directly below the one at [index].
 *
 * The lines naming a cylinder further down the list are renumbered with it, so each still names
 * the cylinder it named.
 */
internal fun Shaping.addGas(index: Int) {
    gases.add(index + 1, Breathed(role = Role.DECO))
    renumber { if (it > index) it + 1 else it }
}

/** Every cylinder a typed line breathes, whether it names it or follows the line above. */
internal fun Shaping.breathed(): Set<Int> {
    val breathed = HashSet<Int>()
    var gas = 0
    for (segment in segments) {
        gas = segment.gas ?: gas
        if (isBlank(segment)) continue
        breathed += gas
    }
    return breathed
}

/**
 * Why the cylinder at [index] cannot be taken out, or null where it can.
 *
 * **One that is breathed stays.** Taking it out would change the dive behind the reader's back, so
 * they change the lines first. A cylinder only the worked-out ascent breathes can go, and the
 * ascent is worked out again without it.
 */
internal fun Shaping.keptBecause(index: Int): String? = when {
    gases.size == 1 -> "The last gas cannot be removed"
    index in breathed() -> "${gasLabelOf(index)} cannot be removed: line ${firstLineOn(index) + 1} uses it"
    else -> null
}

/** Where in the list the first typed line breathing the cylinder at [index] is. */
private fun Shaping.firstLineOn(index: Int): Int {
    var gas = 0
    for ((at, segment) in segments.withIndex()) {
        gas = segment.gas ?: gas
        if (!isBlank(segment) && gas == index) return at
    }
    return 0
}

/** Takes out the cylinder at [index] where nothing keeps it, renumbering the lines after it. */
internal fun Shaping.removeGas(index: Int) {
    if (keptBecause(index) != null) return
    gases.removeAt(index)
    // Only an empty line can still name it, and that line follows the one above instead.
    renumber {
        when {
            it == index -> null
            it > index -> it - 1
            else -> it
        }
    }
}

private fun Shaping.renumber(moved: (Int) -> Int?) {
    for ((at, segment) in segments.withIndex()) {
        val gas = segment.gas ?: continue
        segments[at] = segment.copy(gas = moved(gas))
    }
    // A lost cylinder taken out goes back to the first deco cylinder.
    lostGas = lostGas?.let(moved)
}

/**
 * Whether nothing that times or places [segment] is typed.
 *
 * A gas alone is not a line of the run, but the lines below it still follow it: a reader who
 * chooses the gas first and the depth after has already said what the next lines breathe.
 */
private fun isBlank(segment: Segment): Boolean =
    segment.depth.isBlank() && segment.duration.isBlank() && segment.rate.isBlank()

// --- Reading what was typed.

/** Direction is which way a line goes, which the arrow before it says. */
internal enum class Direction(val arrow: String) {
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
internal data class Leg(
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
 * The legs [segments] lay out, and why the first that will not read does not, or null.
 *
 * A line that times itself by neither a duration nor a rate travels at [descentRate] or
 * [ascentRate]. Those are null where the settings will not read, and such a line is then untimed:
 * the settings say why, so the line need not.
 */
internal fun laidOf(
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
internal fun durationOf(typed: String): Int? = secondsOf(typed.trim())?.takeIf { it > 0 }?.toInt()

private fun durationWrong(line: String, typed: String): String =
    "$line duration should be m:ss or minutes, such as 2:13 or 25, not ${said(typed)}"

/**
 * Conditions are the plan's settings once read.
 *
 * Immutable.
 */
internal class Conditions(
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
    /** Kilograms a cubic metre. */
    val density: Double,
)

/** The plan's settings, or why the first that will not read does not. */
internal fun conditionsOf(shaping: Planned): Pair<Conditions?, String?> {
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
        density = density,
    ) to null
}

/** The source the cylinder [breathed] is, held to the limit its role gives it under [conditions]. */
internal fun sourceOf(breathed: Breathed, gas: Gas, conditions: Conditions): Source = Source(
    gas = gas,
    sac = breathed.sac.trim().toDoubleOrNull(),
    volume = breathed.size.trim().toDoubleOrNull(),
    fill = breathed.fill.trim().toDoubleOrNull(),
    mostOxygen = limitOf(breathed.role, conditions),
    ascentMayChoose = breathed.role != Role.BAILOUT,
    leastOxygen = conditions.leastOxygen,
)

/** The most oxygen a cylinder of [role] is breathed at under [conditions], in bar. */
internal fun limitOf(role: Role, conditions: Conditions): Double =
    if (role == Role.DECO) conditions.decoOxygen else conditions.bottomOxygen

/** Shaped is a run built from what was typed, or why there is none yet, with the lines read so far. */
internal sealed class Shaped {

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
 * The run carries nothing: a plan in the calculations belongs to no dive and so follows none.
 */
internal fun shapedOf(shaping: Planned, universe: Universe? = null): Shaped {
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
    val left = (followed as? Followed.After)?.residual
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
            safetyStop = if (conditions.safetySeconds > 0) {
                SafetyStop(conditions.safetyDepth, conditions.safetySeconds)
            } else {
                null
            },
            ascentRate = conditions.ascentRate,
            carried = left?.tissues,
            oxygenCarried = left?.oxygen,
        ),
        legs,
        conditions,
    )
}

/** Worked is what the model makes of a ready run: the way up it adds, and the whole dive evaluated. */
internal sealed class Worked {

    class Done(val tail: List<Leg>, val whole: Run, val evaluated: Evaluated.Done) : Worked()

    class Refused(val reason: String) : Worked()
}

/** The ascent [ready] is completed with, and what the model makes of the dive with it on the end. */
internal fun workedOf(ready: Shaped.Ready): Worked {
    val conditions = ready.conditions
    val ascended = when (val ascent = completeAscent(ready.run, conditions.ascentRate, conditions.lastStop)) {
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
internal fun Planned.lostIndex(): Int? =
    lostGas?.takeIf { it in gases.indices } ?: gases.indexOfFirst { it.role == Role.DECO }.takeIf { it >= 0 }

/**
 * Whether the lost-gas scenario is tried: a cylinder is lost, and *None* was not chosen. A plan with
 * no deco gas and nothing chosen loses none, so its reserve has no lost-gas scenario to try.
 */
internal fun Planned.lostGasTried(): Boolean = lostGasScenario && lostIndex() != null

/** Scenario is one way a dive can go wrong that the gas reserve is kept back for. `LOGIC-40`. */
internal enum class Scenario(val label: String, val tip: String) {

    /** The cylinders ticked *Lost* are gone, and the way up is to the surface at the usual rate. */
    LOST_GAS("Lost", PlannerTips.LOST_GAS),

    /** A buddy has lost their bottom gas, and the two share this diver's up to a deco gas. */
    SHARED("Buddy out of gas", PlannerTips.SHARED),
}

/** Reckoning is what one scenario of the gas reserve came to, or why it came to nothing. */
internal sealed class Reckoning {

    class Done(val reserve: Reserve.Done) : Reckoning()

    class Wrong(val reason: String) : Reckoning()
}

/**
 * Reckoned is what each scenario of the gas reserve came to, with null for one switched off.
 *
 * Immutable.
 */
internal class Reckoned(val scenarios: Map<Scenario, Reckoning?>) {

    /** The scenarios that were worked out, each with its reserve. */
    val done: Map<Scenario, Reserve.Done>
        get() = scenarios.mapNotNull { (scenario, it) -> (it as? Reckoning.Done)?.let { scenario to it.reserve } }.toMap()
}

/**
 * The gas [done] must keep back in each scenario [shaping] has switched on.
 *
 * The stress factor is read apart from the other settings, so one typed wrong leaves the shared
 * scenario unsaid and everything else answered. `LOGIC-40`.
 */
internal fun reckonedOf(shaping: Planned, done: Worked.Done, conditions: Conditions): Reckoned {
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
        reckoning(lostGasReserve(done.whole, setOf(gasKeyOf(it)), conditions.ascentRate, conditions.lastStop, problem))
    }
    val shared = if (shaping.sharedScenario) {
        val factor = shaping.panicFactor.trim().toDoubleOrNull()?.takeIf { it >= 1 }
        if (factor == null) {
            Reckoning.Wrong(numberWrong("Panic stress factor", "1 or more", shaping.panicFactor))
        } else {
            val deco = keys.filter { shaping.gases[it].role == Role.DECO }.map { gasKeyOf(it) }.toSet()
            reckoning(sharedGasReserve(done.whole, deco, factor, conditions.ascentRate, conditions.lastStop, problem))
        }
    } else {
        null
    }
    return Reckoned(mapOf(Scenario.LOST_GAS to lostGas, Scenario.SHARED to shared))
}

/**
 * What the cylinder under [key] must still hold: the most any scenario switched on asks of it at
 * its own worst moment, since the cylinder has to meet each of them.
 *
 * In bar, rounded up, or in litres where nobody said how big it is. Nothing for a cylinder no
 * scenario breathes from.
 */
internal fun minimumSaid(reckoned: Reckoned, key: String): String {
    val done = reckoned.done.values
    done.mapNotNull { it.reserve[key] }.maxOrNull()?.let { return "${ceil(it).toInt()} bar" }
    return done.mapNotNull { it.needed[key] }.maxOrNull()?.let { "${ceil(it).toInt()} L" }.orEmpty()
}

/** Whether the cylinder under [key] falls short in any scenario switched on. */
internal fun isShort(reckoned: Reckoned, key: String): Boolean =
    reckoned.done.values.any { it.shortfall?.source == key }

/** When a scenario's worst moment is, and how deep. */
internal fun worstSaid(reserve: Reserve.Done): String =
    "${clockOf(reserve.worst)} (${plain(reserve.worstMetres)} m)"

/**
 * Why no reserve can be worked out: every cylinder the reserve may breathe that has no rate, each
 * with everything it lacks. Null where none lacks a rate.
 *
 * Example: `Cannot be calculated (missing for Gas 1: SAC, volume, start pressure)`.
 */
internal fun missingSaid(shaping: Planned): String? {
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
 * What one scenario came to, in a sentence: what each cylinder needs, when, and what it assumes.
 *
 * A scenario that needs nothing says so, and why where the reason is a deco gas the buddy can go
 * to at once, rather than a worst moment at the surface that means nothing.
 *
 * Example: `Gas 1 needs 54 bar at 25:00 (40 m), surfacing without Gas 2 at normal SAC`.
 */
internal fun scenarioSaid(scenario: Scenario, reserve: Reserve.Done, shaping: Planned): String {
    val needs = reserve.needed.filterValues { it > 0 }.keys.map { key ->
        val held = reserve.reserve[key]?.let { "${ceil(it).toInt()} bar" }
            ?: "${ceil(reserve.needed.getValue(key)).toInt()} L"
        "${gasLabelOf(gasIndexOf(key))} needs $held"
    }
    if (needs.isEmpty()) {
        return when (scenario) {
            Scenario.LOST_GAS -> "No reserve needed"
            Scenario.SHARED -> decoReachedSaid(shaping)
                ?.let { "No sharing needed: each diver switches to $it at once" } ?: "No sharing needed"
        }
    }
    val held = problemSecondsOf(shaping)?.takeIf { it > 0 }?.let { clockOf(it) }
    val assumed = when (scenario) {
        Scenario.LOST_GAS -> {
            val lost = shaping.lostIndex()?.let { gasLabelOf(it) } ?: "the lost gas"
            (held?.let { "$it at depth, then " } ?: "") + "surfacing without $lost at normal SAC"
        }
        Scenario.SHARED -> "two divers sharing " + (held?.let { "$it at depth, then " } ?: "") +
            "${upToSaid(reserve.upTo)}, each at ${shaping.panicFactor.trim()} × SAC"
    }
    return "${needs.joinToString(" and ")} at ${worstSaid(reserve)}, $assumed"
}

/** The problem-solving time [shaping] asks for, in whole seconds, or null where it will not read. */
internal fun problemSecondsOf(shaping: Planned): Int? =
    shaping.problemMinutes.trim().toDoubleOrNull()?.takeIf { it >= 0 }?.let { (it * SECONDS_IN_MINUTE).roundToInt() }

/** The deco cylinder a buddy goes to first, being the one breathable deepest, as its line names it. */
private fun decoReachedSaid(shaping: Planned): String? {
    val conditions = conditionsOf(shaping).first ?: return null
    return shaping.gases.withIndex().filter { it.value.role == Role.DECO }
        .maxByOrNull { (_, breathed) ->
            gasOf(breathed.gas)?.let {
                maximumOperatingDepth(it, most = conditions.decoOxygen, density = conditions.density)
            } ?: -1.0
        }?.let { gasChoiceOf(shaping, it.index) }
}

// To a tenth, as the MOD beside the deco gas is, so the two read as the same depth.
private fun upToSaid(metres: Double?): String =
    if (metres == null || metres <= 0) "to the surface" else "to ${plain((metres * 10).roundToInt() / 10.0)} m"

/** Where a cylinder first holds less than [scenario] needs from there, or null where none does. */
internal fun shortfallSaid(scenario: Scenario, reserve: Reserve.Done): String? = reserve.shortfall?.let {
    // A gauge the plan has already run below nought is empty, not a negative pressure.
    val held = floor(it.left).toInt().coerceAtLeast(0)
    val needs = ceil(it.needed).toInt()
    val why = when (scenario) {
        Scenario.LOST_GAS -> "surfacing without the lost gas"
        Scenario.SHARED -> "two divers sharing ${upToSaid(it.upTo)}"
    }
    "${clockOf(it.second)} ${gasLabelOf(gasIndexOf(it.source))}: $held bar should be at least $needs bar ($why)"
}

/** The cylinders a reserve breathes that have no size or no start pressure, so cannot be checked. */
internal fun uncheckedSaid(reckoned: Reckoned, shaping: Planned): String? {
    val unchecked = reckoned.done.values.filter { !it.judged }.flatMap { it.needed.keys }
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
internal fun withAscent(run: Run, ascent: Ascended.Done): Run = Run(
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
)

/**
 * The lines the worked-out [ascent] is shown as, below the typed ones.
 *
 * **The ascent does not repeat the point it leaves from**, so the run's own last point begins the
 * first line. The model writes a point a minute while it holds a stop, and those minutes are one
 * line. A rise through several depths is one line too, unless a gas is switched on the way, since
 * a switch is what a reader has to see.
 */
internal fun tailOf(run: Run, ascent: Ascended.Done): List<Leg> {
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
internal fun tooDeepFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean {
    if (conditions == null) return false
    val breathed = shaping.gases.getOrNull(leg.gas) ?: return false
    val gas = gasOf(breathed.gas) ?: return false
    val deepest = maximumOperatingDepth(gas, most = limitOf(breathed.role, conditions), density = conditions.density)
        ?: return true
    return maxOf(leg.from, leg.to) > deepest
}

/**
 * Whether [leg] comes shallower than its cylinder may be breathed, where the mix is hypoxic and its
 * oxygen falls below the plan's *pO₂ min*. A mix with no oxygen is caught by [tooDeepFor] already.
 */
internal fun tooShallowFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean {
    if (conditions == null) return false
    val breathed = shaping.gases.getOrNull(leg.gas) ?: return false
    val gas = gasOf(breathed.gas) ?: return false
    val shallowest = minimumOperatingDepth(gas, least = conditions.leastOxygen, density = conditions.density) ?: return false
    return minOf(leg.from, leg.to) < shallowest
}

/** Whether [leg] breathes its cylinder anywhere it may not be breathed, too deep or too shallow. */
internal fun gasWrongFor(leg: Leg, shaping: Planned, conditions: Conditions?): Boolean =
    tooDeepFor(leg, shaping, conditions) || tooShallowFor(leg, shaping, conditions)

/**
 * The seconds at which [run] is above the ceiling [evaluated] worked out for it.
 *
 * Compared at the run's own points, as the model compares them when it warns, so a line shown in
 * red and the warning under the plan are one judgement. `LOGIC-37`.
 */
internal fun aboveCeilingAt(run: Run, evaluated: Evaluated.Done): Set<Int> {
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
internal fun breaksCeiling(leg: Leg, above: Set<Int>): Boolean = above.any {
    it in (leg.begins + 1)..leg.ends || (leg.direction == Direction.STAY && it == leg.begins)
}

/** The runtime a line is shown with: the whole minute it ends in, counted up. */
internal fun runtimeSaid(leg: Leg): String = "${ceil(leg.ends / SECONDS_IN_MINUTE).toInt()}:"

/** A length of time as a clock reads it, minutes and seconds. */
internal fun clockOf(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** A rate to a tenth of a metre a minute. */
internal fun rateSaid(rate: Double): String = plain((rate * 10).roundToInt() / 10.0)

/** How deep [breathed] may be breathed under [conditions], as the table says it, or nothing. */
internal fun deepestSaid(breathed: Breathed, conditions: Conditions?): String {
    val gas = gasOf(breathed.gas) ?: return ""
    if (conditions == null) return ""
    val most = limitOf(breathed.role, conditions)
    val deepest = maximumOperatingDepth(gas, most = most, density = conditions.density) ?: return ""
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

// --- The form.

/**
 * The plan form: the lines and the way up on the left, the settings and the cylinders on the right,
 * and what the whole dive costs, what the model objects to, and its graph below. `GUI-43`.
 *
 * **Everything answers as it is typed.** The way up is worked out from the last typed line to the
 * surface and shown in italics beneath it. A reader who types the way up themselves shortens it,
 * and one who types the whole dive leaves nothing to add.
 */
@Composable
internal fun PlanForm(
    shaping: Shaping,
    settings: Settings?,
    scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)? = null,
    /** The logbook a plan may follow an earlier run from, or absent where none is open. */
    universe: Universe? = null,
    /** The row the plan is saved from, under its heading. `GUI-44`. */
    saving: @Composable () -> Unit = {},
) {
    remember(shaping, settings) {
        if (!shaping.prefilled) shaping.prefill(settings)
        shaping
    }
    // The form as a plan, read once: everything below asks the same description the same question.
    val planned = shaping.described()
    val shaped = shapedOf(planned, universe)
    val worked = (shaped as? Shaped.Ready)?.let { workedOf(it) }
    val done = worked as? Worked.Done
    val conditions = conditionsOf(planned).first
    val reckoned = if (done != null && conditions != null) reckonedOf(planned, done, conditions) else null
    Heading("Dive plan")
    saving()
    StartRow(shaping, universe, followedOf(planned, universe))
    // The runtime's height is the zone's, and the gases take what the settings leave of it, so the
    // two columns end on one line however many cylinders there are.
    Row(
        modifier = Modifier.fillMaxWidth().height(ZONE),
        horizontalArrangement = Arrangement.spacedBy(GAP * 2),
    ) {
        Column(modifier = Modifier.width(RUNTIME_BOX).fillMaxHeight()) {
            Caption("Runtime")
            Scrolling(Modifier.weight(1f).fillMaxWidth().framed().padding(HALF), scrollbar) {
                RuntimeLines(shaping, shaped, done, conditions)
            }
        }
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Caption("Settings")
            Framed { Conditions(shaping) }
            Caption("Contingency")
            Framed { Contingency(shaping, reckoned) }
            Caption("Gases")
            Column(modifier = Modifier.weight(1f).fillMaxWidth().framed().padding(HALF)) {
                CylinderHeadings()
                Scrolling(Modifier.weight(1f).fillMaxWidth(), scrollbar) {
                    Cylinders(shaping, conditions, done, reckoned)
                }
            }
        }
    }
    if (done != null) Figures(done.evaluated)
    when {
        shaped is Shaped.Wrong -> Refused(shaped.reason)
        worked is Worked.Refused -> Refused(worked.reason)
        done != null -> {
            for (finding in findingsSaidOf(done.evaluated, mixedTanksOf(planned))) {
                Warning("${finding.label} ${finding.parts.joinToString("") { it.text }}", finding.wrong)
            }
            for ((scenario, reserve) in reckoned?.done.orEmpty()) {
                shortfallSaid(scenario, reserve)?.let { Warning(it, wrong = true) }
            }
            reckoned?.let { uncheckedSaid(it, planned) }?.let { Warning(it, wrong = false) }
        }
    }
    if (done != null) Graph(done, shaping)
}

/** One line of what the model objects to, in the error colour where it is a fault. */
@Composable
internal fun Warning(text: String, wrong: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(horizontal = GAP, vertical = 2.dp),
    )
}

/** What each cylinder is called where the model warns of it, its mix beside its name: `Gas 2 (EAN50)`. */
private fun mixedTanksOf(shaping: Planned): Map<String, String> =
    shaping.gases.indices.associate { index ->
        val mix = gasChoiceOf(shaping, index).substringAfter(": ", "")
        gasKeyOf(index) to if (mix.isEmpty()) gasLabelOf(index) else "${gasLabelOf(index)} ($mix)"
    }

/** What each cylinder is called, under the key the run holds it by. */
private fun tanksOf(shaping: Planned): Map<String, String> =
    shaping.gases.indices.associate { gasKeyOf(it) to gasLabelOf(it) }

/**
 * The runtime's lines: the typed ones, then the way up the model adds, in italics and without
 * boxes.
 */
@Composable
private fun RuntimeLines(shaping: Shaping, shaped: Shaped, done: Worked.Done?, conditions: Conditions?) {
    val above = done?.let { aboveCeilingAt(it.whole, it.evaluated) }.orEmpty()
    for ((index, segment) in shaping.segments.withIndex()) {
        val leg = shaped.legs.firstOrNull { it.index == index }
        TypedLine(
            shaping,
            index,
            segment,
            leg,
            gasWrong = leg != null && gasWrongFor(leg, shaping.described(), conditions),
            ceilingBroken = leg != null && breaksCeiling(leg, above),
        )
    }
    for (leg in done?.tail.orEmpty()) {
        WorkedLine(
            leg,
            shaping,
            gasWrong = gasWrongFor(leg, shaping.described(), conditions),
            ceilingBroken = breaksCeiling(leg, above),
        )
    }
}

/**
 * [content] scrolled within [modifier]'s bounds, with the platform's bar beside it where it draws
 * one, so what does not fit is reached without the part beside it moving.
 */
@Composable
private fun Scrolling(
    modifier: Modifier,
    scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrolled = rememberScrollState()
    Box(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrolled), content = content)
        scrollbar?.invoke(scrolled, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/**
 * One typed line: its runtime and arrow, worked out; its depth and its duration or rate, typed;
 * its gas, following the line above in italics until a reader chooses one.
 */
@Composable
private fun TypedLine(
    shaping: Shaping,
    index: Int,
    segment: Segment,
    leg: Leg?,
    /** Whether the line goes deeper or shallower than its gas may be breathed. */
    gasWrong: Boolean,
    /** Whether the dive is above the ceiling on this line. */
    ceilingBroken: Boolean,
) {
    val staying = leg?.direction == Direction.STAY
    Row(
        modifier = Modifier.height(ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Explained(PlannerTips.RUNTIME) { Cell(leg?.let { runtimeSaid(it) }.orEmpty(), RUNTIME, TextAlign.End) }
        Explained(PlannerTips.DIRECTION) { Cell(leg?.direction?.arrow.orEmpty(), ARROW, TextAlign.Center) }
        Tipped(PlannerTips.DEPTH, DEPTH) {
            Compact(
                dense = true,
                value = segment.depth,
                onChange = { shaping.segments[index] = segment.copy(depth = it) },
                after = "m",
                wrong = ceilingBroken,
            )
        }
        Tipped(PlannerTips.DURATION, DURATION) {
            Compact(
                dense = true,
                value = segment.duration,
                onChange = { shaping.segments[index] = segment.copy(duration = it, rate = "") },
                hint = if (segment.duration.isBlank() && leg != null) clockOf(leg.seconds) else "",
                derived = true,
            )
        }
        Tipped(PlannerTips.RATE, RATE) {
            Compact(
                dense = true,
                value = if (staying) "" else segment.rate,
                onChange = { shaping.segments[index] = segment.copy(rate = it, duration = "") },
                after = "m/min",
                hint = leg?.rate?.takeIf { segment.rate.isBlank() }?.let { rateSaid(it) }.orEmpty(),
                derived = true,
                enabled = !staying,
            )
        }
        val planned = shaping.described()
        val above = gasAbove(planned, index)
        val shown = segment.gas?.takeIf { it in shaping.gases.indices } ?: above
        Tipped(PlannerTips.GAS, GAS) {
            Pick(
                dense = true,
                chosen = gasChoiceOf(planned, shown),
                options = planned.gases.indices.map { gasChoiceOf(planned, it) },
                italic = segment.gas == null,
                wrong = gasWrong,
            ) { chose ->
                // Choosing what the line above breathes is following it again.
                shaping.segments[index] = segment.copy(gas = if (chose == above) null else chose)
            }
        }
        Explained(PlannerTips.ADD_LINE) {
            IconButton(onClick = { shaping.addSegment(index) }, modifier = Modifier.size(BUTTON)) {
                Icon(Icons.Filled.Add, contentDescription = "Add a line below", modifier = Modifier.size(DENSE_GLYPH), tint = MaterialTheme.colorScheme.outline)
            }
        }
        if (shaping.segments.size > 1) {
            Explained(PlannerTips.REMOVE_LINE) {
                IconButton(onClick = { shaping.removeSegment(index) }, modifier = Modifier.size(BUTTON)) {
                    Icon(Icons.Filled.Close, contentDescription = "Take out this line", modifier = Modifier.size(DENSE_GLYPH), tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}

/** What the lines above [index] breathe at their end, which a line following them breathes too. */
internal fun gasAbove(shaping: Planned, index: Int): Int {
    var gas = 0
    for (segment in shaping.segments.take(index)) {
        gas = segment.gas?.takeIf { it in shaping.gases.indices } ?: gas
    }
    return gas
}

/** One line of the way up the model adds: all of it worked out, so all of it in italics. */
@Composable
private fun WorkedLine(leg: Leg, shaping: Shaping, gasWrong: Boolean, ceilingBroken: Boolean) {
    Explained(PlannerTips.WORKED) {
        Row(
            modifier = Modifier.height(ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HALF),
        ) {
            val italic = calculatedOf(MaterialTheme.typography.bodySmall)
            Cell(runtimeSaid(leg), RUNTIME, TextAlign.End, italic)
            Cell(leg.direction.arrow, ARROW, TextAlign.Center, italic)
            val error = italic.copy(color = MaterialTheme.colorScheme.error)
            Cell("${plain(leg.to)} m", DEPTH, TextAlign.End, if (ceilingBroken) error else italic)
            Cell(clockOf(leg.seconds), DURATION, TextAlign.End, italic)
            Cell(leg.rate?.let { "(${rateSaid(it)} m/min)" }.orEmpty(), RATE, TextAlign.End, italic)
            Cell(gasChoiceOf(shaping.described(), leg.gas), GAS, TextAlign.Start, if (gasWrong) error else italic, padding = GAP)
        }
    }
}

/** A box in a column of the runtime or the cylinders, as wide as the column, saying [tip] while pointed at. */
@Composable
private fun Tipped(tip: String, width: Dp, content: @Composable () -> Unit) {
    Box(modifier = Modifier.width(width)) { Explained(tip, content) }
}

/** A word in a column of the runtime box or the cylinders, as wide as the column. */
@Composable
private fun Cell(
    text: String,
    width: Dp,
    align: TextAlign,
    style: TextStyle = MaterialTheme.typography.bodySmall,
    padding: Dp = 0.dp,
) {
    Text(
        text = text,
        style = style,
        textAlign = align,
        modifier = Modifier.width(width).padding(horizontal = padding),
    )
}

/** One part of the form in a box of its own, as the runtime is. */
@Composable
private fun Framed(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().framed().padding(HALF), content = content)
}

/** The line round each part of the form. */
@Composable
private fun Modifier.framed(): Modifier = border(FRAME, MaterialTheme.colorScheme.outlineVariant, SHAPE)

/**
 * The plan's settings, in titled sections stacked in two columns, each starting from what the
 * settings hold and belonging to this plan alone. A safety stop of nought minutes greys its depth,
 * there being no stop to place.
 */
@Composable
private fun Conditions(shaping: Shaping) {
    val none = shaping.safetyMinutes.trim().toDoubleOrNull() == 0.0
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GAP)) {
            Section("General") {
                Setting("Descent rate", PlannerTips.DESCENT_RATE, shaping.descentRate, "m/min") { shaping.descentRate = it }
                Setting("Ascent rate", PlannerTips.ASCENT_RATE, shaping.ascentRate, "m/min") { shaping.ascentRate = it }
                Labelled("Water", PlannerTips.WATER) {
                    // As wide as the boxes above it, a setting being a setting whether it is
                    // typed or chosen.
                    Box(modifier = Modifier.width(SETTING)) {
                        Pick(
                            dense = true,
                            chosen = wordSaid(shaping.water),
                            options = Settings.DEFAULT_WATER_TYPE.choices.map { wordSaid(it) },
                        ) { shaping.water = Settings.DEFAULT_WATER_TYPE.choices[it] }
                    }
                }
            }
            Section("Gas") {
                Setting("pO₂ max bottom", PlannerTips.BOTTOM_OXYGEN, shaping.bottomOxygen, "bar") { shaping.bottomOxygen = it }
                Setting("pO₂ max deco", PlannerTips.DECO_OXYGEN, shaping.decoOxygen, "bar") { shaping.decoOxygen = it }
                Setting("pO₂ min", PlannerTips.LEAST_OXYGEN, shaping.leastOxygen, "bar") { shaping.leastOxygen = it }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(GAP)) {
            Section("Algorithm") {
                // Bühlmann is the only model until another can be chosen here.
                Labelled("Model", PlannerTips.MODEL) {
                    Text("Bühlmann ZH-L16C", style = MaterialTheme.typography.bodySmall)
                }
                Setting("GF low", PlannerTips.GF_LOW, shaping.gradientLow, "%") { shaping.gradientLow = it }
                Setting("GF high", PlannerTips.GF_HIGH, shaping.gradientHigh, "%") { shaping.gradientHigh = it }
            }
            Section("Stops") {
                Setting("Last stop", PlannerTips.LAST_STOP, shaping.lastStop, "m") { shaping.lastStop = it }
                Setting("Safety stop depth", PlannerTips.SAFETY_DEPTH, shaping.safetyDepth, "m", enabled = !none) { shaping.safetyDepth = it }
                Setting("Safety stop duration", PlannerTips.SAFETY_DURATION, shaping.safetyMinutes, "min") { shaping.safetyMinutes = it }
            }
        }
    }
}

/**
 * The gas reserve's box: its settings on the left, and beside them a line for each scenario with
 * what it came to. `LOGIC-40`.
 *
 * The settings and what they decide sit together, so a reader changing one sees the other move.
 */
@Composable
private fun Contingency(shaping: Shaping, reckoned: Reckoned?) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Column {
            Setting("Panic stress factor", PlannerTips.PANIC_FACTOR, shaping.panicFactor, "× SAC") { shaping.panicFactor = it }
            Setting("Problem solving time", PlannerTips.PROBLEM_SOLVING, shaping.problemMinutes, "min") { shaping.problemMinutes = it }
        }
        Scenarios(reckoned, shaping, Modifier.weight(1f))
    }
}

/** A group of settings under a small title of its own. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.outline,
        )
        content()
    }
}

@Composable
private fun Setting(label: String, tip: String, value: String, after: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Labelled(label, tip) {
        Box(modifier = Modifier.width(SETTING)) {
            Compact(value = value, onChange = onChange, after = after, enabled = enabled, dense = true)
        }
    }
}

@Composable
private fun Labelled(label: String, tip: String, content: @Composable () -> Unit) {
    Explained(tip) {
        Row(
            modifier = Modifier.height(ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(SETTING_LABEL),
            )
            content()
        }
    }
}

/**
 * The cylinders, a line each: what it is, what it is for, what it holds and how fast it is
 * breathed, then how deep it may go, and what the plan takes from it and leaves in it.
 *
 * How deep is the model's own figure at the limit the role gives, so it agrees with the ascent's
 * choice and with the warning. `LOGIC-39`.
 */
@Composable
private fun Cylinders(shaping: Shaping, conditions: Conditions?, done: Worked.Done?, reckoned: Reckoned?) {
    for ((index, breathed) in shaping.gases.withIndex()) {
        val key = gasKeyOf(index)
        Row(
            modifier = Modifier.height(ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HALF),
        ) {
            Explained(PlannerTips.NUMBER) { Cell("${index + 1}", INDEX, TextAlign.End) }
            Tipped(PlannerTips.MIX, MIX) {
                Compact(dense = true, value = breathed.gas, onChange = { shaping.gases[index] = breathed.copy(gas = prettyGasOf(it)) })
            }
            Tipped(PlannerTips.ROLE, ROLE) {
                Pick(
                    dense = true,
                    chosen = breathed.role.label,
                    options = Role.entries.map { it.label },
                ) { shaping.gases[index] = breathed.copy(role = Role.entries[it]) }
            }
            Tipped(PlannerTips.VOLUME, VOLUME) {
                Compact(dense = true, value = breathed.size, onChange = { shaping.gases[index] = breathed.copy(size = it) }, after = "L")
            }
            Tipped(PlannerTips.START, PRESSURE) {
                Compact(dense = true, value = breathed.fill, onChange = { shaping.gases[index] = breathed.copy(fill = it) }, after = "bar")
            }
            Tipped(PlannerTips.SAC, SAC) {
                Compact(dense = true, value = breathed.sac, onChange = { shaping.gases[index] = breathed.copy(sac = it) }, after = "L/min")
            }
            val worked = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
            Explained(PlannerTips.MOD) { Cell(deepestSaid(breathed, conditions), FIGURED, TextAlign.End, worked) }
            Explained(PlannerTips.USED) {
                Cell(done?.evaluated?.gasUsed?.get(key)?.let { "${it.roundToInt()} L" }.orEmpty(), FIGURED, TextAlign.End, worked)
            }
            Explained(PlannerTips.END) {
                Cell(done?.evaluated?.pressures?.get(key)?.let { ending(it) }.orEmpty(), FIGURED, TextAlign.End, worked)
            }
            // Red where this is the cylinder that falls short, as a line too deep for its gas is.
            val short = reckoned != null && isShort(reckoned, key)
            Explained(PlannerTips.MINIMUM) {
                Cell(
                    reckoned?.let { minimumSaid(it, key) }.orEmpty(),
                    FIGURED,
                    TextAlign.End,
                    if (short) worked.copy(color = MaterialTheme.colorScheme.error) else worked,
                )
            }
            Explained(PlannerTips.ADD_GAS) {
                IconButton(onClick = { shaping.addGas(index) }, modifier = Modifier.size(BUTTON)) {
                    Icon(Icons.Filled.Add, contentDescription = "Add a gas below", modifier = Modifier.size(DENSE_GLYPH), tint = MaterialTheme.colorScheme.outline)
                }
            }
            // Why a gas cannot be taken out, where a line breathes it.
            val kept = shaping.keptBecause(index)
            Explained(kept ?: PlannerTips.REMOVE_GAS) {
                IconButton(
                    onClick = { shaping.removeGas(index) },
                    enabled = kept == null,
                    modifier = Modifier.size(BUTTON),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Take out ${gasLabelOf(index)}", modifier = Modifier.size(DENSE_GLYPH))
                }
            }
        }
    }
}

/** What each column of the cylinders is, kept above them while they scroll. */
@Composable
private fun CylinderHeadings() {
    Row(horizontalArrangement = Arrangement.spacedBy(HALF)) {
        for ((heading, width, tip) in CYLINDER_COLUMNS) {
            Explained(tip) {
                Cell(heading, width, TextAlign.Start, MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline))
            }
        }
    }
}

/** What the whole dive costs, on one line. */
@Composable
private fun Figures(evaluated: Evaluated.Done) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = GAP),
        horizontalArrangement = Arrangement.spacedBy(GAP * 3),
    ) {
        Figure("CNS", "${evaluated.oxygen.percentCns.toInt()}%", PlannerTips.CNS)
        Figure("OTU", "${evaluated.oxygen.otu.toInt()}", PlannerTips.OTU)
        Figure("No-fly time", evaluated.noFlight?.let { waitSaid(it) } ?: "more than a day", PlannerTips.NO_FLY)
        Figure("Desaturation time", evaluated.desaturation?.let { waitSaid(it) } ?: "more than a day", PlannerTips.DESATURATION)
    }
}

/**
 * The gas reserve's scenarios, a line each: a tick that switches it on, what it asks each cylinder
 * to hold, when its worst moment is, and what it assumes. `LOGIC-40`.
 *
 * The cylinder's own *Minimum* is the most any of them asks, so these lines are where a reader sees
 * which scenario set it.
 */
@Composable
private fun Scenarios(reckoned: Reckoned?, shaping: Shaping, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        for (scenario in Scenario.entries) {
            val reckoning = reckoned?.scenarios?.get(scenario)
            Row(
                modifier = Modifier.heightIn(min = ROW),
                horizontalArrangement = Arrangement.spacedBy(GAP),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The scenario's name with its own switch close beside it, the two in a slot of one
                // width for both lines, so what each scenario came to starts at the same place.
                Row(
                    modifier = Modifier.width(LEAD),
                    horizontalArrangement = Arrangement.spacedBy(HALF),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Explained(scenario.tip) {
                        Text(scenario.label, style = MaterialTheme.typography.bodySmall)
                    }
                    Box {
                        when (scenario) {
                            // Which cylinder is lost, the first deco cylinder until one is chosen, and
                            // None for no lost-gas scenario at all: the choice is the scenario's switch.
                            Scenario.LOST_GAS -> Explained(PlannerTips.GAS_LOST) {
                                Box(modifier = Modifier.width(SETTING)) {
                                    val planned = shaping.described()
                                    Pick(
                                        dense = true,
                                        chosen = planned.lostIndex()?.takeIf { planned.lostGasTried() }
                                            ?.let { gasChoiceOf(planned, it) } ?: NO_GAS_LOST,
                                        options = listOf(NO_GAS_LOST) + planned.gases.indices.map { gasChoiceOf(planned, it) },
                                    ) { chosen ->
                                        shaping.lostGasScenario = chosen > 0
                                        if (chosen > 0) shaping.lostGas = chosen - 1
                                    }
                                }
                            }
                            Scenario.SHARED ->
                                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                    Checkbox(
                                        checked = shaping.sharedScenario,
                                        onCheckedChange = { shaping.sharedScenario = it },
                                        // The platform draws its box at one size whatever the slot, so it
                                        // is drawn smaller rather than squeezed into a smaller slot.
                                        modifier = Modifier.size(DENSE_GLYPH).scale(DENSE_CHECK),
                                    )
                                }
                        }
                    }
                }
                val quiet = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
                val said = when {
                    // Before a plan can be worked out there is nothing to say, only the tick.
                    reckoned == null -> ""
                    reckoning == null -> "off"
                    reckoning is Reckoning.Wrong -> reckoning.reason
                    reckoning is Reckoning.Done -> scenarioSaid(scenario, reckoning.reserve, shaping.described())
                    else -> ""
                }
                // Two lines whatever it says, so switching a scenario off or on does not move the
                // gases below: a result at this width takes two, and *off* takes the same room.
                Text(
                    said,
                    style = if (reckoning is Reckoning.Done) MaterialTheme.typography.bodySmall else quiet,
                    minLines = RESULT_LINES,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun Figure(label: String, said: String, tip: String) {
    Explained(tip) {
        Row(horizontalArrangement = Arrangement.spacedBy(HALF)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
            Text(said, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The whole dive drawn as a recording is: depth, the ceiling over it, and the switches on it. */
@Composable
private fun Graph(done: Worked.Done, shaping: Shaping) {
    val tanks = tanksOf(shaping.described())
    val depth = listOfNotNull(
        Line("Depth", done.whole.depth.map { (second, metres) -> Point(second / SECONDS_IN_MINUTE, metres) }),
        ceilingLineOf(done.evaluated),
    )
    val events = done.whole.switches.map { (second, key) ->
        Event(second / SECONDS_IN_MINUTE, tanks[key] ?: key, Marking.SWITCH)
    }
    Graphed(depth, runOverlaysOf(done.evaluated, tanks), events, planned = true, chosenFor = null)
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
private fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0

/**
 * How tall the top of the plan is: about eighteen lines of the runtime, and about four cylinders
 * under the settings, before either scrolls.
 */
private val ZONE = 508.dp

/** How thick the line round each part of the form is. */
private val FRAME = 1.dp

/** How much of its own size a checkbox is drawn at in a dense row: the size of a dense glyph. */
private const val DENSE_CHECK = 0.8f

/** How tall a line of the runtime box and of the gases is. */
private val ROW = 26.dp

private val RUNTIME_BOX = 540.dp
private val RUNTIME = 36.dp
private val ARROW = 16.dp
private val DEPTH = 80.dp
private val DURATION = 76.dp
private val RATE = 110.dp
private val GAS = 110.dp
private val BUTTON = 22.dp

private val SETTING_LABEL = 150.dp
internal val SETTING = 104.dp

private val INDEX = 16.dp
private val MIX = 76.dp
private val ROLE = 84.dp
private val VOLUME = 56.dp
private val PRESSURE = 72.dp
private val SAC = 84.dp
private val FIGURED = 56.dp


/** How many lines a scenario's result is given, whether or not it needs them. */
private const val RESULT_LINES = 2

/** How wide a scenario's name and switch are together, the same for both lines. */
private val LEAD = 140.dp

/** What *Gas lost* offers for losing no gas, which is no lost-gas scenario. */
private const val NO_GAS_LOST = "None"

/** The cylinders' columns, headed, as wide as what sits under them. */
private val CYLINDER_COLUMNS: List<Triple<String, Dp, String>> = listOf(
    Triple("", INDEX, PlannerTips.NUMBER),
    Triple("Gas", MIX, PlannerTips.MIX),
    Triple("Role", ROLE, PlannerTips.ROLE),
    Triple("Volume", VOLUME, PlannerTips.VOLUME),
    Triple("Start", PRESSURE, PlannerTips.START),
    Triple("SAC", SAC, PlannerTips.SAC),
    Triple("MOD", FIGURED, PlannerTips.MOD),
    Triple("Used", FIGURED, PlannerTips.USED),
    Triple("End", FIGURED, PlannerTips.END),
    Triple("Minimum", FIGURED, PlannerTips.MINIMUM),
)
