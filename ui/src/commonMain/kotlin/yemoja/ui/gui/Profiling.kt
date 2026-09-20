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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import yemoja.logic.maximumOperatingDepth
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
internal data class Level(
    val minutes: String = "",
    val depth: String = "",
    /** Which of the gases is breathed from this level on, by its place in the list. */
    val gas: Int = 0,
)

/**
 * Breathed is one cylinder as it is typed: what is in it, and what is known of its size and use.
 *
 * Immutable.
 */
internal data class Breathed(
    val gas: String = "air",
    val sac: String = "",
    val size: String = "",
    val fill: String = "",
)

/**
 * Shaping is the plan form while the tab holds it: the levels, what is breathed, and how
 * conservative to be.
 *
 * Not immutable.
 */
internal class Shaping {
    val levels = mutableStateListOf(Level())

    /** The cylinders, in the order they are listed, which is the order they are named in. */
    val gases = mutableStateListOf(Breathed())

    var gradientLow: String by mutableStateOf("")
    var gradientHigh: String by mutableStateOf("")

    /** Whether the form has been opened before, which decides whether it takes the settings. */
    var prefilled: Boolean = false
}

/** What the cylinder at [index] is called, which is what a reader sees of a key. */
internal fun gasLabelOf(index: Int): String = "Gas ${index + 1}"

/** The key the cylinder at [index] sits under, as a dive's own cylinders sit under keys. */
internal fun gasKeyOf(index: Int): String = "g${index + 1}"

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
    val levels = ArrayList<Laid.Level>()
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
        levels += Laid.Level(minutes, depth, level.gas)
    }
    if (levels.isEmpty()) return Shaped.Waiting
    val sources = LinkedHashMap<String, Source>()
    for ((index, breathed) in shaping.gases.withIndex()) {
        val gas = try {
            Gas.parse(breathed.gas)
        } catch (refused: ValueFormatException) {
            return Shaped.Wrong("${gasLabelOf(index)}: ${refused.message ?: "not a gas"}")
        } catch (refused: IllegalArgumentException) {
            return Shaped.Wrong("${gasLabelOf(index)}: ${refused.message ?: "not a gas"}")
        }
        sources[gasKeyOf(index)] = Source(
            gas = gas,
            sac = breathed.sac.trim().toDoubleOrNull(),
            volume = breathed.size.trim().toDoubleOrNull(),
            fill = breathed.fill.trim().toDoubleOrNull(),
        )
    }
    val low = percentageOf(shaping.gradientLow) ?: return Shaped.Wrong(factorWrong("low", shaping.gradientLow))
    val high = percentageOf(shaping.gradientHigh) ?: return Shaped.Wrong(factorWrong("high", shaping.gradientHigh))
    if (low > high) {
        return Shaped.Wrong("the low gradient factor should not be above the high one")
    }
    val laid = depthsOf(levels, descentRate, ascentRate)
    val points = when (laid) {
        is Laid.Points -> laid.points
        is Laid.Wrong -> return Shaped.Wrong(laid.reason)
    }
    return Shaped.Ready(
        Run(
            depth = points,
            sources = sources,
            gradientFactorLow = low,
            gradientFactorHigh = high,
            switches = switchesOf(levels, laid.begins, sources.size),
        ),
    )
}

/**
 * The switches [levels] ask for: one at the start, and one wherever a level changes cylinder.
 *
 * **The first is always at nought.** A run saying nothing about what was breathed before its first
 * switch is refused, and rightly: it is the gap a computer leaves when it logs only its changes.
 * A level names the cylinder it is breathed on, so the first level names what the dive goes in on
 * however the list is ordered. A level naming a cylinder that is no longer listed falls back to
 * the one before it, the list being the reader's to shorten while they think. `LOGIC-37`.
 *
 * The ascent's own switches are not here: `completeAscent` works those out and hands them back,
 * so a reader lists a deco gas and is switched to it without saying when.
 */
internal fun switchesOf(
    levels: List<Laid.Level>,
    begins: List<Int>,
    gases: Int,
): List<Pair<Int, String>> {
    val switches = ArrayList<Pair<Int, String>>()
    var breathing = ""
    for ((index, level) in levels.withIndex()) {
        val key = gasKeyOf(level.gas.coerceIn(0, gases - 1))
        if (key == breathing) continue
        switches += (if (switches.isEmpty()) 0 else begins[index]) to key
        breathing = key
    }
    return switches
}

/** What laying the levels out came to: the points of the run, or why they will not lie. */
internal sealed class Laid {

    /** One level read from the form: how long, how deep, and which cylinder on. */
    data class Level(val minutes: Double, val metres: Double, val gas: Int)

    /** The points of the run, and the second each level begins to be travelled to. */
    class Points(val points: List<Pair<Int, Double>>, val begins: List<Int>) : Laid()

    class Wrong(val reason: String) : Laid()
}

