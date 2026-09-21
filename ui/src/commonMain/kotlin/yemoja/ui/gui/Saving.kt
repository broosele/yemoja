package yemoja.ui.gui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import yemoja.data.Element
import yemoja.data.Gas
import yemoja.data.Item
import yemoja.data.ItemWriter
import yemoja.data.KeyReference
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Stored
import yemoja.data.Units
import yemoja.logic.Change
import yemoja.logic.Outcome
import yemoja.logic.Run
import yemoja.logic.Settings
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.freeName
import yemoja.logic.planKeyOf
import yemoja.logic.planName

/*
 * A plan from the Calculations tab put into the logbook, and a saved plan taken back out.
 *
 * **One way.** A plan is saved as the points of its run, the cylinders and the gradient factors,
 * which is what a planned profile holds. The planner's own settings, the oxygen limits, the safety
 * stop, the rates, the reserve, and which lines were typed, have nowhere to go and are not kept.
 * A plan opened again is its points as typed lines, under the settings the planner starts from.
 *
 * See ../../../../../../gui/doc.md — `GUI-44`.
 */

/** Bound is the dive a plan is being typed for, and whether it is a new plan there or one saved before. */
internal sealed class Bound {

    /** The dive a new plan will be added to. */
    abstract val dive: String

    /** A new plan on [dive], under a name not yet taken there. */
    data class Adding(override val dive: String) : Bound()

    /** The plan under [key] on [dive], saved over. */
    data class Editing(override val dive: String, val key: String) : Bound()
}

/**
 * Saving is where the plan in the Calculations tab will go, and what the last save said.
 *
 * Not immutable.
 */
internal class Saving {
    var bound: Bound? by mutableStateOf(null)

    /** What the plan will be called on the dive it is added to, typed over freely. */
    var name: String by mutableStateOf("Plan A")

    var said: String? by mutableStateOf(null)
}

/** Opens the planner for [bound], from the Dives tab. Absent where no tab can be switched to. */
internal val LocalPlanOpener = staticCompositionLocalOf<((Bound) -> Unit)?> { null }

// --- A plan as the fields of a profile.

/**
 * The fields of a planned profile holding [whole], the run [shaping] describes with its way up,
 * under [conditions].
 *
 * The cylinders are keyed as a gas source's key is proposed: by what each is for, `bottom`,
 * `deco`, `bailout`, with `#1` where two are for the same. `JSON-18`.
 */
internal fun planFieldsOf(shaping: Shaping, conditions: Conditions, whole: Run): Map<String, Stored> {
    val keys = ArrayList<String>()
    val sources = LinkedHashMap<String, Stored>()
    for (breathed in shaping.gases) {
        val key = freeName(usageOf(breathed.role)) { it in keys }
        keys += key
        val source = linkedMapOf<String, Stored>()
        gasOf(breathed.gas)?.let { source["gas_type"] = Stored.Leaf(it.toString()) }
        source["usage"] = Stored.Leaf(usageOf(breathed.role))
        numberOf(breathed.size)?.let { source["volume"] = Stored.Leaf(it) }
        numberOf(breathed.fill)?.let { source["start_pressure"] = Stored.Leaf(it) }
        numberOf(breathed.sac)?.let { source["sac"] = Stored.Leaf(it) }
        sources[key] = Stored.Members(source)
    }
    return linkedMapOf(
        "planned" to Stored.Leaf(true),
        "water_type" to Stored.Leaf(shaping.water),
        "deco_model" to Stored.Leaf(MODEL),
        "gradient_factor_low" to Stored.Leaf(conditions.gradientLow),
        "gradient_factor_high" to Stored.Leaf(conditions.gradientHigh),
        "depth" to Stored.Elements(whole.depth.map { (second, metres) -> sample(second, Stored.Leaf(metres)) }),
        "gas_switches" to Stored.Elements(
            whole.switches.map { (second, key) ->
                sample(second, Stored.Leaf("*" + keys[gasIndexOf(key).coerceIn(0, keys.size - 1)]))
            },
        ),
        "gas_sources" to Stored.Members(sources),
    )
}

