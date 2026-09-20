package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import yemoja.data.Gas
import yemoja.data.ValueFormatException
import yemoja.logic.Ascended
import yemoja.logic.Evaluated
import yemoja.logic.Run
import yemoja.logic.Settings
import yemoja.logic.Source
import yemoja.logic.completeAscent
import yemoja.logic.evaluate
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/*
 * A dive planned in the Calculations tab, belonging to no dive: levels and what is breathed in,
 * the way up and what it costs out.
 *
 * The model is the logic layer's, `LOGIC-37`, reached through a `Run` rather than through an item,
 * so a plan typed here and a plan on a dive are answered by one walk. Nothing here is stored.
 *
 * See ../../../../../../gui/doc.md — `GUI-43`.
 */

/**
 * Level is one leg of a planned run as it is typed: how long, and at what depth.
 *
 * The minutes count from leaving the depth before, so a first level of twenty minutes at thirty
 * metres is twenty minutes from the surface, which is how a diver reads a bottom time. `GUI-41`.
 *
 * Immutable.
 */
internal data class Level(val minutes: String = "", val depth: String = "")

/**
 * Shaping is the plan form while the tab holds it: the levels, what is breathed, and how
 * conservative to be.
 *
 * Not immutable.
 */
internal class Shaping {
    val levels = mutableStateListOf(Level())
    var gas: String by mutableStateOf("air")
    var gradientLow: String by mutableStateOf("")
    var gradientHigh: String by mutableStateOf("")

    /** What the cylinder holds and how fast it is breathed, each empty until somebody says. */
    var sac: String by mutableStateOf("")
    var size: String by mutableStateOf("")
    var fill: String by mutableStateOf("")

    /** Whether the form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false
}

/** Shaped is a run built from what was typed, or why there is none yet. */
internal sealed class Shaped {

    /** A run to evaluate, holding the levels alone: the way up is the model's to add. */
    class Ready(val run: Run) : Shaped()

    /** A box is empty, so the form waits rather than complains. */
    object Waiting : Shaped()

    data class Wrong(val reason: String) : Shaped()
}

/**
 * The run [shaping] describes, down to the end of its last level.
 *
 * **Each level is travelled to and then held.** A level's minutes count from leaving the depth
 * before it, so going deeper takes [descentRate] and coming shallower takes [ascentRate], and what
 * is left of the minutes is spent there. A level with less time than the travel takes is refused
 * with the arithmetic, as a plan on a dive is. `GUI-41`.
 *
 * The run carries nothing: a plan in the calculations belongs to no dive and so follows none.
 */
internal fun shapedOf(shaping: Shaping, descentRate: Double, ascentRate: Double): Shaped {
    val levels = ArrayList<Pair<Double, Double>>()
    for ((index, level) in shaping.levels.withIndex()) {
        if (level.minutes.isBlank() && level.depth.isBlank()) {
            if (shaping.levels.size == 1) return Shaped.Waiting
            continue
        }
        val minutes = level.minutes.trim().toDoubleOrNull()
        if (minutes == null || minutes <= 0) {
            return Shaped.Wrong(
                "level ${index + 1} lasts minutes, more than nought, and " +
                    "${said(level.minutes)} is not",
            )
        }
        val depth = level.depth.trim().toDoubleOrNull()
        if (depth == null || depth < 0) {
            return Shaped.Wrong(
                "level ${index + 1} is metres, nought or more, and ${said(level.depth)} is not",
            )
        }
        levels += minutes to depth
    }
    if (levels.isEmpty()) return Shaped.Waiting
    val gas = try {
        Gas.parse(shaping.gas)
    } catch (refused: ValueFormatException) {
        return Shaped.Wrong(refused.message ?: "${said(shaping.gas)} is not a gas")
    } catch (refused: IllegalArgumentException) {
        return Shaped.Wrong(refused.message ?: "${said(shaping.gas)} is not a gas")
    }
    val low = percentageOf(shaping.gradientLow) ?: return Shaped.Wrong(factorWrong("low", shaping.gradientLow))
    val high = percentageOf(shaping.gradientHigh) ?: return Shaped.Wrong(factorWrong("high", shaping.gradientHigh))
    if (low > high) {
        return Shaped.Wrong("the low gradient factor should not be above the high one")
    }
    val depths = when (val laid = depthsOf(levels, descentRate, ascentRate)) {
        is Laid.Points -> laid.points
        is Laid.Wrong -> return Shaped.Wrong(laid.reason)
    }
    val source = Source(
        gas = gas,
        sac = shaping.sac.trim().toDoubleOrNull(),
        volume = shaping.size.trim().toDoubleOrNull(),
        fill = shaping.fill.trim().toDoubleOrNull(),
    )
    return Shaped.Ready(
        Run(
            depth = depths,
            sources = mapOf(SOURCE to source),
            gradientFactorLow = low,
            gradientFactorHigh = high,
            switches = listOf(0 to SOURCE),
        ),
    )
}

/** What laying the levels out came to: the points of the run, or why they will not lie. */
private sealed class Laid {
    class Points(val points: List<Pair<Int, Double>>) : Laid()

