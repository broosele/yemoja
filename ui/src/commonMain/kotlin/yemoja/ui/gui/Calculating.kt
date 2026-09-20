package yemoja.ui.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.NOMINAL_DENSITY
import yemoja.logic.SEA_LEVEL
import yemoja.logic.Settings
import yemoja.logic.ambientAt
import yemoja.logic.depthAt
import yemoja.logic.noDecompressionLimit
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
    /** Breathing rate against gas used, any one of six from the other five. */
    SAC("SAC"),

    /** How long a depth may be stayed at before a stop is owed. */
    NDL("NDL"),
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
    var calculation: Calculation by mutableStateOf(Calculation.SAC)

    /** The SAC form's six boxes, by figure. */
    val figures = mutableStateMapOf<Figure, String>()

    /** Which of the six the form works out from the other five. */
    var unknown: Figure by mutableStateOf(Figure.SAC)

    var depth: String by mutableStateOf("")
    var gas: String by mutableStateOf("air")
    var gradientLow: String by mutableStateOf("")
    var gradientHigh: String by mutableStateOf("")

    /** Whether the NDL form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false
}

/** Answer is what a calculation came to: a number, or why there is none. */
internal sealed class Answer {
    data class Value(val value: Double) : Answer()

    /** Nothing to say yet, a box being empty: the form waits rather than complains. */
    object Waiting : Answer()

