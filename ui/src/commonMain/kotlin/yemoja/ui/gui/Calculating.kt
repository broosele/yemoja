package yemoja.ui.gui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yemoja.data.Date
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.NOMINAL_DENSITY
import yemoja.logic.SEA_LEVEL
import yemoja.logic.Settings
import yemoja.logic.TideCalculator
import yemoja.logic.Universe
import yemoja.logic.ambientAt
import yemoja.logic.depthAt
import yemoja.logic.equivalentAirDepth
import yemoja.logic.equivalentNarcoticDepth
import yemoja.logic.LEAST_OXYGEN
import yemoja.logic.maximumOperatingDepth
import yemoja.logic.minimumOperatingDepth
import yemoja.logic.noDecompressionLimit
import yemoja.logic.shownOf
import yemoja.logic.today
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

/*
 * The Calculations tab: a list of what can be worked out on the left, and the form for the one
 * chosen on the right.
 *
 * See ../../../../../../gui/doc.md — `GUI-43`.
 */

/**
 * Calculation is one thing the tab can work out, as the list on the left names it.
 *
 * In the order offered.
 */
internal enum class Calculation(val label: String) {
    /** A whole dive, level by level, and the way up it owes. `GUI-43`. */
    PLAN("Dive plan"),

    /** Breathing rate against gas used, any one of six from the other five. */
    SAC("SAC"),

    /** How long a depth may be stayed at before a stop is owed. */
    NDL("NDL"),

    /** The deepest a mix may be breathed at an oxygen limit. `LOGIC-39`. */
    MOD("MOD"),

    /** The depth of air holding as much nitrogen as a mix does at a depth. `LOGIC-41`. */
    EAD("EAD"),

    /** The depth of air as narcotic as a mix is at a depth. `LOGIC-41`. */
    END("END"),

    /** High and low water at a dive site on a day. `GUI-55`. */
    TIDES("Tides"),
}

/**
 * Figure is one of the six boxes of the SAC form, in the order they sit.
 *
 * Each is what a diver reads it as and the unit they write it in. The units are the model's own —
 * litres a minute at the surface, metres, minutes, litres, bar — until the units a user wants
 * shown are read. `UI-2`.
 */
internal enum class Figure(val label: String, val unit: String) {
    SAC("SAC", "L/min"),
    DEPTH("Average depth", "m"),
    DURATION("Duration", "min"),
    SIZE("Cylinder size", "L"),
    START("Start pressure", "bar"),
    END("End pressure", "bar"),
}

/**
 * Working is what the tab holds while it is open: what is typed in each box and which box is
 * worked out, kept between visits like anything a tab holds. `GUI-27`.
 *
 * Not immutable.
 */
internal class Working {
    var calculation: Calculation by mutableStateOf(Calculation.PLAN)

    /** The SAC form's six boxes, by figure. */
    val figures = mutableStateMapOf<Figure, String>()

    /** Which of the six the form works out from the other five. */
    var unknown: Figure by mutableStateOf(Figure.SAC)

    var depth: String by mutableStateOf("")
    var gas: String by mutableStateOf("air")
    var gradientHigh: String by mutableStateOf("")

    /** Whether the NDL form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false

    /** The mix the MOD, EAD and END forms are asked about, one for the three. */
    var mix: String by mutableStateOf("EAN32")

    /** The depth the EAD and END forms are asked about. */
    var mixDepth: String by mutableStateOf("")

    /** The oxygen limit the MOD form is asked about, in bar. */
    var mostOxygen: String by mutableStateOf("")

    /** The least oxygen the MOD form is asked about, in bar, for a mix's minimum depth. */
    var leastOxygen: String by mutableStateOf(plain(LEAST_OXYGEN))

    /** Whether the END form counts oxygen as narcotic. */
    var oxygenNarcotic: Boolean by mutableStateOf(true)

    /** Whether the MOD form has been opened before, which decides whether it takes the settings. */
    var mixPrefilled: Boolean = false