/** What a cylinder of [role] is for, in the words `usage` uses. */
internal fun usageOf(role: Role): String = role.name.lowercase()

/** The role a cylinder `usage` says, or bottom where it says something the planner has no role for. */
internal fun roleOf(usage: String?): Role = when (usage?.trim()?.lowercase()) {
    "deco" -> Role.DECO
    "bailout" -> Role.BAILOUT
    else -> Role.BOTTOM
}

/** The name a new plan on [dive] starts with: the first `Plan` letter it does not already hold. */
internal fun planNameOn(dive: Item?): String {
    val taken = dive?.let { keyedEntriesOf(it, "profiles").map { (key, _) -> key } }.orEmpty().toSet()
    return planName { planKeyOf(it) in taken }
}

/** The changes that make a new dive holding only the plan [fields], under [key]. */
internal fun newDiveOf(key: String, fields: Map<String, Stored>): List<Change> = listOf(
    Change.Add(
        Types.DIVE,
        mapOf(
            "primary_profile" to Stored.Leaf("*$key"),
            "profiles" to Stored.Members(mapOf(key to Stored.Members(fields))),
        ),
    ),
)

/**
 * The changes that put the plan [fields] on [dive] under [key]: a new profile where there is none
 * under it, or over the one there, keeping whatever else was written on it.
 *
 * A dive with no profile before takes the plan as its primary one, as a new dive does; one with a
 * recording keeps the recording.
 */
internal fun onDiveOf(dive: Item, key: String, fields: Map<String, Stored>): List<Change> {
    val written = LinkedHashMap<String, Stored>()
    for ((held, entry) in keyedEntriesOf(dive, "profiles")) {
        written[held] = ItemWriter.write(entry, Units.DEFAULT)
    }
    val kept = (written[key] as? Stored.Members)?.members.orEmpty()
    written[key] = Stored.Members(kept + fields)
    val changes = mutableListOf<Change>(Change.Write(dive, "profiles", Stored.Members(written)))
    if (written.size == 1) changes += Change.Write(dive, "primary_profile", Stored.Leaf("*$key"))
    return changes
}

// --- A saved plan taken back out.

/**
 * Fills [shaping] with the plan [profile] holds: its points as typed lines, its cylinders, its
 * gradient factors and its water. The planner's other settings are left as they are.
 *
 * **Each pair of points is a line**, a rise, a descent or a stay, timed by its duration so the
 * points come back to the second. The minutes a stop is held for, which the model writes a point
 * at a time, are one line. A line is given its gas where a switch falls at its start, and follows
 * the line above everywhere else.
 */