    data class Wrong(val reason: String) : Answer()
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
            ?: return Answer.Wrong("${figure.label} should be a number, but was \"$text\"")
        if (number < 0) return Answer.Wrong("${figure.label} should not be below nought, but was $text")
        known[figure] = number
    }
    fun at(figure: Figure): Double = known.getValue(figure)
    val value = when (unknown) {
        Figure.SAC -> {
            if (at(Figure.DURATION) == 0.0) return Answer.Wrong("a duration of nought gives no rate")
            at(Figure.SIZE) * (at(Figure.START) - at(Figure.END)) /
                (at(Figure.DURATION) * ambient(at(Figure.DEPTH)))
        }
        Figure.DEPTH -> {
            val used = at(Figure.SIZE) * (at(Figure.START) - at(Figure.END))
            if (at(Figure.SAC) == 0.0 || at(Figure.DURATION) == 0.0) {
                return Answer.Wrong("a rate or a duration of nought breathes no gas at any depth")
            }
            val pressure = used / (at(Figure.SAC) * at(Figure.DURATION))
            val depth = depthOf(pressure)
            if (depth < 0) return Answer.Wrong("less gas than a minute at the surface would take")
            depth
        }
        Figure.DURATION -> {
            if (at(Figure.SAC) == 0.0) return Answer.Wrong("a rate of nought breathes for ever")
            at(Figure.SIZE) * (at(Figure.START) - at(Figure.END)) /
                (at(Figure.SAC) * ambient(at(Figure.DEPTH)))
        }
        Figure.SIZE -> {
            val drop = at(Figure.START) - at(Figure.END)
            if (drop == 0.0) return Answer.Wrong("a pressure that did not drop says nothing of the size")
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
    if (value.isNaN() || value.isInfinite()) return Answer.Wrong("the five do not give a number")
    if (unknown != Figure.END && value < 0) return Answer.Wrong("the five give a number below nought")
    return Answer.Value(value)
}

/** What the NDL form reads from its boxes, or why it cannot. */
internal fun ndlAsked(depth: String, gas: String, gradientHigh: String, descentRate: Double): Answer {
    if (depth.isBlank() || gas.isBlank() || gradientHigh.isBlank()) return Answer.Waiting
    val metres = depth.trim().toDoubleOrNull()
        ?: return Answer.Wrong("Depth should be a number, but was \"${depth.trim()}\"")
    if (metres <= 0) return Answer.Wrong("Depth should be more than nought, but was ${depth.trim()}")
    val breathed = try {
        Gas.parse(gas)
    } catch (refused: ValueFormatException) {
        return Answer.Wrong(refused.message ?: "\"$gas\" is not a gas")
    } catch (refused: IllegalArgumentException) {
        return Answer.Wrong(refused.message ?: "\"$gas\" is not a gas")
    }
    val high = gradientHigh.trim().removeSuffix("%").trim().toDoubleOrNull()
        ?.takeIf { it >= 1 && it <= PERCENT }
        ?: return Answer.Wrong("GF high is a percentage from 1 to 100, but was \"${gradientHigh.trim()}\"")
    // The high factor alone: a limit is the moment a stop becomes owed, which is the high
    // factor's question, and the low one only says how deep a first stop is taken. `LOGIC-37`.
    val seconds = noDecompressionLimit(metres, breathed, high / PERCENT, descentRate)
        ?: return Answer.Wrong("no limit within a day: this depth owes no stop however long it is stayed at")
    return Answer.Value(seconds / SECONDS_IN_MINUTE)
}

/** An answer as the form shows it under the boxes. */
internal fun answerSaid(answer: Answer, unit: String): String? = when (answer) {
    is Answer.Value -> "${plain((answer.value * 10).roundToLong() / 10.0)} $unit".trim()
    Answer.Waiting -> null
    is Answer.Wrong -> answer.reason
}

/**
 * The tab: what can be worked out on the left, and the form for the one chosen on the right.
 *
 * [settings] prefill the NDL form's gradient factors the first time it opens, and are absent where
 * no logbook is open, since a calculation needs none. `GUI-43`.
 */
@Composable
internal fun Calculations(working: Working, settings: Settings?) {
    Row(modifier = Modifier.fillMaxSize()) {
        Selectable {
            Column(modifier = Modifier.width(SELECTOR).fillMaxHeight().padding(GAP)) {
                for (calculation in Calculation.entries) {
                    Line(
                        text = calculation.label,
                        depth = 0,
                        chosen = working.calculation == calculation,
                    ) { working.calculation = calculation }
                }
            }
        }
        VerticalDivider()
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(GAP)) {
            Selectable {
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (working.calculation) {
                        Calculation.SAC -> SacForm(working)
                        Calculation.NDL -> NdlForm(working, settings)
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
                        style = MaterialTheme.typography.bodyMedium,
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
    (answer as? Answer.Wrong)?.let { Wrong(it.reason) }
}

/** A depth and what is breathed, and how long a fresh diver may stay. */
@Composable
private fun NdlForm(working: Working, settings: Settings?) {
    // The factors the user chose, the first time the form opens; typed over freely after that.
    remember(working, settings) {
        if (!working.prefilled && settings != null) {
            working.gradientLow = shownOf(Settings.DEFAULT_GF_LOW, settings.number(Settings.DEFAULT_GF_LOW))
            working.gradientHigh = shownOf(Settings.DEFAULT_GF_HIGH, settings.number(Settings.DEFAULT_GF_HIGH))
            working.prefilled = true
        }
        working
    }
    val descent = settings?.number(Settings.DEFAULT_DESCENT_RATE) ?: FALLBACK_DESCENT_RATE
    Heading("NDL")
    Aside("How long a depth may be stayed at, from leaving the surface, before a stop is owed.")
    Asked("GF low", working.gradientLow, "%") { working.gradientLow = it }
    Asked("GF high", working.gradientHigh, "%") { working.gradientHigh = it }
    Asked("Gas", working.gas, "") { working.gas = it }
    Asked("Depth", working.depth, "m") { working.depth = it }
    val answer = ndlAsked(working.depth, working.gas, working.gradientHigh, descent)
    when (answer) {
        is Answer.Value -> Row(
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
            Text(answerSaid(answer, "min").orEmpty(), style = MaterialTheme.typography.bodyMedium)
        }
        is Answer.Wrong -> Wrong(answer.reason)
        Answer.Waiting -> Unit
    }
    Aside("Descending at ${plain(descent)} m a minute, in salt water at sea level. The low factor does not move a limit: it applies only at a stop, and a dive within its limit owes none.")
}

/** One labelled box of the NDL form. */
@Composable
private fun Asked(label: String, value: String, after: String, onChange: (String) -> Unit) {
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
private fun Heading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(bottom = HALF),
    )
    HorizontalDivider(modifier = Modifier.padding(bottom = HALF))
}

@Composable
private fun Wrong(reason: String) {
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

/** How wide a figure's box is. */
private val FIGURE = 140.dp
