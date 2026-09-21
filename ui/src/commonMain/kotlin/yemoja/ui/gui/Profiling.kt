package yemoja.ui.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.Ascended
import yemoja.logic.Evaluated
import yemoja.logic.NumberSetting
import yemoja.logic.Run
import yemoja.logic.SafetyStop
import yemoja.logic.Settings
import yemoja.logic.Source
import yemoja.logic.completeAscent
import yemoja.logic.densityOfWater
import yemoja.logic.evaluate
import yemoja.logic.maximumOperatingDepth
import kotlin.math.abs
import kotlin.math.ceil
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
    val gas: String = "air",
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
    var descentRate: String by mutableStateOf("")
    var ascentRate: String by mutableStateOf("")
    var safetyDepth: String by mutableStateOf("")
    var safetyMinutes: String by mutableStateOf("")
    var lastStop: String by mutableStateOf("")

    /** In the words `water_type` uses. */
    var water: String by mutableStateOf(Settings.DEFAULT_WATER_TYPE.default)

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
    descentRate = shown(Settings.DEFAULT_DESCENT_RATE)
    ascentRate = shown(Settings.DEFAULT_ASCENT_RATE)
    safetyDepth = shown(Settings.DEFAULT_SAFETY_STOP_DEPTH)
    safetyMinutes = shown(Settings.DEFAULT_SAFETY_STOP_DURATION)
    lastStop = shown(Settings.DEFAULT_LAST_STOP)
    water = settings?.choice(Settings.DEFAULT_WATER_TYPE) ?: Settings.DEFAULT_WATER_TYPE.default
    prefilled = true
}

/** What the cylinder at [index] is called, which is what a reader sees of a key. */
internal fun gasLabelOf(index: Int): String = "Gas ${index + 1}"

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
        if (isBlank(segment)) continue
        gas = segment.gas ?: gas
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
    gases.size == 1 -> "A plan breathes something, so its last cylinder stays."
    index in breathed() -> "${gasLabelOf(index)} is breathed by a line. Change that line first."
    else -> null
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
}

