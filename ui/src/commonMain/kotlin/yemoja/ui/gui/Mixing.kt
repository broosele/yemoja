package yemoja.ui.gui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.Blend
import yemoja.logic.Contents
import yemoja.logic.MOST_PRESSURE
import yemoja.logic.blended
import yemoja.logic.contentsOf
import yemoja.logic.toppedUp
import kotlin.math.roundToLong

/*
 * The Gas mix form of the Calculations tab: what a cylinder holds after a top-up, and which gases
 * to add to make a mix.
 *
 * See ../../../../../../gui/doc.md — `GUI-56`. The arithmetic is the logic layer's, `LOGIC-45`.
 */

/**
 * Mixing is what the Gas mix form holds while the tab is open: what is typed in each box, and
 * which of its two questions is asked. `GUI-27`.
 *
 * Not immutable.
 */
internal class Mixing {
    /** Whether the form asks which gases to add, rather than what a top-up comes to. */
    var blending: Boolean by mutableStateOf(false)

    var startGas: String by mutableStateOf("EAN32")

    var startPressure: String by mutableStateOf("")

    /** In litres, and empty where the litres added are not wanted. */
    var size: String by mutableStateOf("")

    /** The gas a top-up adds. */
    var topUp: String by mutableStateOf("air")

    /** The mix a blend is to leave in the cylinder. */
    var wanted: String by mutableStateOf("EAN32")

    /** One box for both questions: what the cylinder reads once it is full. */
    var endPressure: String by mutableStateOf("200")

    /** The gases a blend may add, separated by commas, in the order they are filled. */
    var gases: String by mutableStateOf("O2, He, air")
}

/** MixRow is one line of what the form answers: what it is, and the figure beside it. */
internal data class MixRow(val label: String, val text: String)

/** MixAnswer is what the form came to: lines to show, or why there are none. */
internal sealed class MixAnswer {
    /** Nothing to say yet, a box being empty. */
    object Waiting : MixAnswer()

    data class Wrong(val reason: String) : MixAnswer()

    data class Rows(val rows: List<MixRow>) : MixAnswer()
}

/**
 * What a cylinder of [startGas] at [startPressure] holds once [with] has been added to [endPressure].
 *
 * The mix, and the litres added where [size] is given. `GUI-56`.
 */
internal fun toppedUpAsked(
    startGas: String,
    startPressure: String,
    size: String,
    with: String,
    endPressure: String,
): MixAnswer {
    if (startPressure.isBlank() || with.isBlank() || endPressure.isBlank()) return MixAnswer.Waiting
    return answered {
        val startBar = pressureRead("Start pressure", startPressure)
        if (startBar > 0 && startGas.isBlank()) return MixAnswer.Waiting
        val start = if (startBar > 0) contentsOf(gasRead(startGas), startBar) else Contents.EMPTY
        val endBar = pressureRead("End pressure", endPressure)
        if (endBar == 0.0) throw ValueFormatException("End pressure should be more than 0 bar")
        if (endBar < startBar) {
            throw ValueFormatException(
                "End pressure should be at least the start pressure, ${plain(startBar)} bar, not ${endPressure.trim()}",
            )
        }
        val litres = sizeRead(size)
        val added = gasRead(with)
        val held = toppedUp(start, added, endBar)
        val rows = arrayListOf(MixRow("Mix", mixSaid(held)))
        if (litres != null) {
            val more = (held.litresIn(litres) - start.litresIn(litres)).roundToLong()
            rows += MixRow("Added", "$more L of ${nameOf(added)}")
        }
        MixAnswer.Rows(rows)
    }
}

/**
 * How [wanted] at [endPressure] is made in a cylinder of [startGas] at [startPressure], from [gases].
 *
 * One line a gas, in the order [gases] lists them, each saying the pressure to fill to. A line
 * before them says how far to drain the cylinder where what it holds is in the way. `GUI-56`.
 */
internal fun blendAsked(
    startGas: String,
    startPressure: String,
    size: String,
    wanted: String,
    endPressure: String,
    gases: String,
): MixAnswer {
    if (startPressure.isBlank() || wanted.isBlank() || endPressure.isBlank() || gases.isBlank()) {
        return MixAnswer.Waiting
    }
    return answered {
        val startBar = pressureRead("Start pressure", startPressure)
        if (startBar > 0 && startGas.isBlank()) return MixAnswer.Waiting
        val start = if (startBar > 0) contentsOf(gasRead(startGas), startBar) else Contents.EMPTY
        val endBar = pressureRead("End pressure", endPressure)
        if (endBar == 0.0) throw ValueFormatException("End pressure should be more than 0 bar")
        val litres = sizeRead(size)
        val mix = gasRead(wanted)
        val from = gases.split(',', ';').filter { it.isNotBlank() }.map { gasRead(it) }
        when (val blend = blended(start, contentsOf(mix, endBar), from)) {
            Blend.Impossible -> MixAnswer.Wrong(
                "${listed(from.distinct().map { nameOf(it) })} cannot make ${nameOf(mix)}, in an empty cylinder either",
            )
            is Blend.Recipe -> MixAnswer.Rows(rowsOf(blend, litres))
        }
    }
}

private fun rowsOf(recipe: Blend.Recipe, litres: Double?): List<MixRow> {
    val rows = ArrayList<MixRow>()
    recipe.drained?.let { bar ->
        rows += MixRow("Drain", if (tenths(bar) == "0") "until empty" else "to ${tenths(bar)} bar")
    }
    for (added in recipe.added) {
        val volume = litres?.let { " (${added.contents.litresIn(it).roundToLong()} L)" }.orEmpty()
        rows += MixRow("Add ${nameOf(added.gas)}", "to ${tenths(added.bar)} bar$volume")
    }
    if (recipe.added.isEmpty()) {
        rows += MixRow("Add", if (recipe.drained == null) "nothing, the cylinder holds this already" else "nothing")
    }
    return rows
}