/**
 * The levels as the points a run holds: the surface, then each level travelled to and held.
 *
 * A level at the depth before it needs no travelling, and takes no point of its own beyond the one
 * that ends it: two points at one second is not a run the model will take.
 */
private fun depthsOf(
    levels: List<Laid.Level>,
    descentRate: Double,
    ascentRate: Double,
): Laid {
    val points = ArrayList<Pair<Int, Double>>()
    val begins = ArrayList<Int>()
    points += 0 to 0.0
    var second = 0
    var depth = 0.0
    for ((index, level) in levels.withIndex()) {
        val (minutes, target) = level
        begins += second
        val rate = if (target > depth) descentRate else ascentRate
        val travel = ceil(abs(target - depth) / rate * SECONDS_IN_MINUTE).toInt()
        val whole = (minutes * SECONDS_IN_MINUTE).roundToInt()
        if (whole < travel) {
            return Laid.Wrong(
                "level ${index + 1} takes ${clockOf(travel)} to reach at ${plain(rate)} m a " +
                    "minute, which ${plain(minutes)} minutes does not leave room for",
            )
        }
        if (travel > 0) {
            second += travel
            points += second to target
        }
        // A level with nothing left after the travel is written once. Two points at one second
        // would say the run was in two places.
        if (whole > travel) {
            second += whole - travel
            points += second to target
        }
        depth = target
    }
    return Laid.Points(points, begins)
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
/** Which cylinder [key] is, by its place in the list the keys were minted from. */
internal fun gasIndexOf(key: String): Int = (key.removePrefix("g").toIntOrNull() ?: 1) - 1

/**
 * The way up [ascent] worked out, written as levels of [run], so that it can be typed over.
 *
 * Each stretch becomes one level: the rise to a depth and the hold there are one row, because that
 * is what a level already means. **The ascent does not repeat the point it leaves from**, so the
 * run's own last point begins the first stretch.
 *
 * A level is written at least as long as this form's own arithmetic will read it as taking, and
 * rounded up to a hundredth of a minute besides. The model and the form round a travel time
 * differently, by a second at most, and a level a second short of its own rise would be refused the
 * moment it was written. What the rounding adds is held at the stop, which is the safe direction.
 *
 * The cylinder is the one being breathed when the level began, so a deco switch the model made for
 * itself is kept. Taking it from the beginning rather than the end means a rise carries the mix it
 * started on, and the plan never breathes one deeper than the model did.
 */
internal fun ascentLevelsOf(
    run: Run,
    ascent: Ascended.Done,
    ascentRate: Double,
    gases: Int,
): List<Level> {
    val points = listOfNotNull(run.depth.lastOrNull()) + ascent.depth
    val levels = ArrayList<Level>()
    var breathing = carriedOf(run)
    var at = 0
    while (at < points.lastIndex) {
        val (began, from) = points[at]
        var next = at + 1
        val target = points[next].second
        while (next < points.lastIndex && points[next + 1].second == target) next++
        val ends = points[next].first
        ascent.switches.lastOrNull { it.first <= began }?.let { breathing = gasIndexOf(it.second) }
        val travel = ceil(abs(target - from) / ascentRate * SECONDS_IN_MINUTE).toInt()
        val seconds = maxOf(ends - began, travel)
        if (seconds > 0) {
            levels += Level(
                minutes = plain(ceil(seconds / HUNDREDTHS_IN_MINUTE) / PER_HUNDRED),
                depth = plain(target),
                gas = breathing.coerceIn(0, gases - 1),
            )
        }
        at = next
    }
    return levels
}

/** What the run is breathing where it stops being typed and starts being worked out. */
internal fun carriedOf(run: Run): Int = run.switches.lastOrNull()?.let { gasIndexOf(it.second) } ?: 0

/** Seconds in a hundredth of a minute, which is as fine as a written level is rounded. */
private const val HUNDREDTHS_IN_MINUTE = 0.6

private const val PER_HUNDRED = 100.0

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
    Gases(shaping)
    Field("GF low", shaping.gradientLow, "%") { shaping.gradientLow = it }
    Field("GF high", shaping.gradientHigh, "%") { shaping.gradientHigh = it }
    when (val shaped = shapedOf(shaping, descent, ascent)) {
        Shaped.Waiting -> Unit
        is Shaped.Wrong -> Refused(shaped.reason)
        is Shaped.Ready -> Worked(shaped.run, shaping, ascent, last)
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
            // Which cylinder, where there is a choice: a level breathes one from its start, and
            // one cylinder needs no saying. The ascent's own switches are the model's. `GUI-43`.
            if (shaping.gases.size > 1) {
                Chosen(
                    chosen = gasLabelOf(level.gas.coerceIn(0, shaping.gases.size - 1)),
                    options = shaping.gases.indices.map { gasLabelOf(it) },
                ) { chose -> shaping.levels[index] = level.copy(gas = chose) }
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
    Added("Add a level") { shaping.levels.add(Level(gas = shaping.levels.lastOrNull()?.gas ?: 0)) }
}

/**
 * The cylinders, each a row: what is in it, how fast it is breathed, and what it holds.
 *
 * How deep the mix may be breathed is said beside it, from the model rather than from a limit of
 * this form's own, so what a reader is told here and what the model objects to are one figure.
 * `LOGIC-39`.
 */
@Composable
private fun Gases(shaping: Shaping) {
    for ((index, breathed) in shaping.gases.withIndex()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GAP),
        ) {
            Text(
                text = gasLabelOf(index),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                modifier = Modifier.width(LABEL),
            )
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = breathed.gas,
                    onChange = { shaping.gases[index] = breathed.copy(gas = it) },
                )
            }
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = breathed.sac,
                    onChange = { shaping.gases[index] = breathed.copy(sac = it) },
                    after = "L/min",
                )
            }
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = breathed.size,
                    onChange = { shaping.gases[index] = breathed.copy(size = it) },
                    after = "L",
                )
            }
            Box(modifier = Modifier.width(LEVEL)) {
                Compact(
                    value = breathed.fill,
                    onChange = { shaping.gases[index] = breathed.copy(fill = it) },
                    after = "bar",
                )
            }
            Text(
                text = deepestSaid(breathed.gas),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
            )
            // The first is never taken out: it is what the dive goes in on.
            if (index > 0) {
                IconButton(onClick = { shaping.gases.removeAt(index) }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Take out ${gasLabelOf(index)}",
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
    Added("Add a gas") { shaping.gases.add(Breathed()) }
}

/** A deed that adds a row, under the rows it adds to. */
@Composable
private fun Added(said: String, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        Box(modifier = Modifier.width(LABEL))
        IconButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = said,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** One of [options], chosen from a menu that names them. */
@Composable
private fun Chosen(chosen: String, options: List<String>, onChoose: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { picking = true }) {
            Text(chosen, style = MaterialTheme.typography.bodyMedium)
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        Menu(expanded = picking, onDismissRequest = { picking = false }) {
            for ((index, option) in options.withIndex()) {
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onChoose(index)
                        picking = false
                    },
                )
            }
        }
    }
}