/** Whether nothing that times or places [segment] is typed. A gas alone is not a line. */
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
        if (isBlank(segment)) continue
        val line = "line ${index + 1}"
        val to = segment.depth.trim().toDoubleOrNull()?.takeIf { it >= 0 }
            ?: return legs to if (segment.depth.isBlank()) {
                "$line needs a depth"
            } else {
                "$line: a depth is metres, nought or more, and ${said(segment.depth)} is not"
            }
        var timedBy: Double? = null
        val seconds = if (to == from) {
            if (segment.duration.isBlank()) return legs to "$line stays at ${plain(to)} m, so it needs a duration"
            durationOf(segment.duration) ?: return legs to durationWrong(line, segment.duration)
        } else if (segment.duration.isNotBlank()) {
            durationOf(segment.duration) ?: return legs to durationWrong(line, segment.duration)
        } else {
            val rate = if (segment.rate.isNotBlank()) {
                segment.rate.trim().toDoubleOrNull()?.takeIf { it > 0 }
                    ?: return legs to "$line: a rate is metres a minute, more than nought, and ${said(segment.rate)} is not"
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
    "$line: a duration is minutes and seconds, as 2:13, or minutes, as 25, and ${said(typed)} is not"

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
internal fun conditionsOf(shaping: Shaping): Pair<Conditions?, String?> {
    val low = percentageOf(shaping.gradientLow) ?: return null to factorWrong("low", shaping.gradientLow)
    val high = percentageOf(shaping.gradientHigh) ?: return null to factorWrong("high", shaping.gradientHigh)
    if (low > high) return null to "the low gradient factor should not be above the high one"
    val bottom = positiveOf(shaping.bottomOxygen)
        ?: return null to numberWrong("pO₂ max bottom", "bar", shaping.bottomOxygen)
    val deco = positiveOf(shaping.decoOxygen)
        ?: return null to numberWrong("pO₂ max deco", "bar", shaping.decoOxygen)
    val descent = positiveOf(shaping.descentRate)
        ?: return null to numberWrong("the descent rate", "metres a minute", shaping.descentRate)
    val ascent = positiveOf(shaping.ascentRate)
        ?: return null to numberWrong("the ascent rate", "metres a minute", shaping.ascentRate)
    val minutes = shaping.safetyMinutes.trim().toDoubleOrNull()?.takeIf { it >= 0 }
        ?: return null to "the safety stop lasts minutes, nought or more, and ${said(shaping.safetyMinutes)} is not"
    val safety = if (minutes == 0.0) {
        0.0
    } else {
        positiveOf(shaping.safetyDepth)
            ?: return null to numberWrong("the safety stop's depth", "metres", shaping.safetyDepth)
    }
    val last = shaping.lastStop.trim().toDoubleOrNull()?.takeIf { it >= 0 }
        ?: return null to "the last stop is metres, nought or more, and ${said(shaping.lastStop)} is not"
    val density = densityOfWater(shaping.water)
        ?: return null to "${said(shaping.water)} is not a water this knows the weight of"
    return Conditions(
        gradientLow = low,
        gradientHigh = high,
        bottomOxygen = bottom,
        decoOxygen = deco,
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
internal fun shapedOf(shaping: Shaping): Shaped {
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
        val gas = gasOf(breathed.gas) ?: return Shaped.Wrong("${gasLabelOf(index)}: ${said(breathed.gas)} is not a gas", legs)
        sources[gasKeyOf(index)] = sourceOf(breathed, gas, conditions)
    }
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
) {
    remember(shaping, settings) {
        if (!shaping.prefilled) shaping.prefill(settings)
        shaping
    }
    val shaped = shapedOf(shaping)
    val worked = (shaped as? Shaped.Ready)?.let { workedOf(it) }
    val done = worked as? Worked.Done
    val conditions = conditionsOf(shaping).first
    Heading("Dive plan")
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP * 2)) {
        Column(modifier = Modifier.width(RUNTIME_BOX)) {
            Runtime(shaping, shaped, done, scrollbar)
        }
        Column(modifier = Modifier.weight(1f)) {
            Conditions(shaping)
            Cylinders(shaping, conditions, done)
        }
    }
    if (done != null) Figures(done.evaluated)
    when {
        shaped is Shaped.Wrong -> Refused(shaped.reason)
        worked is Worked.Refused -> Refused(worked.reason)
        done != null -> for (finding in findingsSaidOf(done.evaluated, tanksOf(shaping))) {
            Text(
                text = "${finding.label}: ${finding.parts.joinToString("") { it.text }}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (finding.wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = GAP, vertical = 2.dp),
            )
        }
    }
    if (done != null) Graph(done, shaping)
}

/** What each cylinder is called, under the key the run holds it by. */
private fun tanksOf(shaping: Shaping): Map<String, String> =
    shaping.gases.indices.associate { gasKeyOf(it) to gasLabelOf(it) }

/**
 * The runtime box: the typed lines, then the way up the model adds, in italics and without boxes.
 *
 * About twelve lines tall, scrolling past that, so the settings and the cylinders beside it stay
 * where they are however long the dive.
 */
@Composable
private fun Runtime(
    shaping: Shaping,
    shaped: Shaped,
    done: Worked.Done?,
    scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)?,
) {
    Caption("Runtime")
    val scrolled = rememberScrollState()
    Box(
        modifier = Modifier.fillMaxWidth().height(ROW * LINES_SHOWN)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SHAPE),
    ) {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(scrolled).padding(HALF)) {
            for ((index, segment) in shaping.segments.withIndex()) {
                val leg = shaped.legs.firstOrNull { it.index == index }
                TypedLine(shaping, index, segment, leg)
            }
            for (leg in done?.tail.orEmpty()) WorkedLine(leg)
        }
        scrollbar?.invoke(scrolled, Modifier.align(Alignment.CenterEnd).fillMaxHeight())
    }
}

