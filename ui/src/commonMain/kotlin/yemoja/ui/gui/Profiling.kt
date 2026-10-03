package yemoja.ui.gui

import yemoja.ui.icons.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
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
import yemoja.logic.SEA_LEVEL_SAID
import yemoja.logic.Breathed
import yemoja.logic.Conditions
import yemoja.logic.Direction
import yemoja.logic.Evaluated
import yemoja.logic.Following
import yemoja.logic.Leg
import yemoja.logic.NumberSetting
import yemoja.logic.Planned
import yemoja.logic.Reckoned
import yemoja.logic.Reckoning
import yemoja.logic.Role
import yemoja.logic.Scenario
import yemoja.logic.Segment
import yemoja.logic.Settings
import yemoja.logic.Shaped
import yemoja.logic.Universe
import yemoja.logic.Worked
import yemoja.logic.aboveCeilingAt
import yemoja.logic.breaksCeiling
import yemoja.logic.clockOf
import yemoja.logic.conditionsOf
import yemoja.logic.deepestSaid
import yemoja.logic.followedOf
import yemoja.logic.gasChoiceOf
import yemoja.logic.gasIndexOf
import yemoja.logic.gasKeyOf
import yemoja.logic.gasLabelOf
import yemoja.logic.needsDuration
import yemoja.logic.gasWrongFor
import yemoja.logic.isShort
import yemoja.logic.lostGasTried
import yemoja.logic.lostIndex
import yemoja.logic.keptSaid
import yemoja.logic.plain
import yemoja.logic.prettyGasOf
import yemoja.logic.rateSaid
import yemoja.logic.reckonedOf
import yemoja.logic.runtimeSaid
import yemoja.logic.scenarioSaid
import yemoja.logic.shapedOf
import yemoja.logic.shortfallSaid
import yemoja.logic.shownOf
import yemoja.logic.uncheckedSaid
import yemoja.logic.workedOf
import kotlin.math.roundToInt

/*
 * The plan form while the tab holds it: the lines, the settings and the cylinders. The model
 * itself — segments, settings and gases in, the way up and what it costs out — moved to the logic
 * layer, `LOGIC-43`, so that something other than this window can ask for the same
 * calculation. What stays here is Compose state (so typing redraws) and the screen that edits it.
 *
 * See ../../../../../../gui/doc.md — `GUI-43`.
 */

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

    /** Whether the way up stops to switch gas where no deco stop is owed. */
    var switchStops: Boolean by mutableStateOf(false)
    var stressFactor: String by mutableStateOf("")

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

    /** In bar, as typed. */
    var atmosphericPressure: String by mutableStateOf(SEA_LEVEL_SAID)

    /** When the plan begins, as typed: a date such as `2026-10-03`, and a time such as `14:30`. */
    var startDate: String by mutableStateOf("")
    var startTime: String by mutableStateOf("")

    /** The earlier run the plan follows, or null for none, which is where it starts. `GUI-43`. */
    var following: Following? by mutableStateOf(null)

    /** Whether the form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false

    /** Which parts of the form a phone has folded away, by caption. `PHONE-2`. */
    val folded = mutableStateSetOf<String>()
}