    /** The plan form's levels and settings. `GUI-43`. */
    val shaping: Shaping = Shaping()

    /** Where the plan will be saved, and what the last save said. `GUI-44`. */
    val saving: Saving = Saving()

    /** The Tides form's choices and what was answered. `GUI-55`. */
    val tiding: Tiding = Tiding()
}

/** Answer is what a calculation came to: a number, or why there is none. */
internal sealed class Answer {
    data class Value(val value: Double) : Answer()

    /** Nothing to say yet, a box being empty: the form waits rather than complains. */
    object Waiting : Answer()

    data class Wrong(val reason: String) : Answer()

    /** A result that is words rather than a number, shown where a number would be: *No limit*. */
    data class Said(val text: String) : Answer()
}

/**
 * The [unknown] figure worked out from the other five in [typed].
 *
 * The gas used is the cylinder's size times the pressure it dropped, and the breathing rate is
 * that spread over the minutes at the pressure of the average depth, which [ambient] gives in bar
 * for a depth in metres. Each of the six is that one equation turned round. A depth is refused
 * where the pressure it would take is not reachable from the surface; a rate is refused where the
 * five say no gas was used. `GUI-43`.
 */
internal fun sacSolved(
    typed: Map<Figure, String>,
    unknown: Figure,
    ambient: (Double) -> Double,
    depthOf: (Double) -> Double,
): Answer {
    val known = LinkedHashMap<Figure, Double>()
    for (figure in Figure.entries) {
        if (figure == unknown) continue
        val text = typed[figure].orEmpty().trim()
        if (text.isEmpty()) return Answer.Waiting
        val number = text.toDoubleOrNull()
            ?: return Answer.Wrong("${figure.label} should be a number, not \"$text\"")
        if (number < 0) return Answer.Wrong("${figure.label} should be 0 or more, not $text")
        known[figure] = number
    }
    fun at(figure: Figure): Double = known.getValue(figure)
    val value = when (unknown) {
        Figure.SAC -> {
            if (at(Figure.DURATION) == 0.0) return Answer.Wrong("Duration should be more than 0 min")
            at(Figure.SIZE) * (at(Figure.START) - at(Figure.END)) /
                (at(Figure.DURATION) * ambient(at(Figure.DEPTH)))
        }
        Figure.DEPTH -> {
            val used = at(Figure.SIZE) * (at(Figure.START) - at(Figure.END))
            if (at(Figure.SAC) == 0.0 || at(Figure.DURATION) == 0.0) {
                return Answer.Wrong("SAC and duration should both be more than 0")
            }
            val pressure = used / (at(Figure.SAC) * at(Figure.DURATION))
            val depth = depthOf(pressure)
            if (depth < 0) {
                // Breathing at the surface is the least a dive can use, so less than that puts the
                // average depth above the water.
                val least = at(Figure.SAC) * at(Figure.DURATION) * ambient(0.0)
                return Answer.Wrong(
                    "Gas used should be at least ${least.roundToLong()} L: ${plain(at(Figure.SAC))} L/min for " +
                        "${plain(at(Figure.DURATION))} min, even at the surface (now ${used.roundToLong()} L)",
                )
            }
            depth
        }
        Figure.DURATION -> {
            if (at(Figure.SAC) == 0.0) return Answer.Wrong("SAC should be more than 0 L/min")
            at(Figure.SIZE) * (at(Figure.START) - at(Figure.END)) /
                (at(Figure.SAC) * ambient(at(Figure.DEPTH)))
        }
        Figure.SIZE -> {
            val drop = at(Figure.START) - at(Figure.END)
            if (drop <= 0.0) return Answer.Wrong("End pressure should be lower than start pressure")
            at(Figure.SAC) * at(Figure.DURATION) * ambient(at(Figure.DEPTH)) / drop
        }
        Figure.START -> {
            if (at(Figure.SIZE) == 0.0) return Answer.Wrong("a cylinder of nought litres holds no gas")
            at(Figure.END) + at(Figure.SAC) * at(Figure.DURATION) * ambient(at(Figure.DEPTH)) / at(Figure.SIZE)
        }
        Figure.END -> {
            if (at(Figure.SIZE) == 0.0) return Answer.Wrong("a cylinder of nought litres holds no gas")
            at(Figure.START) - at(Figure.SAC) * at(Figure.DURATION) * ambient(at(Figure.DEPTH)) / at(Figure.SIZE)
        }
    }
    if (value.isNaN() || value.isInfinite()) return Answer.Wrong("These five values give no result: check for zeros")
    if (unknown != Figure.END && value < 0) return Answer.Wrong("These five values give a negative result: check them")
    return Answer.Value(value)
}