/**
 * One typed line: its runtime and arrow, worked out; its depth and its duration or rate, typed;
 * its gas, following the line above in italics until a reader chooses one.
 */
@Composable
private fun TypedLine(shaping: Shaping, index: Int, segment: Segment, leg: Leg?) {
    val staying = leg?.direction == Direction.STAY
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HALF)) {
        Cell(leg?.let { runtimeSaid(it) }.orEmpty(), RUNTIME, TextAlign.End)
        Cell(leg?.direction?.arrow.orEmpty(), ARROW, TextAlign.Center)
        Box(modifier = Modifier.width(DEPTH)) {
            Compact(
                value = segment.depth,
                onChange = { shaping.segments[index] = segment.copy(depth = it) },
                after = "m",
            )
        }
        Box(modifier = Modifier.width(DURATION)) {
            Compact(
                value = segment.duration,
                onChange = { shaping.segments[index] = segment.copy(duration = it, rate = "") },
                hint = if (segment.duration.isBlank() && leg != null) clockOf(leg.seconds) else "",
                derived = true,
            )
        }
        Box(modifier = Modifier.width(RATE)) {
            Compact(
                value = if (staying) "" else segment.rate,
                onChange = { shaping.segments[index] = segment.copy(rate = it, duration = "") },
                after = "m/min",
                hint = leg?.rate?.takeIf { segment.rate.isBlank() }?.let { rateSaid(it) }.orEmpty(),
                derived = true,
                enabled = !staying,
            )
        }
        val above = gasAbove(shaping, index)
        val shown = segment.gas?.takeIf { it in shaping.gases.indices } ?: above
        Box(modifier = Modifier.width(GAS)) {
            Pick(
                chosen = gasLabelOf(shown),
                options = shaping.gases.indices.map { gasLabelOf(it) },
                italic = segment.gas == null,
            ) { chose ->
                // Choosing what the line above breathes is following it again.
                shaping.segments[index] = segment.copy(gas = if (chose == above) null else chose)
            }
        }
        IconButton(onClick = { shaping.addSegment(index) }, modifier = Modifier.size(BUTTON)) {
            Icon(Icons.Filled.Add, contentDescription = "Add a line below", tint = MaterialTheme.colorScheme.outline)
        }
        if (shaping.segments.size > 1) {
            IconButton(onClick = { shaping.removeSegment(index) }, modifier = Modifier.size(BUTTON)) {
                Icon(Icons.Filled.Close, contentDescription = "Take out this line", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

/** What the lines above [index] breathe at their end, which a line following them breathes too. */
private fun gasAbove(shaping: Shaping, index: Int): Int {
    var gas = 0
    for (segment in shaping.segments.take(index)) {
        if (isBlank(segment)) continue
        gas = segment.gas?.takeIf { it in shaping.gases.indices } ?: gas
    }
    return gas
}

/** One line of the way up the model adds: all of it worked out, so all of it in italics. */
@Composable
private fun WorkedLine(leg: Leg) {
    Row(
        modifier = Modifier.height(ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        val italic = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic)
        Cell(runtimeSaid(leg), RUNTIME, TextAlign.End, italic)
        Cell(leg.direction.arrow, ARROW, TextAlign.Center, italic)
        Cell("${plain(leg.to)} m", DEPTH, TextAlign.End, italic)
        Cell(clockOf(leg.seconds), DURATION, TextAlign.End, italic)
        Cell(leg.rate?.let { "(${rateSaid(it)} m/min)" }.orEmpty(), RATE, TextAlign.End, italic)
        Cell(gasLabelOf(leg.gas), GAS, TextAlign.Start, italic, padding = GAP)
    }
}

/** A word in a column of the runtime box or the cylinders, as wide as the column. */
@Composable
private fun Cell(
    text: String,
    width: Dp,
    align: TextAlign,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    padding: Dp = 0.dp,
) {
    Text(
        text = text,
        style = style,
        textAlign = align,
        modifier = Modifier.width(width).padding(horizontal = padding),
    )
}

/** A small heading over one part of the form. */
@Composable
private fun Caption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = HALF),
    )
}

/**
 * The plan's settings, in two columns, each starting from what the settings hold and belonging to
 * this plan alone. A safety stop of nought minutes greys its depth, there being no stop to place.
 */
@Composable
private fun Conditions(shaping: Shaping) {
    Caption("Settings")
    val none = shaping.safetyMinutes.trim().toDoubleOrNull() == 0.0
    Paired(
        first = { Setting("GF low", shaping.gradientLow, "%") { shaping.gradientLow = it } },
        second = { Setting("GF high", shaping.gradientHigh, "%") { shaping.gradientHigh = it } },
    )
    Paired(
        first = { Setting("pO₂ max bottom", shaping.bottomOxygen, "bar") { shaping.bottomOxygen = it } },
        second = { Setting("pO₂ max deco", shaping.decoOxygen, "bar") { shaping.decoOxygen = it } },
    )
    Paired(
        first = { Setting("Descent rate", shaping.descentRate, "m/min") { shaping.descentRate = it } },
        second = { Setting("Ascent rate", shaping.ascentRate, "m/min") { shaping.ascentRate = it } },
    )
    Paired(
        first = { Setting("Safety stop depth", shaping.safetyDepth, "m", enabled = !none) { shaping.safetyDepth = it } },
        second = { Setting("Safety stop duration", shaping.safetyMinutes, "min") { shaping.safetyMinutes = it } },
    )
    Paired(
        first = { Setting("Last stop", shaping.lastStop, "m") { shaping.lastStop = it } },
        second = {
            Labelled("Water") {
                Pick(
                    chosen = wordSaid(shaping.water),
                    options = Settings.DEFAULT_WATER_TYPE.choices.map { wordSaid(it) },
                ) { shaping.water = Settings.DEFAULT_WATER_TYPE.choices[it] }
            }
        },
    )
}

/** Two settings side by side, each taking half the width. */
@Composable
private fun Paired(first: @Composable () -> Unit, second: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Box(modifier = Modifier.weight(1f)) { first() }
        Box(modifier = Modifier.weight(1f)) { second() }
    }
}