internal fun Shaping.loadFrom(profile: Item, dive: Item?) {
    val sources = keyedEntriesOf(profile, "gas_sources").ifEmpty {
        dive?.let { keyedEntriesOf(it, "gas_sources") }.orEmpty()
    }
    gases.clear()
    lostGas = null
    val index = HashMap<String, Int>()
    for ((key, source) in sources) {
        index[key] = gases.size
        gases += Breathed(
            gas = (source.single<Gas>("gas_type") as? Result.Usable)?.value?.toString() ?: "air",
            role = roleOf((source.single<String>("usage") as? Result.Usable)?.value),
            size = numberSaid(source, "volume"),
            fill = numberSaid(source, "start_pressure"),
            sac = numberSaid(source, "sac"),
        )
    }
    if (gases.isEmpty()) gases += Breathed()

    val points = numbersOf(profile, "depth")
    val switches = keysOf(profile, "gas_switches").mapNotNull { (second, key) -> index[key]?.let { second to it } }
    val lines = ArrayList<Triple<Double, Int, Int?>>()
    var breathing = 0
    for (at in 1..<points.size) {
        val (began, from) = points[at - 1]
        val (ends, to) = points[at]
        if (ends <= began) continue
        val switched = switches.lastOrNull { it.first in began..<ends }?.second
        val named = switched?.takeIf { it != breathing || lines.isEmpty() && it != 0 }
        switched?.let { breathing = it }
        val last = lines.lastOrNull()
        if (last != null && named == null && to == from && last.first == to && points[at - 2].second == to) {
            lines[lines.lastIndex] = Triple(to, last.second + (ends - began), last.third)
        } else {
            lines += Triple(to, ends - began, named)
        }
    }
    segments.clear()
    for ((metres, seconds, gas) in lines) {
        segments += Segment(depth = plain(metres), duration = clockOf(seconds), gas = gas)
    }
    if (segments.isEmpty()) segments += Segment()

    (profile.single<Double>("gradient_factor_low") as? Result.Usable)?.value?.let { gradientLow = plain(it * 100) }
    (profile.single<Double>("gradient_factor_high") as? Result.Usable)?.value?.let { gradientHigh = plain(it * 100) }
    (profile.single<String>("water_type") as? Result.Usable)?.value
        ?.takeIf { it in Settings.DEFAULT_WATER_TYPE.choices }?.let { water = it }
}

/** Empties [shaping] to a plan of one blank line on one cylinder, under the settings' defaults. */
internal fun Shaping.startAfresh(settings: Settings?) {
    segments.clear()
    segments += Segment()
    gases.clear()
    gases += Breathed()
    // A cylinder chosen as lost names a place in the list just emptied, so the choice goes with it.
    lostGas = null
    prefill(settings)
}

/**
 * Makes [saving] save to [bound], and fills [shaping] with what it starts from there: the plan
 * already saved where it is being edited, and a blank one where one is being added.
 */
internal fun Saving.open(bound: Bound, universe: Universe?, shaping: Shaping) {
    val dive = universe?.logbook?.get(bound.dive)
    this.bound = bound
    said = null
    when (bound) {
        is Bound.Adding -> {
            shaping.startAfresh(universe?.settings)
            name = planNameOn(dive)
        }
        is Bound.Editing -> {
            shaping.startAfresh(universe?.settings)
            val profile = dive?.let { keyedEntriesOf(it, "profiles").firstOrNull { (key, _) -> key == bound.key }?.second }
            if (profile != null) shaping.loadFrom(profile, dive)
            name = prettyOf(bound.key)
        }
    }
}

// --- The row a plan is saved from.

/**
 * The save row under the plan's heading: what it will be called, where it goes, and the deeds.
 *
 * **A plan with nothing wrong is saved; one the model cannot answer for is not**, since what is
 * written is the run the model worked out, its way up included. Where it goes is said beside the
 * deed rather than asked for after it, so a reader sees *adds Plan B to …* before pressing.
 */