/** What the NDL form reads from its boxes, or why it cannot. */
internal fun ndlAsked(depth: String, gas: String, gradientHigh: String, descentRate: Double): Answer {
    if (depth.isBlank() || gas.isBlank() || gradientHigh.isBlank()) return Answer.Waiting
    val metres = depth.trim().toDoubleOrNull()
        ?: return Answer.Wrong("Depth should be a number, not \"${depth.trim()}\"")
    if (metres <= 0) return Answer.Wrong("Depth should be more than 0 m, not ${depth.trim()}")
    val breathed = try {
        Gas.parse(gas)
    } catch (refused: ValueFormatException) {
        return Answer.Wrong(refused.message ?: gasWrong(gas))
    } catch (refused: IllegalArgumentException) {
        return Answer.Wrong(refused.message ?: gasWrong(gas))
    }
    val high = gradientHigh.trim().removeSuffix("%").trim().toDoubleOrNull()
        ?.takeIf { it >= 1 && it <= PERCENT }
        ?: return Answer.Wrong("GF high should be 1 to 100 %, not \"${gradientHigh.trim()}\"")
    // The high factor alone: a limit is the moment a stop becomes owed, which is the high
    // factor's question, and the low one only says how deep a first stop is taken. `LOGIC-37`.
    val seconds = noDecompressionLimit(metres, breathed, high / PERCENT, descentRate)
        ?: return Answer.Said("No limit")
    return Answer.Value(seconds / SECONDS_IN_MINUTE)
}

/**
 * The deepest [gas] may be breathed before its oxygen passes [mostOxygen] bar, in metres.
 *
 * Rounded down to a tenth, since a depth rounded up would be one the mix is too rich for. Salt
 * water at sea level, as the other forms assume.
 */
internal fun modAsked(gas: String, mostOxygen: String): Answer {
    if (gas.isBlank() || mostOxygen.isBlank()) return Answer.Waiting
    val mix = when (val read = mixRead(gas)) {
        is Mixed.Read -> read.gas
        is Mixed.Wrong -> return Answer.Wrong(read.reason)
    }
    val most = mostOxygen.trim().toDoubleOrNull()?.takeIf { it > 0 }
        ?: return Answer.Wrong("pO₂ max should be more than 0 bar, not \"${mostOxygen.trim()}\"")
    if (mix.fractionO2 * SEA_LEVEL > most) {
        val surface = ceil(mix.fractionO2 * SEA_LEVEL * HUNDREDTHS) / HUNDREDTHS
        return Answer.Wrong("pO₂ max should be at least ${plain(surface)} bar for $mix, its pO₂ at the surface")
    }
    val deepest = maximumOperatingDepth(mix, most, NOMINAL_DENSITY, SEA_LEVEL)
        ?: return Answer.Wrong("Gas should contain oxygen")
    return Answer.Value(floor(deepest * TENTHS) / TENTHS)
}

/**
 * The shallowest [gas] may be breathed before its oxygen falls below [leastOxygen] bar, in metres.
 *
 * Rounded up to a tenth, since a depth rounded down would be one the mix is too lean for. Nought for
 * a mix breathable at the surface. Salt water at sea level, as the other forms assume.
 */