@Composable
private fun Setting(label: String, value: String, after: String, enabled: Boolean = true, onChange: (String) -> Unit) {
    Labelled(label) {
        Box(modifier = Modifier.width(SETTING)) {
            Compact(value = value, onChange = onChange, after = after, enabled = enabled)
        }
    }
}

@Composable
private fun Labelled(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(SETTING_LABEL),
        )
        content()
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
private fun Cylinders(shaping: Shaping, conditions: Conditions?, done: Worked.Done?) {
    Caption("Gases")
    Row(horizontalArrangement = Arrangement.spacedBy(HALF)) {
        for ((heading, width) in CYLINDER_COLUMNS) {
            Cell(heading, width, TextAlign.Start, MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.outline))
        }
    }
    for ((index, breathed) in shaping.gases.withIndex()) {
        val key = gasKeyOf(index)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HALF)) {
            Cell("${index + 1}", INDEX, TextAlign.End)
            Box(modifier = Modifier.width(MIX)) {
                Compact(value = breathed.gas, onChange = { shaping.gases[index] = breathed.copy(gas = it) })
            }
            Box(modifier = Modifier.width(ROLE)) {
                Pick(
                    chosen = breathed.role.label,
                    options = Role.entries.map { it.label },
                ) { shaping.gases[index] = breathed.copy(role = Role.entries[it]) }
            }
            Box(modifier = Modifier.width(VOLUME)) {
                Compact(value = breathed.size, onChange = { shaping.gases[index] = breathed.copy(size = it) }, after = "L")
            }
            Box(modifier = Modifier.width(PRESSURE)) {
                Compact(value = breathed.fill, onChange = { shaping.gases[index] = breathed.copy(fill = it) }, after = "bar")
            }
            Box(modifier = Modifier.width(SAC)) {
                Compact(value = breathed.sac, onChange = { shaping.gases[index] = breathed.copy(sac = it) }, after = "L/min")
            }
            val worked = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.outline)
            Cell(deepestSaid(breathed, conditions), FIGURED, TextAlign.End, worked)
            Cell(done?.evaluated?.gasUsed?.get(key)?.let { "${it.roundToInt()} L" }.orEmpty(), FIGURED, TextAlign.End, worked)
            Cell(done?.evaluated?.pressures?.get(key)?.let { ending(it) }.orEmpty(), FIGURED, TextAlign.End, worked)
            IconButton(onClick = { shaping.addGas(index) }, modifier = Modifier.size(BUTTON)) {
                Icon(Icons.Filled.Add, contentDescription = "Add a gas below", tint = MaterialTheme.colorScheme.outline)
            }
            val kept = shaping.keptBecause(index)
            Explained(kept) {
                IconButton(
                    onClick = { shaping.removeGas(index) },
                    enabled = kept == null,
                    modifier = Modifier.size(BUTTON),
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Take out ${gasLabelOf(index)}")
                }
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
        Figure("CNS", "${evaluated.oxygen.percentCns.toInt()}%")
        Figure("OTU", "${evaluated.oxygen.otu.toInt()}")
        Figure("No-fly time", evaluated.noFlight?.let { waitSaid(it) } ?: "more than a day")
        Figure("Desaturation time", evaluated.desaturation?.let { waitSaid(it) } ?: "more than a day")
    }
}