@Composable
internal fun SaveRow(saving: Saving, shaping: Shaping, universe: Universe?) {
    val changer = LocalChanger.current
    // A plan bound to a dive since taken out saves as a new one.
    val bound = saving.bound?.takeIf { universe?.logbook?.get(it.dive) != null }
    val dive = bound?.let { universe?.logbook?.get(it.dive) }
    val ready = shapedOf(shaping) as? Shaped.Ready
    val done = ready?.let { workedOf(it) as? Worked.Done }
    val unsaved = when {
        universe == null -> "Open a logbook to save a plan into it."
        done == null -> "The plan needs to be one the model can work out before it is saved."
        else -> null
    }
    fun save(target: Bound?) {
        val fields = planFieldsOf(shaping, ready!!.conditions, done!!.whole)
        val key = if (target is Bound.Editing) target.key else planKeyOf(saving.name)
        val outcome = when (target) {
            null -> changer.change(newDiveOf(key, fields))
            else -> changer.change(onDiveOf(universe!!.logbook[target.dive]!!, key, fields))
        }
        saving.said = when (outcome) {
            is Outcome.Refused -> outcome.reason
            is Outcome.Done -> {
                val id = if (target == null) outcome.added.firstOrNull() else target.dive
                id?.let { saving.bound = Bound.Editing(it, key) }
                if (target == null) "Saved as ${prettyOf(key)} on a new dive." else "Saved as ${prettyOf(key)}."
            }
        }
    }
    val nameWrong = when {
        planKeyOf(saving.name).isEmpty() -> "A saved plan needs a name."
        bound is Bound.Adding && dive != null &&
            keyedEntriesOf(dive, "profiles").any { (key, _) -> key == planKeyOf(saving.name) } ->
            "That dive already holds ${saving.name.trim()}."
        else -> null
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = HALF),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (bound is Bound.Editing) {
            Text(prettyOf(bound.key), style = MaterialTheme.typography.titleSmall)
        } else {
            Box(modifier = Modifier.width(NAME)) {
                Compact(value = saving.name, onChange = { saving.name = it; saving.said = null }, dense = true, wrong = nameWrong != null)
            }
        }
        val target = when (bound) {
            null -> null
            else -> dive?.let { titleOf(it) }
        }
        val blocked = unsaved ?: nameWrong
        if (target != null) {
            Explained(blocked) {
                Button(onClick = { save(bound) }, enabled = blocked == null) {
                    Text(if (bound is Bound.Editing) "Save" else "Add to $target")
                }
            }
        }
        Explained(blocked) {
            val deed = @Composable { Text("Save as new dive") }
            if (target == null) {
                Button(onClick = { save(null) }, enabled = blocked == null) { deed() }
            } else {
                OutlinedButton(onClick = { save(null) }, enabled = blocked == null) { deed() }
            }
        }
        if (bound != null) {
            TextButton(onClick = { saving.bound = null; saving.said = null }) { Text("Forget the dive") }
        }
        Text(
            text = saving.said ?: when (bound) {
                is Bound.Editing -> "Saves over ${prettyOf(bound.key)} of ${target ?: "its dive"}."
                is Bound.Adding -> "Adds a plan to ${target ?: "its dive"}."
                null -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

// --- Reading a profile.

/** The numbers a series of [profile] holds, as seconds and values, leaving out what will not read. */
private fun numbersOf(profile: Item, field: String): List<Pair<Int, Double>> {
    val series = seriesIn(profile, field) ?: return emptyList()
    return (0..<series.size).mapNotNull { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value as? Number ?: return@mapNotNull null
        series.secondAt(at) to value.toDouble()
    }
}

/** The keys a series of key references in [profile] names, as seconds and keys. */
private fun keysOf(profile: Item, field: String): List<Pair<Int, String>> {
    val series = seriesIn(profile, field) ?: return emptyList()
    return (0..<series.size).mapNotNull { at ->
        val value = (series.valueAt(at) as? Element.Usable)?.value as? KeyReference ?: return@mapNotNull null
        series.secondAt(at) to value.key
    }
}

private fun seriesIn(profile: Item, field: String): Series? {
    if (profile.description[field] == null) return null
    return (profile.read(field) as? Result.Usable)?.value as? Series
}

/** A number field of [item] as a box shows it, or blank. */
private fun numberSaid(item: OwnedItem, field: String): String =
    (item.single<Double>(field) as? Result.Usable)?.value?.let { plain(it) }.orEmpty()

private fun numberOf(typed: String): Double? = typed.trim().toDoubleOrNull()

private fun gasOf(typed: String): Gas? = try {
    Gas.parse(typed)
} catch (refused: Exception) {
    null
}

/** One sample of a series, as a file writes it: the second, then the value. */
private fun sample(second: Int, value: Stored): Stored = Stored.Elements(listOf(Stored.Leaf(second.toLong()), value))

/** The one model built, which a plan is worked out with. `LOGIC-3`. */
private const val MODEL = "buhlmann"

/** How wide the box a plan's name is typed in is. */
private val NAME = 120.dp