internal fun minimumAsked(gas: String, leastOxygen: String): Answer {
    if (gas.isBlank() || leastOxygen.isBlank()) return Answer.Waiting
    val mix = when (val read = mixRead(gas)) {
        is Mixed.Read -> read.gas
        is Mixed.Wrong -> return Answer.Wrong(read.reason)
    }
    val least = leastOxygen.trim().toDoubleOrNull()?.takeIf { it > 0 }
        ?: return Answer.Wrong("pO₂ min should be more than 0 bar, not \"${leastOxygen.trim()}\"")
    val shallowest = minimumOperatingDepth(mix, least, NOMINAL_DENSITY, SEA_LEVEL)
        ?: return Answer.Wrong("Gas should contain oxygen")
    return Answer.Value(ceil(shallowest * TENTHS) / TENTHS)
}

/**
 * Why a mix whose minimum depth is [shallowest] and whose MOD is [deepest] can be breathed nowhere,
 * or null where there is a depth between the two.
 */
internal fun rangeWrong(gas: String, shallowest: Answer, deepest: Answer): String? {
    if (shallowest !is Answer.Value || deepest !is Answer.Value) return null
    if (shallowest.value <= deepest.value) return null
    return "${gas.trim()} has no usable depth: its minimum depth, ${plain(shallowest.value)} m, should be " +
        "shallower than its MOD, ${plain(deepest.value)} m"
}

/**
 * The depth at which air holds as much nitrogen as [gas] does at [depth], in metres.
 *
 * Rounded up to a tenth, the cautious way for a depth read against air tables. `LOGIC-41`.
 */
internal fun eadAsked(depth: String, gas: String): Answer =
    equivalentAsked(depth, gas) { metres, mix -> equivalentAirDepth(metres, mix) }

/**
 * The depth at which air is as narcotic as [gas] is at [depth], in metres, counting oxygen as
 * narcotic where [oxygenNarcotic] says so.
 *
 * Rounded up to a tenth, the cautious way. `LOGIC-41`.
 */
internal fun endAsked(depth: String, gas: String, oxygenNarcotic: Boolean): Answer =
    equivalentAsked(depth, gas) { metres, mix -> equivalentNarcoticDepth(metres, mix, oxygenNarcotic) }

private fun equivalentAsked(depth: String, gas: String, equivalent: (Double, Gas) -> Double): Answer {
    if (depth.isBlank() || gas.isBlank()) return Answer.Waiting
    val metres = depth.trim().toDoubleOrNull()
        ?: return Answer.Wrong("Depth should be a number, not \"${depth.trim()}\"")
    if (metres < 0) return Answer.Wrong("Depth should be 0 m or more, not ${depth.trim()}")
    val mix = when (val read = mixRead(gas)) {
        is Mixed.Read -> read.gas
        is Mixed.Wrong -> return Answer.Wrong(read.reason)
    }
    return Answer.Value(ceil(equivalent(metres, mix) * TENTHS) / TENTHS)
}

/** Mixed is a gas read from a box, or why the box names none. */
private sealed class Mixed {
    class Read(val gas: Gas) : Mixed()
    class Wrong(val reason: String) : Mixed()
}

private fun mixRead(typed: String): Mixed = try {
    Mixed.Read(Gas.parse(typed))
} catch (refused: ValueFormatException) {
    Mixed.Wrong(refused.message ?: gasWrong(typed))
} catch (refused: IllegalArgumentException) {
    Mixed.Wrong(refused.message ?: gasWrong(typed))
}

/** What to say of a box that names no gas. */
internal fun gasWrong(typed: String): String =
    "Gas should be a mix such as AIR, EAN32 or TMX18/45, not \"${typed.trim()}\""

/** An answer as the form shows it under the boxes. */
internal fun answerSaid(answer: Answer, unit: String): String? = when (answer) {
    is Answer.Value -> "${plain((answer.value * 10).roundToLong() / 10.0)} $unit".trim()
    Answer.Waiting -> null
    is Answer.Wrong -> answer.reason
    is Answer.Said -> answer.text
}