    class Wrong(val reason: String) : Laid()
}

/**
 * The levels as the points a run holds: the surface, then each level travelled to and held.
 *
 * A level at the depth before it needs no travelling, and takes no point of its own beyond the one
 * that ends it: two points at one second is not a run the model will take.
 */
private fun depthsOf(
    levels: List<Pair<Double, Double>>,
    descentRate: Double,
    ascentRate: Double,
): Laid {
    val points = ArrayList<Pair<Int, Double>>()
    points += 0 to 0.0
    var second = 0
    var depth = 0.0
    for ((index, level) in levels.withIndex()) {
        val (minutes, target) = level
        val rate = if (target > depth) descentRate else ascentRate
        val travel = ceil(abs(target - depth) / rate * SECONDS_IN_MINUTE).toInt()
        val whole = (minutes * SECONDS_IN_MINUTE).roundToInt()
        if (whole <= travel) {
            return Laid.Wrong(
                "level ${index + 1} takes ${clockOf(travel)} to reach at ${plain(rate)} m a " +
                    "minute, which ${plain(minutes)} minutes does not leave room for",
            )
        }
        if (travel > 0) {
            second += travel
            points += second to target
        }
        second += whole - travel
        points += second to target
        depth = target
    }
    return Laid.Points(points)
}

/**
 * [run] with [ascent] on the end of it, which is the whole dive: down, along, and up.
 *
 * What the model works out about the ascent — the gas it costs, the oxygen it adds — belongs to
 * the dive as much as the bottom does, so the figures a reader sees are of the two together.
 */
internal fun withAscent(run: Run, ascent: Ascended.Done): Run = Run(
    depth = run.depth + ascent.depth,
    sources = run.sources,
    gradientFactorLow = run.gradientFactorLow,
    gradientFactorHigh = run.gradientFactorHigh,
    switches = run.switches + ascent.switches,
    density = run.density,
    surface = run.surface,
)

/** Stop is one depth the way up holds at, and for how long. */
internal data class Stop(val metres: Double, val seconds: Int)

/**
 * The stops in [ascent]: the stretches it holds a depth rather than rising through it.
 *
 * **Held minutes are gathered into one stop.** The model writes a point a minute while it holds,
 * so a stop of eight minutes arrives as eight stretches, and a reader wants *3 m for 8 min* rather
 * than that said eight times. Gathering by the depth of the stop before is enough: an ascent never
 * returns to a depth it has left.
 */
internal fun stopsOf(ascent: Ascended.Done): List<Stop> {
    val stops = ArrayList<Stop>()
    for (index in 1..<ascent.depth.size) {
        val (was, from) = ascent.depth[index - 1]
        val (now, to) = ascent.depth[index]
        if (from != to || to <= 0 || now <= was) continue
        val held = stops.lastOrNull()
        if (held != null && held.metres == to) {
            stops[stops.lastIndex] = Stop(to, held.seconds + (now - was))
        } else {
            stops += Stop(to, now - was)
        }
    }
    return stops
}

/** A stop as a reader reads one: the depth it is held at, and how long for. */
internal fun stopSaid(stop: Stop): String =
    "${plain(stop.metres)} m for ${plain(stop.seconds / SECONDS_IN_MINUTE)} min"

/** How long the whole run takes, in seconds, which is where its last point sits. */
internal fun runtimeOf(run: Run): Int = run.depth.lastOrNull()?.first ?: 0

/**
 * The plan form: the levels, what is breathed, and what the model makes of the whole run.
 *
 * **The way up is added without being asked for.** A calculation answers as it is typed, as the
 * other two do, and what a reader wants from a plan is the whole dive rather than the bottom of
 * one: the stops, how long it all takes, and what it costs. `GUI-43`.
 */
@Composable
internal fun PlanForm(shaping: Shaping, settings: Settings?) {
    remember(shaping, settings) {
        if (!shaping.prefilled && settings != null) {
            shaping.gradientLow = shownOf(Settings.DEFAULT_GF_LOW, settings.number(Settings.DEFAULT_GF_LOW))
            shaping.gradientHigh = shownOf(Settings.DEFAULT_GF_HIGH, settings.number(Settings.DEFAULT_GF_HIGH))
            shaping.prefilled = true
        }
        shaping
    }
    val descent = settings?.number(Settings.DEFAULT_DESCENT_RATE) ?: FALLBACK_DESCENT_RATE
    val ascent = settings?.number(Settings.DEFAULT_ASCENT_RATE) ?: FALLBACK_ASCENT_RATE
    val last = settings?.number(Settings.DEFAULT_LAST_STOP) ?: FALLBACK_LAST_STOP
    Heading("Dive plan")
    Aside("Each level is reached at the rate below and held for the rest of its minutes.")
    Levels(shaping)
    Field("Gas", shaping.gas, "") { shaping.gas = it }
    Field("GF low", shaping.gradientLow, "%") { shaping.gradientLow = it }
    Field("GF high", shaping.gradientHigh, "%") { shaping.gradientHigh = it }
    Field("SAC", shaping.sac, "L/min") { shaping.sac = it }
    Field("Cylinder size", shaping.size, "L") { shaping.size = it }
    Field("Fill", shaping.fill, "bar") { shaping.fill = it }
    when (val shaped = shapedOf(shaping, descent, ascent)) {
        Shaped.Waiting -> Unit
        is Shaped.Wrong -> Refused(shaped.reason)
        is Shaped.Ready -> Worked(shaped.run, ascent, last)
    }
    Aside(
        "Descending at ${plain(descent)} m a minute, rising at ${plain(ascent)}, shallowest stop " +
            "at ${plain(last)} m, in salt water at sea level. Change them in Settings.",
    )
}

/** The levels, each a row, with a deed to add one and a deed to take one out. */
@Composable
private fun Levels(shaping: Shaping) {
    for ((index, level) in shaping.levels.withIndex()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = "Level ${index + 1}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LABEL),
            )
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = level.minutes,
                    onChange = { shaping.levels[index] = level.copy(minutes = it) },
                    after = "min",
                )
            }
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = level.depth,
                    onChange = { shaping.levels[index] = level.copy(depth = it) },
                    after = "m",
                )
            }
            // The last row is never taken out: a plan with no levels is not one.
            if (shaping.levels.size > 1) {
                IconButton(onClick = { shaping.levels.removeAt(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Take out level ${index + 1}",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Box(modifier = Modifier.width(LABEL))
        IconButton(onClick = { shaping.levels.add(Level()) }) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add a level",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** What the model makes of the levels once the way up is on the end of them. */
@Composable
private fun Worked(run: Run, ascentRate: Double, lastStop: Double) {
    when (val ascended = completeAscent(run, ascentRate, lastStop)) {
        is Ascended.Refused -> Refused(ascended.reason)
        is Ascended.Done -> {
            val whole = withAscent(run, ascended)
            val stops = stopsOf(ascended)
            Said("Stops", if (stops.isEmpty()) "none" else stops.joinToString(", ") { stopSaid(it) })
            Said("Runtime", spanOf(runtimeOf(whole).toDouble()))
            when (val evaluated = evaluate(whole)) {
                is Evaluated.Refused -> Refused(evaluated.reason)
                is Evaluated.Done -> {
                    // The stops are shown above, depth by depth; the figure that says only how
                    // deep they begin would be the same answer twice.
                    for (figure in runFiguresOf(evaluated, mapOf(SOURCE to "Gas"), stops = false)) {
                        Said(figure.label, figure.text, worked = true)
                    }
                    for (finding in findingsSaidOf(evaluated)) {
                        Said(finding.label, finding.text, wrong = finding.wrong)
                    }
                }
            }
        }
    }
}

/** One line of the answer: what it is, and what it says. */
@Composable
private fun Said(label: String, said: String, worked: Boolean = false, wrong: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
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
            text = said,
            style = MaterialTheme.typography.bodyMedium,
            color = when {
                wrong -> MaterialTheme.colorScheme.error
                worked -> MaterialTheme.colorScheme.outline
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** A percentage from 1 to 100 as a proportion, or absent where [typed] is not one. `GUI-41`. */
private fun percentageOf(typed: String): Double? =
    typed.trim().removeSuffix("%").trim().toDoubleOrNull()?.takeIf { it >= 1 && it <= PERCENT }
        ?.let { it / PERCENT }

/** What to say of a gradient factor that will not do. */
private fun factorWrong(which: String, typed: String): String = if (typed.isBlank()) {
    "a plan needs its $which gradient factor, which says how conservative it is"
} else {
    "the $which gradient factor is a percentage from 1 to 100, and ${said(typed)} is not"
}

/** Something typed, quoted, or *nothing* where nothing was. */
private fun said(typed: String): String = if (typed.isBlank()) "nothing" else "\"${typed.trim()}\""

/** A length of time as a clock reads it, minutes and seconds. */
private fun clockOf(seconds: Int): String =
    "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** What the one cylinder of a planned run is keyed as. */
private const val SOURCE = "g1"

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0

/** How wide a level's box is. */
private val LEVEL = 110.dp