/** Fills every setting of the plan from [settings], or from the defaults where there is no logbook. */
internal fun Shaping.prefill(settings: Settings?) {
    fun shown(setting: NumberSetting): String =
        shownOf(setting, if (settings == null) setting.default else settings.number(setting))
    gradientLow = shown(Settings.DEFAULT_GRADIENT_FACTOR_LOW)
    gradientHigh = shown(Settings.DEFAULT_GRADIENT_FACTOR_HIGH)
    bottomOxygen = shown(Settings.DEFAULT_PO2_MAX_BOTTOM)
    decoOxygen = shown(Settings.DEFAULT_PO2_MAX_DECO)
    leastOxygen = shown(Settings.DEFAULT_PO2_MIN)
    descentRate = shown(Settings.DEFAULT_DESCENT_RATE)
    ascentRate = shown(Settings.DEFAULT_ASCENT_RATE)
    safetyDepth = shown(Settings.DEFAULT_SAFETY_STOP_DEPTH)
    safetyMinutes = shown(Settings.DEFAULT_SAFETY_STOP_DURATION)
    lastStop = shown(Settings.DEFAULT_LAST_STOP)
    stressFactor = shown(Settings.DEFAULT_STRESS_FACTOR)
    problemMinutes = shown(Settings.DEFAULT_PROBLEM_SOLVING_TIME)
    water = settings?.choice(Settings.DEFAULT_WATER_TYPE) ?: Settings.DEFAULT_WATER_TYPE.default
    atmosphericPressure = shown(Settings.DEFAULT_ATMOSPHERIC_PRESSURE)
    prefilled = true
}

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
    if (LocalCompact.current) {
        // A phone stacks the four, each its own height and each folding away under its caption,
        // in the order a plan is made: what it is dived under, what it is dived on, the dive, and
        // what is kept back. The gases' table scrolls sideways, its columns being wider together
        // than the screen. `PHONE-2`.
        Folding("Settings", shaping.folded) { Framed { Conditions(shaping) } }
        Folding("Gases", shaping.folded) {
            Column(
                modifier = Modifier.fillMaxWidth().framed().padding(HALF)
                    .horizontalScroll(rememberScrollState()),
            ) {
                CylinderHeadings()
                Cylinders(shaping, conditions, done, reckoned)
            }
        }
        // As tall as its lines: the page scrolls, and a box scrolling inside it would be mostly
        // empty for a short dive and a second thing to scroll for a long one.
        Folding("Runtime", shaping.folded) { Framed { RuntimeLines(shaping, shaped, done, conditions) } }
        Folding("Contingency", shaping.folded) { Framed { Contingency(shaping, reckoned) } }
    } else {
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
    }
    if (done != null) Figures(done.evaluated)
    when {
        shaped is Shaped.Wrong -> Refused(shaped.reason)
        worked is Worked.Refused -> Refused(worked.reason)
        done != null -> {
            for (finding in findingsSaidOf(done.evaluated, mixedTanksOf(planned))) {
                Warning("${finding.label}: ${finding.parts.joinToString("") { it.text }}", finding.wrong)
            }
            for ((scenario, reserve) in reckoned?.done.orEmpty()) {
                shortfallSaid(scenario, reserve)?.let { Warning(it, wrong = true) }
            }
            reckoned?.let { uncheckedSaid(it, planned) }?.let { Warning(it, wrong = false) }
        }
    }
    if (done != null) Graph(done, shaping)
}

/**
 * A part of the plan under its caption, which a press folds away and another brings back, so that
 * a phone's one column need not hold all four at once. `PHONE-2`.
 */
@Composable
private fun Folding(title: String, folded: MutableSet<String>, content: @Composable () -> Unit) {
    val away = title in folded
    Row(
        modifier = Modifier.fillMaxWidth().clickable { if (away) folded -= title else folded += title },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (away) Icons.Filled.ArrowRight else Icons.Filled.ArrowDropDown,
            contentDescription = if (away) "unfold" else "fold",
            tint = MaterialTheme.colorScheme.outline,
        )
        Caption(title)
    }
    if (!away) content()
}

/** Two columns of settings side by side, or one above the other on a phone. `PHONE-2`. */
@Composable
private fun Halves(first: @Composable ColumnScope.() -> Unit, second: @Composable ColumnScope.() -> Unit) {
    val spaced = Arrangement.spacedBy(GAP)
    if (LocalCompact.current) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = spaced) {
            first()
            second()
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = spaced) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = spaced, content = first)
        Column(modifier = Modifier.weight(1f), verticalArrangement = spaced, content = second)
    }
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
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val widths = widthsFor(maxWidth)
        Column {
            if (widths.narrow) ColumnHeads(widths)
            for ((index, segment) in shaping.segments.withIndex()) {
                val leg = shaped.legs.firstOrNull { it.index == index }
                TypedLine(
                    shaping,
                    index,
                    segment,
                    leg,
                    gasWrong = leg != null && gasWrongFor(leg, shaping.described(), conditions),
                    ceilingBroken = leg != null && breaksCeiling(leg, above),
                    widths = widths,
                )
            }
            for (leg in done?.tail.orEmpty()) {
                WorkedLine(
                    leg,
                    shaping,
                    gasWrong = gasWrongFor(leg, shaping.described(), conditions),
                    ceilingBroken = breaksCeiling(leg, above),
                    widths = widths,
                )
            }
        }
    }
}

/**
 * Widths is how wide each column of the runtime is drawn, and whether they were narrowed to fit.
 *
 * Narrowed, a box has no room for its unit beside its number, so the units stand over the columns
 * instead, which also says what each column is where no pointer can rest on it to be told.
 *
 * Immutable.
 */
private class Widths(
    val runtime: Dp,
    val arrow: Dp,
    val depth: Dp,
    val duration: Dp,
    val rate: Dp,
    val gas: Dp,
    val narrow: Boolean,
)