/**
 * The tab: what can be worked out on the left, and the form for the one chosen on the right.
 *
 * [settings] prefill the NDL form's gradient factors the first time it opens, and are absent where
 * no logbook is open, since a calculation needs none. `GUI-43`.
 */
@Composable
internal fun Calculations(
    working: Working,
    settings: Settings?,
    scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)? = null,
    /** The logbook a plan is saved into and a tide's dive site is chosen from, or absent where none is open. */
    universe: Universe? = null,
    /** What the Tides form can ask, or empty where the platform reaches none. */
    tides: List<TideCalculator> = emptyList(),
    today: () -> Date = ::today,
) {
    // A phone scrolls the warning away with the form, which it still heads; a third of a small
    // screen held by it for good would leave the form no room. `PHONE-2`.
    if (LocalCompact.current) {
        Calculators(working, settings, scrollbar, universe, tides, today)
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Waiver()
        HorizontalDivider()
        Calculators(working, settings, scrollbar, universe, tides, today)
    }
}

/**
 * What these figures are not, above every calculation the tab offers.
 *
 * **Said here rather than in the manual alone.** A calculation is the one place in the window
 * where a reader asks for a number to act on, with no dive and no computer behind it, and a
 * warning they must go looking for is one they will not find. The words are the manual's own, so
 * a reader meets one wording here, in `manual/decompression.md` and in the licence. `GUI-43`.
 */
@Composable
private fun Waiver() {
    Row(
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = GAP, vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HALF),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(ICON),
        )
        Text(
            text = WAIVER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * What the tab says of itself, in the words the manual and the licence use.
 *
 * One wording in three places rather than three of its own: a reader who has read the manual meets
 * the sentence they already know, and nothing here can drift into a softer claim than the licence
 * makes. `manual/decompression.md`, `manual/app-info.md`.
 */
internal const val WAIVER: String =
    "These figures come from a model, not a dive computer, and they may be wrong. The model has " +
        "been neither certified nor validated as a dive computer or as planning software. You use " +
        "its figures entirely at your own risk, and nobody involved in making Yemoja accepts " +
        "responsibility for a dive planned, made or judged with their help. Check them against " +
        "your training and your tables, and never let them override your dive computer or your " +
        "own judgement."

/** The list of what can be worked out, and the form for the one chosen. */
@Composable
private fun Calculators(
    working: Working,
    settings: Settings?,
    scrollbar: (@Composable (state: ScrollState, modifier: Modifier) -> Unit)?,
    universe: Universe?,
    tides: List<TideCalculator>,
    today: () -> Date,
) {
    // A phone chooses the calculation from a list above it rather than beside it. `PHONE-2`.
    val compact = LocalCompact.current
    Row(modifier = Modifier.fillMaxSize()) {
        if (!compact) Selectable {
            Column(modifier = Modifier.width(CALCULATIONS).fillMaxHeight().padding(GAP)) {
                for (calculation in Calculation.entries) {
                    Line(
                        text = calculation.label,
                        depth = 0,
                        chosen = working.calculation == calculation,
                    ) { working.calculation = calculation }
                }
            }
        }
        if (!compact) VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            Selectable {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if (compact) {
                        Waiver()
                        val all = Calculation.entries
                        Picked(all.map { it.label }, all.indexOf(working.calculation)) {
                            working.calculation = all[it]
                        }
                    }
                    when (working.calculation) {
                        Calculation.SAC -> SacForm(working)
                        Calculation.NDL -> NdlForm(working, settings)
                        Calculation.MOD -> ModForm(working, settings)
                        Calculation.EAD -> EadForm(working)
                        Calculation.END -> EndForm(working)
                        Calculation.PLAN -> PlanForm(working.shaping, settings, scrollbar, universe) {
                            SaveRow(working.saving, working.shaping, universe)
                        }
                        Calculation.TIDES -> TidesForm(working.tiding, universe, tides, today())
                    }
                }
            }
        }
    }
}