@Composable
private fun Figure(label: String, said: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(HALF)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Text(said, style = MaterialTheme.typography.bodyMedium)
    }
}

/** The whole dive drawn as a recording is: depth, the ceiling over it, and the switches on it. */
@Composable
private fun Graph(done: Worked.Done, shaping: Shaping) {
    val tanks = tanksOf(shaping)
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
private fun factorWrong(which: String, typed: String): String = if (typed.isBlank()) {
    "a plan needs its $which gradient factor, which says how conservative it is"
} else {
    "the $which gradient factor is a percentage from 1 to 100, and ${said(typed)} is not"
}

private fun numberWrong(what: String, unit: String, typed: String): String =
    "$what is $unit, more than nought, and ${said(typed)} is not"

/** Something typed, quoted, or *nothing* where nothing was. */
private fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0

/** How many lines the runtime box shows before it scrolls. */
private const val LINES_SHOWN = 12

/** How tall a line of the runtime box is. */
private val ROW = 40.dp

private val RUNTIME_BOX = 540.dp
private val RUNTIME = 36.dp
private val ARROW = 16.dp
private val DEPTH = 80.dp
private val DURATION = 76.dp
private val RATE = 110.dp
private val GAS = 110.dp
private val BUTTON = 28.dp

private val SETTING_LABEL = 150.dp
private val SETTING = 104.dp

private val INDEX = 16.dp
private val MIX = 84.dp
private val ROLE = 104.dp
private val VOLUME = 64.dp
private val PRESSURE = 80.dp
private val SAC = 96.dp
private val FIGURED = 64.dp

/** The cylinders' columns, headed, as wide as what sits under them. */
private val CYLINDER_COLUMNS: List<Pair<String, Dp>> = listOf(
    "" to INDEX,
    "Gas" to MIX,
    "Role" to ROLE,
    "Volume" to VOLUME,
    "Start" to PRESSURE,
    "SAC" to SAC,
    "MOD" to FIGURED,
    "Used" to FIGURED,
    "End" to FIGURED,
)