/** The deed that writes the way up into the levels, with a word on what it leaves behind. */
@Composable
private fun Written(onWrite: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        Box(modifier = Modifier.width(LABEL))
        Button(onClick = onWrite) { Text(ADD_THE_ASCENT) }
        Text(
            text = "The levels then hold the whole dive, and are yours to change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/** What the deed that writes the ascent into the levels is called. */
internal const val ADD_THE_ASCENT = "Add ascent"

/** What the model makes of the levels once the way up is on the end of them. */
@Composable
private fun Worked(run: Run, shaping: Shaping, ascentRate: Double, lastStop: Double) {
    val tanks = run.sources.keys.withIndex().associate { (index, key) -> key to gasLabelOf(index) }
    when (val ascended = completeAscent(run, ascentRate, lastStop)) {
        is Ascended.Refused -> Refused(ascended.reason)
        is Ascended.Done -> {
            val whole = withAscent(run, ascended)
            val stops = stopsOf(ascended)
            Said("Stops", if (stops.isEmpty()) "none" else stops.joinToString(", ") { stopSaid(it) })
            // Shown only where it would write something. A plan already ending at the surface has
            // no way up left to add, and one edited to stop short of it has again. `GUI-43`.
            val writing = ascentLevelsOf(run, ascended, ascentRate, shaping.gases.size)
            if (writing.isNotEmpty()) {
                Written {
                    shaping.levels.removeAll { it.minutes.isBlank() && it.depth.isBlank() }
                    shaping.levels.addAll(writing)
                }
            }
            Said("Runtime", spanOf(runtimeOf(whole).toDouble()))
            when (val evaluated = evaluate(whole)) {
                is Evaluated.Refused -> Refused(evaluated.reason)
                is Evaluated.Done -> {
                    // The stops are shown above, depth by depth; the figure that says only how
                    // deep they begin would be the same answer twice.
                    for (figure in runFiguresOf(evaluated, tanks, stops = false)) {
                        Said(figure.label, figure.text, worked = true)
                    }
                    for (finding in findingsSaidOf(evaluated, tanks)) {
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

/**
 * How deep [typed] may be breathed, as the row says it, or nothing where it is not a gas.
 *
 * The model's own limit, `LOGIC-39`, rather than a rule of this form's: a reader placing a deco gas
 * by what it says here and the model objecting to the plan they placed would be one figure kept in
 * two places.
 */
private fun deepestSaid(typed: String): String {
    val gas = try {
        Gas.parse(typed)
    } catch (refused: ValueFormatException) {
        return ""
    } catch (refused: IllegalArgumentException) {
        return ""
    }
    val deepest = maximumOperatingDepth(gas) ?: return ""
    return "to ${plain((deepest * 10).roundToInt() / 10.0)} m"
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

private const val SECONDS_IN_MINUTE = 60.0

private const val PERCENT = 100.0

/** How wide a level's box is. */
private val LEVEL = 110.dp