/** The six figures, one worked out from the other five. */
@Composable
private fun SacForm(working: Working) {
    Heading("SAC")
    Aside("Select the quantity to calculate")
    val answer = sacSolved(working.figures, working.unknown, ::ambientOf, ::depthOf)
    for (figure in Figure.entries) {
        val solved = figure == working.unknown
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HALF),
        ) {
            RadioButton(selected = solved, onClick = { working.unknown = figure })
            // The name chooses too, and the box does not: a click into a box is to type there.
            Text(
                text = figure.label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LABEL).clickable { working.unknown = figure },
            )
            Box(modifier = Modifier.width(FIGURE)) {
                if (solved) {
                    Text(
                        text = (answer as? Answer.Value)?.let { answerSaid(it, figure.unit) } ?: "",
                        style = calculatedOf(MaterialTheme.typography.bodyMedium),
                        modifier = Modifier.padding(horizontal = GAP, vertical = HALF),
                    )
                } else {
                    Compact(
                        value = working.figures[figure].orEmpty(),
                        onChange = { working.figures[figure] = it },
                        after = figure.unit,
                    )
                }
            }
        }
    }
    (answer as? Answer.Wrong)?.let { Refused(it.reason) }
}

/** A depth and what is breathed, and how long a fresh diver may stay. */
@Composable
private fun NdlForm(working: Working, settings: Settings?) {
    // The factor the user chose, the first time the form opens; typed over freely after that.
    remember(working, settings) {
        if (!working.prefilled && settings != null) {
            working.gradientHigh = shownOf(Settings.DEFAULT_GRADIENT_FACTOR_HIGH, settings.number(Settings.DEFAULT_GRADIENT_FACTOR_HIGH))
            working.prefilled = true
        }
        working
    }
    val descent = settings?.number(Settings.DEFAULT_DESCENT_RATE) ?: FALLBACK_DESCENT_RATE
    Heading("NDL")
    Aside("Time at a depth, from leaving the surface, before a stop is needed.")
    // The high factor alone. A limit is the moment a stop becomes owed, which is the high
    // factor's question; the low one says how deep a first stop is taken and nothing about
    // whether there is one, so a box for it would ask for a number that changes no answer.
    // `LOGIC-39`.
    Field("GF high", working.gradientHigh, "%") { working.gradientHigh = it }
    Field("Gas", working.gas, "") { working.gas = it }
    Field("Depth", working.depth, "m") { working.depth = it }
    val answer = ndlAsked(working.depth, working.gas, working.gradientHigh, descent)
    when (answer) {
        is Answer.Value, is Answer.Said -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = "Time",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LABEL),
            )
            Text(
                text = answerSaid(answer, "min").orEmpty(),
                style = calculatedOf(MaterialTheme.typography.bodyMedium),
            )
        }
        is Answer.Wrong -> Refused(answer.reason)
        Answer.Waiting -> Unit
    }
    Aside("Descent ${plain(descent)} m/min, salt water, sea level.")
}

/** A mix and an oxygen limit, and how deep the mix may be breathed. */
@Composable
private fun ModForm(working: Working, settings: Settings?) {
    // The limits the user chose, for a bottom gas and for any gas, the first time the form opens.
    remember(working, settings) {
        if (!working.mixPrefilled) {
            val chosen = settings?.number(Settings.DEFAULT_PO2_MAX_BOTTOM) ?: Settings.DEFAULT_PO2_MAX_BOTTOM.default
            working.mostOxygen = shownOf(Settings.DEFAULT_PO2_MAX_BOTTOM, chosen)
            val least = settings?.number(Settings.DEFAULT_PO2_MIN) ?: Settings.DEFAULT_PO2_MIN.default
            working.leastOxygen = shownOf(Settings.DEFAULT_PO2_MIN, least)
            working.mixPrefilled = true
        }
        working
    }
    Heading("MOD")
    Aside(
        "MOD: the deepest depth at pO₂ max. Minimum depth: the shallowest at pO₂ min, for hypoxic " +
            "mixes only.",
    )
    Field("Gas", working.mix, "") { working.mix = it }
    Field("pO₂ max", working.mostOxygen, "bar") { working.mostOxygen = it }
    Field("pO₂ min", working.leastOxygen, "bar") { working.leastOxygen = it }
    val deepest = modAsked(working.mix, working.mostOxygen)
    val shallowest = minimumAsked(working.mix, working.leastOxygen)
    Answered("MOD", deepest, "m")
    Answered("Minimum depth", shallowest, "m")
    rangeWrong(working.mix, shallowest, deepest)?.let { Refused(it) }
    Aside(
        "Salt water, sea level. MOD rounded down, minimum depth up, to 0.1 m.",
    )
}