/** A mix as the form answers it: the name a diver writes, and the fractions to a tenth of a percent. */
internal fun mixSaid(held: Contents): String {
    val helium = if (held.fractionHe * PERCENT >= SHOWN) ", ${tenths(held.fractionHe * PERCENT)} % He" else ""
    return "${nameOf(held.gas)} (${tenths(held.fractionO2 * PERCENT)} % O₂$helium)"
}

/**
 * A gas as the form names it, which is as a logbook writes it but for helium.
 *
 * Pure helium is never breathed, so a logbook has no word for it and would write `TMX0/100`.
 */
private fun nameOf(gas: Gas): String = if (gas == HELIUM) "He" else gas.toString()

private fun gasRead(typed: String): Gas = when (typed.trim().uppercase()) {
    "HE", "HELIUM" -> HELIUM
    else -> Gas.parse(typed)
}

/** A pressure from a box, a comma being read as the decimal point a phone's number keys may give. */
private fun pressureRead(label: String, typed: String): Double {
    val bar = typed.trim().replace(',', '.').toDoubleOrNull()
        ?: throw ValueFormatException("$label should be a number, not \"${typed.trim()}\"")
    if (bar < 0 || bar > MOST_PRESSURE) {
        throw ValueFormatException("$label should be 0 to ${plain(MOST_PRESSURE)} bar, not ${typed.trim()}")
    }
    return bar
}

/** The cylinder's size in litres, or null where the box is empty. */
private fun sizeRead(typed: String): Double? {
    if (typed.isBlank()) return null
    return typed.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
        ?: throw ValueFormatException("Cylinder size should be more than 0 L, not \"${typed.trim()}\"")
}

/** What [working] came to, a box that could not be read being why there is no answer. */
private inline fun answered(working: () -> MixAnswer): MixAnswer = try {
    working()
} catch (refused: ValueFormatException) {
    MixAnswer.Wrong(refused.message ?: "This could not be read")
} catch (refused: IllegalArgumentException) {
    MixAnswer.Wrong(refused.message ?: "This could not be read")
}

private fun listed(names: List<String>): String =
    if (names.size < 2) names.joinToString() else "${names.dropLast(1).joinToString()} and ${names.last()}"

private fun tenths(value: Double): String = plain(value, 1)

/** The two questions, the boxes for the one chosen, and what it comes to. */
@Composable
internal fun MixForm(mixing: Mixing) {
    Heading("Gas mix")
    Aside("Select what to calculate")
    Question("Mix after a top-up", !mixing.blending) { mixing.blending = false }
    Question("Gases to add for a mix", mixing.blending) { mixing.blending = true }
    Typed("Start gas", mixing.startGas) { mixing.startGas = it }
    Typed("Start pressure", mixing.startPressure, "bar", number = true) { mixing.startPressure = it }
    Typed("Cylinder size", mixing.size, "L", hint = "optional", number = true) { mixing.size = it }
    if (mixing.blending) {
        Typed("Wanted gas", mixing.wanted) { mixing.wanted = it }
    } else {
        Typed("Top up with", mixing.topUp) { mixing.topUp = it }
    }
    Typed("End pressure", mixing.endPressure, "bar", number = true) { mixing.endPressure = it }
    if (mixing.blending) Typed("Gases to add", mixing.gases, width = GASES) { mixing.gases = it }
    val answer = if (mixing.blending) {
        blendAsked(mixing.startGas, mixing.startPressure, mixing.size, mixing.wanted, mixing.endPressure, mixing.gases)
    } else {
        toppedUpAsked(mixing.startGas, mixing.startPressure, mixing.size, mixing.topUp, mixing.endPressure)
    }
    when (answer) {
        is MixAnswer.Rows -> for (row in answer.rows) Said(row)
        is MixAnswer.Wrong -> Refused(answer.reason)
        MixAnswer.Waiting -> Unit
    }
    if (mixing.blending) Aside("The gases are added in the order listed, separated by commas.")
    Aside(
        "Real gases at 20 °C: let the cylinder cool before reading a pressure. Analyse the mix before " +
            "breathing it.",
    )
}

/** One of the form's two questions, chosen by its button or its name. */
@Composable
private fun Question(text: String, chosen: Boolean, onChoose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HALF)) {
        RadioButton(selected = chosen, onClick = onChoose)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.clickable(onClick = onChoose),
        )
    }
}

/** One labelled box of the form. */
@Composable
private fun Typed(
    label: String,
    value: String,
    after: String = "",
    hint: String = "",
    number: Boolean = false,
    width: Dp = FIGURE,
    onChange: (String) -> Unit,
) {
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
        Box(modifier = Modifier.width(width)) {
            Compact(value = value, onChange = onChange, after = after, hint = hint, number = number)
        }
    }
}

/** One line of the answer, its name where a box's would be. */
@Composable
private fun Said(row: MixRow) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(LABEL),
        )
        Text(text = row.text, style = calculatedOf(MaterialTheme.typography.bodyMedium))
    }
}

private val HELIUM = Gas(0, 100)

private const val PERCENT = 100.0

/** The least helium said, in percent: less rounds to none at a tenth. */
private const val SHOWN = 0.05

/** How wide the box listing the gases is, which holds four or five of them. */
private val GASES = FIGURE * 2