/**
 * The runtime's columns for a box [available] wide: their own widths where a line fits, and
 * otherwise what is left shared out among the four typed in, so that a line keeps its gas and its
 * two buttons on a phone rather than running off the screen. `PHONE-2`.
 */
private fun widthsFor(available: Dp): Widths {
    val around = BUTTON * 2 + HALF * COLUMN_GAPS
    if (available >= RUNTIME + ARROW + DEPTH + DURATION + RATE + GAS + around) {
        return Widths(RUNTIME, ARROW, DEPTH, DURATION, RATE, GAS, narrow = false)
    }
    val left = (available - around - NARROW_RUNTIME - NARROW_ARROW).coerceAtLeast(LEAST_LEFT)
    return Widths(
        NARROW_RUNTIME,
        NARROW_ARROW,
        left * DEPTH_SHARE,
        left * DURATION_SHARE,
        left * RATE_SHARE,
        left * GAS_SHARE,
        narrow = true,
    )
}

/** What each column of a narrowed runtime holds, over the lines, the units no longer in the boxes. */
@Composable
private fun ColumnHeads(widths: Widths) {
    Row(horizontalArrangement = Arrangement.spacedBy(HALF)) {
        val style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
        Cell("min", widths.runtime, TextAlign.End, style)
        Cell("", widths.arrow, TextAlign.Center, style)
        Cell("m", widths.depth, TextAlign.Center, style)
        Cell("time", widths.duration, TextAlign.Center, style)
        Cell("m/min", widths.rate, TextAlign.Center, style)
        Cell("gas", widths.gas, TextAlign.Center, style)
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
    widths: Widths,
) {
    val staying = leg?.direction == Direction.STAY
    Row(
        modifier = Modifier.height(ROW),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Explained(PlannerTips.RUNTIME) { Cell(leg?.let { runtimeSaid(it) }.orEmpty(), widths.runtime, TextAlign.End) }
        Explained(PlannerTips.DIRECTION) { Cell(leg?.direction?.arrow.orEmpty(), widths.arrow, TextAlign.Center) }
        Tipped(PlannerTips.DEPTH, widths.depth) {
            Compact(
                dense = true,
                value = segment.depth,
                onChange = { shaping.segments[index] = segment.copy(depth = it) },
                after = if (widths.narrow) "" else "m",
                number = true,
                wrong = ceilingBroken,
            )
        }
        Tipped(PlannerTips.DURATION, widths.duration) {
            Compact(
                dense = true,
                value = segment.duration,
                onChange = { shaping.segments[index] = segment.copy(duration = it, rate = "") },
                hint = if (segment.duration.isBlank() && leg != null) clockOf(leg.seconds) else "",
                derived = true,
                // A line staying at its depth has nothing else to time it by.
                wrong = needsDuration(shaping.segments, index),
            )
        }
        Tipped(PlannerTips.RATE, widths.rate) {
            Compact(
                dense = true,
                value = if (staying) "" else segment.rate,
                onChange = { shaping.segments[index] = segment.copy(rate = it, duration = "") },
                after = if (widths.narrow) "" else "m/min",
                number = true,
                hint = leg?.rate?.takeIf { segment.rate.isBlank() }?.let { rateSaid(it) }.orEmpty(),
                derived = true,
                enabled = !staying,
            )
        }
        val planned = shaping.described()
        val above = gasAbove(planned, index)
        val shown = segment.gas?.takeIf { it in shaping.gases.indices } ?: above
        Tipped(PlannerTips.GAS, widths.gas) {
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
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "Add a line below",
                    modifier = Modifier.size(DENSE_GLYPH),
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
        if (shaping.segments.size > 1) {
            Explained(PlannerTips.REMOVE_LINE) {
                IconButton(onClick = { shaping.removeSegment(index) }, modifier = Modifier.size(BUTTON)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Take out this line",
                        modifier = Modifier.size(DENSE_GLYPH),
                        tint = MaterialTheme.colorScheme.outline
                    )
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
private fun WorkedLine(leg: Leg, shaping: Shaping, gasWrong: Boolean, ceilingBroken: Boolean, widths: Widths) {
    Explained(PlannerTips.WORKED) {
        Row(
            modifier = Modifier.height(ROW),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HALF),
        ) {
            val italic = calculatedOf(MaterialTheme.typography.bodySmall)
            Cell(runtimeSaid(leg), widths.runtime, TextAlign.End, italic)
            Cell(leg.direction.arrow, widths.arrow, TextAlign.Center, italic)
            val error = italic.copy(color = MaterialTheme.colorScheme.error)
            val metres = if (widths.narrow) plain(leg.to) else "${plain(leg.to)} m"
            Cell(metres, widths.depth, TextAlign.End, if (ceilingBroken) error else italic)
            Cell(clockOf(leg.seconds), widths.duration, TextAlign.End, italic)
            val rate = leg.rate?.let { if (widths.narrow) "(${rateSaid(it)})" else "(${rateSaid(it)} m/min)" }
            Cell(rate.orEmpty(), widths.rate, TextAlign.End, italic)
            Cell(
                gasChoiceOf(shaping.described(), leg.gas),
                widths.gas,
                TextAlign.Start,
                if (gasWrong) error else italic,
                padding = GAP
            )
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
    Halves(
        first = {
            Section("General") {
                Setting("Descent rate", PlannerTips.DESCENT_RATE, shaping.descentRate, "m/min") {
                    shaping.descentRate = it
                }
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
                Setting("Atmospheric pressure", PlannerTips.ATMOSPHERIC_PRESSURE, shaping.atmosphericPressure, "bar") {
                    shaping.atmosphericPressure = it
                }
            }
            Section("Gas") {
                Setting(
                    "pO₂ max bottom",
                    PlannerTips.BOTTOM_OXYGEN,
                    shaping.bottomOxygen,
                    "bar"
                ) { shaping.bottomOxygen = it }
                Setting("pO₂ max deco", PlannerTips.DECO_OXYGEN, shaping.decoOxygen, "bar") { shaping.decoOxygen = it }
                Setting("pO₂ min", PlannerTips.LEAST_OXYGEN, shaping.leastOxygen, "bar") { shaping.leastOxygen = it }
            }
        },
        second = {
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
                Setting(
                    "Safety stop depth",
                    PlannerTips.SAFETY_DEPTH,
                    shaping.safetyDepth,
                    "m",
                    enabled = !none
                ) { shaping.safetyDepth = it }
                Setting(
                    "Safety stop duration",
                    PlannerTips.SAFETY_DURATION,
                    shaping.safetyMinutes,
                    "min"
                ) { shaping.safetyMinutes = it }
                Labelled("Gas switches between stops", PlannerTips.SWITCH_STOPS) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        Checkbox(
                            checked = shaping.switchStops,
                            onCheckedChange = { shaping.switchStops = it },
                            modifier = Modifier.size(DENSE_GLYPH).scale(DENSE_CHECK),
                        )
                    }
                }
            }
        },
    )
}

/**
 * The gas reserve's box: its settings on the left, and beside them a line for each scenario with
 * what it came to. `LOGIC-40`.
 *
 * The settings and what they decide sit together, so a reader changing one sees the other move.
 */
@Composable
private fun Contingency(shaping: Shaping, reckoned: Reckoned?) {
    val settings = @Composable {
        Column {
            Setting(
                "Stress factor",
                PlannerTips.STRESS_FACTOR,
                shaping.stressFactor,
                "× SAC"
            ) { shaping.stressFactor = it }
            Setting(
                "Problem solving time",
                PlannerTips.PROBLEM_SOLVING,
                shaping.problemMinutes,
                "min"
            ) { shaping.problemMinutes = it }
        }
    }
    // On a phone the scenarios go under the settings: beside them there was no room left for
    // what each came to. `PHONE-2`.
    if (LocalCompact.current) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HALF)) {
            settings()
            Scenarios(reckoned, shaping, Modifier.fillMaxWidth())
        }
        return
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        settings()
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
private fun Setting(
    label: String,
    tip: String,
    value: String,
    after: String,
    enabled: Boolean = true,
    onChange: (String) -> Unit
) {
    Labelled(label, tip) {
        Box(modifier = Modifier.width(SETTING)) {
            Compact(value = value, onChange = onChange, after = after, enabled = enabled, dense = true, number = true)
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
                Compact(
                    dense = true,
                    value = breathed.gas,
                    onChange = { shaping.gases[index] = breathed.copy(gas = prettyGasOf(it)) })
            }
            Tipped(PlannerTips.ROLE, ROLE) {
                Pick(
                    dense = true,
                    chosen = breathed.role.label,
                    options = Role.entries.map { it.label },
                ) { shaping.gases[index] = breathed.copy(role = Role.entries[it]) }
            }
            Tipped(PlannerTips.VOLUME, VOLUME) {
                Compact(
                    dense = true,
                    value = breathed.size,
                    onChange = { shaping.gases[index] = breathed.copy(size = it) },
                    after = "L",
                    number = true,
                )
            }
            Tipped(PlannerTips.START, PRESSURE) {
                Compact(
                    dense = true,
                    value = breathed.fill,
                    onChange = { shaping.gases[index] = breathed.copy(fill = it) },
                    after = "bar",
                    number = true,
                )
            }
            Tipped(PlannerTips.SAC, SAC) {
                Compact(
                    dense = true,
                    value = breathed.sac,
                    onChange = { shaping.gases[index] = breathed.copy(sac = it) },
                    after = "L/min",
                    number = true,
                )
            }
            val worked = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.outline)
            Explained(PlannerTips.MOD) { Cell(deepestSaid(breathed, conditions), FIGURED, TextAlign.End, worked) }
            Explained(PlannerTips.USED) {
                Cell(
                    done?.evaluated?.gasUsed?.get(key)?.let { "${it.roundToInt()} L" }.orEmpty(),
                    FIGURED,
                    TextAlign.End,
                    worked
                )
            }
            Explained(PlannerTips.END) {
                Cell(done?.evaluated?.pressures?.get(key)?.let { ending(it) }.orEmpty(), FIGURED, TextAlign.End, worked)
            }
            // Red where the cylinder ends with less than this, as a line too deep for its gas is.
            val short = reckoned != null && isShort(reckoned, key)
            Explained(PlannerTips.RESERVE) {
                Cell(
                    reckoned?.let { keptSaid(it, key) }.orEmpty(),
                    FIGURED,
                    TextAlign.End,
                    if (short) worked.copy(color = MaterialTheme.colorScheme.error) else worked,
                )
            }
            Explained(PlannerTips.ADD_GAS) {
                IconButton(onClick = { shaping.addGas(index) }, modifier = Modifier.size(BUTTON)) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Add a gas below",
                        modifier = Modifier.size(DENSE_GLYPH),
                        tint = MaterialTheme.colorScheme.outline
                    )
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
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Take out ${gasLabelOf(index)}",
                        modifier = Modifier.size(DENSE_GLYPH)
                    )
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
                Cell(
                    heading,
                    width,
                    TextAlign.Start,
                    MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.outline)
                )
            }
        }
    }
}