/** A mix and a depth, and the depth of air holding as much nitrogen. */
@Composable
private fun EadForm(working: Working) {
    Heading("EAD")
    Aside("The depth at which air has the same nitrogen as the mix. Use it to read air tables for nitrox.")
    Field("Gas", working.mix, "") { working.mix = it }
    Field("Depth", working.mixDepth, "m") { working.mixDepth = it }
    Answered("EAD", eadAsked(working.mixDepth, working.mix), "m")
    Aside("Salt water, sea level, rounded up to 0.1 m.")
}

/** A mix and a depth, and the depth of air as narcotic. */
@Composable
private fun EndForm(working: Working) {
    Heading("END")
    Aside("The depth at which air is as narcotic as the mix. Helium counts as not narcotic.")
    Field("Gas", working.mix, "") { working.mix = it }
    Field("Depth", working.mixDepth, "m") { working.mixDepth = it }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Box(modifier = Modifier.width(LABEL))
        Checkbox(checked = working.oxygenNarcotic, onCheckedChange = { working.oxygenNarcotic = it })
        Text(
            text = "Oxygen is narcotic",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable { working.oxygenNarcotic = !working.oxygenNarcotic },
        )
    }
    Answered("END", endAsked(working.mixDepth, working.mix, working.oxygenNarcotic), "m")
    Aside(
        "Agencies differ on oxygen. Counting it gives the deeper, more cautious END. Salt water, sea " +
            "level, rounded up to 0.1 m.",
    )
}

/** What a form came to, beside its name, or why it came to nothing. */
@Composable
private fun Answered(label: String, answer: Answer, unit: String) {
    when (answer) {
        is Answer.Value, is Answer.Said -> Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LABEL),
            )
            Text(
                text = answerSaid(answer, unit).orEmpty(),
                style = calculatedOf(MaterialTheme.typography.bodyMedium),
            )
        }
        is Answer.Wrong -> Refused(answer.reason)
        Answer.Waiting -> Unit
    }
}

/** One labelled box of a form in this tab. */
@Composable
internal fun Field(label: String, value: String, after: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        Box(modifier = Modifier.width(FIGURE)) { Compact(value = value, onChange = onChange, after = after) }
    }
}

@Composable
internal fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = HALF),
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = HALF))
}

@Composable
internal fun Refused(reason: String) {
    Text(
        text = reason,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(horizontal = GAP, vertical = HALF),
    )
}

/** The pressure at [metres] of salt water at sea level, in bar, which a form with no dive assumes. */
private fun ambientOf(metres: Double): Double = ambientAt(metres, NOMINAL_DENSITY, SEA_LEVEL)

/** The depth in salt water at sea level that [bar] is the pressure of. */
private fun depthOf(bar: Double): Double = depthAt(bar, NOMINAL_DENSITY, SEA_LEVEL)

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0

/** Tenths of a metre in a metre, which is as fine as these forms give a depth. */
private const val TENTHS = 10.0

/** How wide a figure's box is, and a box of any other form in this tab. */
internal val FIGURE = 140.dp

/** How big the mark beside the waiver is, which is a body line's own height. */
private val ICON = 18.dp

/** Hundredths in one, for a pressure given to a hundredth of a bar. */
private const val HUNDREDTHS = 100.0

/** How wide the list of calculations is: its names are short, and the forms beside it are wide. */
private val CALCULATIONS = 150.dp