/** What the whole dive costs, on one line where it fits and wrapping where it does not. */
@Composable
private fun Figures(evaluated: Evaluated.Done) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = GAP),
        horizontalArrangement = Arrangement.spacedBy(GAP * 3),
        verticalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Figure("CNS", "${evaluated.oxygen.percentCns.toInt()}%", PlannerTips.CNS)
        Figure("OTU", "${evaluated.oxygen.otu.toInt()}", PlannerTips.OTU)
        Figure("No-fly time", evaluated.noFlight?.let { waitSaid(it) } ?: "more than a day", PlannerTips.NO_FLY)
        Figure(
            "Desaturation time",
            evaluated.desaturation?.let { waitSaid(it) } ?: "more than a day",
            PlannerTips.DESATURATION)
    }
}

/** The tip shown beside a scenario's name: the window's own copy, kept out of `logic`. */
internal fun tipOf(scenario: Scenario): String = when (scenario) {
    Scenario.LOST_GAS -> PlannerTips.LOST_GAS
    Scenario.SHARED -> PlannerTips.SHARED
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
                    Explained(tipOf(scenario)) {
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
                                        options = listOf(NO_GAS_LOST) + planned.gases.indices.map {
                                            gasChoiceOf(
                                                planned,
                                                it
                                            )
                                        },
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

// A kept, local copy: the same 60.0 the model's own file uses, for the one place left here that
// turns a second into a minute for the graph's own axis.
private const val SECONDS_IN_MINUTE = 60.0

/** How tall the top of the plan is: eighteen lines of the runtime, and three cylinders under the settings. */
private val ZONE = 520.dp

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

/** How many gaps a typed line has between its eight parts. */
private const val COLUMN_GAPS = 7

/** How wide the runtime's first two columns are where a line has to be shrunk to fit. */
private val NARROW_RUNTIME = 28.dp
private val NARROW_ARROW = 12.dp

/** The least the four typed columns are given together, below which a number could not be read. */
private val LEAST_LEFT = 180.dp

/** How a narrowed runtime shares what is left among the four typed columns, the gas named in full. */
private const val DEPTH_SHARE = 0.21f
private const val DURATION_SHARE = 0.23f
private const val RATE_SHARE = 0.21f
private const val GAS_SHARE = 0.35f

private val SETTING_LABEL = 170.dp
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
    Triple("Reserve", FIGURED, PlannerTips.RESERVE),
)
