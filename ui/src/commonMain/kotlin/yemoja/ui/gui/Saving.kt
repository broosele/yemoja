package yemoja.ui.gui

import yemoja.data.ReferenceableItem
import androidx.compose.runtime.remember
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
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
import yemoja.data.Reference
import yemoja.data.Time
import yemoja.data.Date
import yemoja.data.Units
import yemoja.logic.Breathed
import yemoja.logic.Change
import yemoja.logic.Conditions
import yemoja.logic.clockOf
import yemoja.logic.Following
import yemoja.logic.Outcome
import yemoja.logic.Planned
import yemoja.logic.Role
import yemoja.logic.Run
import yemoja.logic.NumberSetting
import yemoja.logic.Segment
import yemoja.logic.Settings
import yemoja.logic.Shaped
import yemoja.logic.Start
import yemoja.logic.Types
import yemoja.logic.Universe
import yemoja.logic.Worked
import yemoja.logic.durationOf
import yemoja.logic.freeName
import yemoja.logic.gasIndexOf
import yemoja.logic.problemSecondsOf
import yemoja.logic.startOf
import yemoja.logic.planKeyOf
import yemoja.logic.planName
import yemoja.logic.shapedOf
import yemoja.logic.shownOf
import yemoja.logic.titleOf
import yemoja.logic.workedOf
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * A plan from the Calculations tab put into the logbook, and a saved plan taken back out.
 *
 * A plan is saved as the points of its run with its way up, its cylinders, every setting the
 * planner had, and its lines as typed. The points are the plan, and the rest is kept so that it
 * opens again as it was saved. `DATA-129`.
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
    var name: String by mutableStateOf(yemoja.ui.api.FIRST_PLAN)

    var said: String? by mutableStateOf(null)

    /**
     * The plan as it was last opened, saved or started, which tells whether it has changed since.
     * Null before any of those, which is a blank plan.
     */
    var kept: Planned? by mutableStateOf(null)
}

/**
 * Whether [shaping] holds what was never saved: anything changed since the plan was opened, saved
 * or started. A plan nobody has touched is a blank one under [settings]. `GUI-54`.
 */
internal fun Saving.isChanged(shaping: Shaping, settings: Settings?): Boolean =
    shaping.described() != (kept ?: Shaping().also { it.startAfresh(settings) }.described())

/** Empties the planner to a new plan, bound to no dive and called by the first name. `GUI-54`. */
internal fun Saving.startNew(shaping: Shaping, settings: Settings?) {
    bound = null
    name = yemoja.ui.api.FIRST_PLAN
    said = null
    shaping.startAfresh(settings)
    kept = shaping.described()
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
internal fun planFieldsOf(shaping: Planned, conditions: Conditions, whole: Run): Map<String, Stored> {
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
    val began = (startOf(shaping) as? Start.At)?.moment
    val start = if (began == null) {
        emptyMap()
    } else {
        // Written as a file writes them, which is what a leaf of a date or a time is read from.
        mapOf("start_date" to Stored.Leaf(began.date.toString()), "start_time" to Stored.Leaf(began.time.toString()))
    }
    // Named on the plan itself, since a dive's own previous dive speaks for its primary run alone.
    val after = shaping.following?.let { mapOf("previous_profile" to Stored.Leaf("@${it.dive}*${it.key}")) }.orEmpty()
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
    ) + start + after + plannerFieldsOf(shaping, conditions, keys)
}

/**
 * What the planner was set to for [shaping], and its lines as typed, for opening it again. `DATA-129`.
 *
 * A setting that will not read is left out, and the planner's own starting value answers for it
 * when the plan is opened. A blank line is not kept.
 */
private fun plannerFieldsOf(shaping: Planned, conditions: Conditions, keys: List<String>): Map<String, Stored> {
    val fields = linkedMapOf<String, Stored>(
        "po2_max_bottom" to Stored.Leaf(conditions.bottomOxygen),
        "po2_max_deco" to Stored.Leaf(conditions.decoOxygen),
        "po2_min" to Stored.Leaf(conditions.leastOxygen),
        "descent_rate" to Stored.Leaf(conditions.descentRate),
        "ascent_rate" to Stored.Leaf(conditions.ascentRate),
        "last_stop" to Stored.Leaf(conditions.lastStop),
        "gas_switch_stops" to Stored.Leaf(conditions.switchStops),
        "safety_stop_depth" to Stored.Leaf(conditions.safetyDepth),
        "safety_stop_duration" to Stored.Leaf(conditions.safetySeconds.toLong()),
    )
    numberOf(shaping.stressFactor)?.let { fields["stress_factor"] = Stored.Leaf(it) }
    problemSecondsOf(shaping)?.let { fields["problem_solving_time"] = Stored.Leaf(it.toLong()) }
    fields["lost_gas_reserve"] = Stored.Leaf(shaping.lostGasScenario)
    shaping.lostGas?.let { keys.getOrNull(it) }?.let { fields["lost_gas"] = Stored.Leaf("*$it") }
    fields["shared_gas_reserve"] = Stored.Leaf(shaping.sharedScenario)
    val lines = LinkedHashMap<String, Stored>()
    for (segment in shaping.segments) {
        val depth = numberOf(segment.depth) ?: continue
        val line = linkedMapOf<String, Stored>("depth" to Stored.Leaf(depth))
        durationOf(segment.duration)?.let { line["duration"] = Stored.Leaf(it.toLong()) }
        numberOf(segment.rate)?.let { line["rate"] = Stored.Leaf(it) }
        segment.gas?.let { keys.getOrNull(it) }?.let { line["gas_source"] = Stored.Leaf("*$it") }
        lines["${lines.size + 1}"] = Stored.Members(line)
    }
    fields["runtime"] = Stored.Members(lines)
    return fields
}

/**
 * What a new dive holding [shaping]'s plan says of itself besides the plan: the dive it follows, and
 * that dive's time zone, so the surface interval it works out is the one the planner showed.
 */
internal fun diveFieldsOf(shaping: Planned, universe: Universe?): Map<String, Stored> {
    val following = shaping.following ?: return emptyMap()
    val fields = linkedMapOf<String, Stored>("previous_dive" to Stored.Leaf("@${following.dive}"))
    val earlier = universe?.logbook?.get(following.dive)
    (earlier?.single<Double>("time_zone_offset") as? Result.Usable)?.value?.let {
        fields["time_zone_offset"] = Stored.Leaf(it)
    }
    return fields
}

/**
 * Why [shaping]'s plan cannot go on [dive], or null where it can: a dive follows one dive, so a plan
 * following another than the one [dive] already names would contradict it.
 */
internal fun followingClashOf(shaping: Planned, dive: Item): String? {
    val following = shaping.following ?: return null
    val named = (dive.single<Reference>("previous_dive") as? Result.Usable)?.value as? Reference.Identified
        ?: return null
    if (named.id == following.dive) return null
    return "After should be a run of ${named.id}, which this dive already follows, or the plan saved as a new dive"
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

/**
 * The key a plan named [name] goes under on [dive] when it is attached there: the name typed where
 * the dive holds no plan of that name, and the next free letter where it does, since attaching adds
 * a plan and never saves over one.
 */
internal fun attachedKeyOf(dive: Item, name: String): String {
    val typed = planKeyOf(name)
    val taken = keyedEntriesOf(dive, PROFILES).map { it.first }.toSet()
    return if (typed.isNotEmpty() && typed !in taken) typed else planKeyOf(planNameOn(dive))
}

/**
 * Every plan saved in [universe], each with what it is called in a list of them: the dive's id and
 * the plan's name, as `2026-03-01#1: Plan A`. Newest dive first.
 */
internal fun plansIn(universe: Universe): List<Pair<Bound.Editing, String>> =
    universe.logbook.allOf(Types.DIVE).flatMap { dive ->
        val id = universe.logbook.idOf(dive) ?: return@flatMap emptyList()
        keyedEntriesOf(dive, PROFILES).filter { (_, profile) -> isPlanned(profile) }
            .map { (key, _) -> Bound.Editing(id, key) to "$id: ${prettyOf(key)}" }
    }.sortedByDescending { it.second }

/** The changes that make a new dive holding only the plan [fields], under [key]. */
internal fun newDiveOf(key: String, fields: Map<String, Stored>, dive: Map<String, Stored> = emptyMap()): List<Change> =
    listOf(
        Change.Add(
            Types.DIVE,
            mapOf(
                "primary_profile" to Stored.Leaf("*$key"),
                PROFILES to Stored.Members(mapOf(key to Stored.Members(fields))),
            ) + dive,
        ),
    )

/**
 * The changes that put the plan [fields] on [dive] under [key]: a new profile where there is none
 * under it, or over the one there, keeping whatever else was written on it.
 *
 * A dive with no profile before takes the plan as its primary one, as a new dive does; one with a
 * recording keeps the recording.
 */
internal fun onDiveOf(
    dive: Item,
    key: String,
    fields: Map<String, Stored>,
    diveFields: Map<String, Stored> = emptyMap(),
): List<Change> {
    val written = LinkedHashMap<String, Stored>()
    for ((held, entry) in keyedEntriesOf(dive, PROFILES)) {
        written[held] = ItemWriter.write(entry, Units.DEFAULT)
    }
    val kept = (written[key] as? Stored.Members)?.members.orEmpty()
    written[key] = Stored.Members(kept + fields)
    val changes = mutableListOf<Change>(Change.Write(dive, PROFILES, Stored.Members(written)))
    if (written.size == 1) changes += Change.Write(dive, "primary_profile", Stored.Leaf("*$key"))
    // A dive already following one keeps it; followingClashOf refuses a plan that says otherwise.
    val follows = dive.single<Reference>("previous_dive") !is Result.Absent
    if (!follows) for ((field, value) in diveFields) changes += Change.Write(dive, field, value)
    return changes
}

// --- A saved plan taken back out.

/**
 * Fills [shaping] with the plan [profile] holds: its lines, its cylinders, and the settings it was
 * made under. A setting the plan does not hold is left as it is.
 *
 * **The lines are the ones typed** where the plan keeps them and they still lead to its points,
 * which [universe] is needed to tell for a plan following another. `DATA-129`.
 *
 * **Otherwise each pair of points is a line**, a rise, a descent or a stay, timed by its duration
 * so the points come back to the second. That is a plan saved before the lines were kept, or one
 * whose points were changed by hand. The minutes a stop is held for, which the model writes a point
 * at a time, are one line. A line is given its gas where a switch falls at its start, and follows
 * the line above everywhere else.
 */
internal fun Shaping.loadFrom(profile: Item, dive: Item?, universe: Universe? = null) {
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
    val guessed = lines.map { (metres, seconds, gas) ->
        Segment(depth = plain(metres), duration = clockOf(seconds), gas = gas)
    }.ifEmpty { listOf(Segment()) }

    (profile.single<Double>("gradient_factor_low") as? Result.Usable)?.value?.let { gradientLow = plain(it * 100) }
    (profile.single<Double>("gradient_factor_high") as? Result.Usable)?.value?.let { gradientHigh = plain(it * 100) }
    (profile.single<String>("water_type") as? Result.Usable)?.value
        ?.takeIf { it in Settings.DEFAULT_WATER_TYPE.choices }?.let { water = it }

    // The plan's own start where it has one, and its dive's otherwise.
    val date = (profile.single<Date>("start_date") as? Result.Usable)?.value
        ?: (dive?.single<Date>("start_date") as? Result.Usable)?.value
    val time = (profile.single<Time>("start_time") as? Result.Usable)?.value
        ?: (dive?.single<Time>("start_time") as? Result.Usable)?.value
    startDate = date?.toString().orEmpty()
    startTime =
        time?.let { "${it.hour.toString().padStart(2, '0')}:${it.minute.toString().padStart(2, '0')}" }.orEmpty()
    following = (profile.single<KeyReference>("previous_profile") as? Result.Usable)?.value
        ?.let { reference -> reference.id?.let { Following(it, reference.key) } }

    loadSettingsFrom(profile, index)
    val typed = typedLinesOf(profile, index)
    segments.clear()
    segments += typed?.takeIf { leadsTo(described().copy(segments = it), universe, points, switches) } ?: guessed
}

/** Fills [shaping] with the settings [profile] was planned under, where it holds them. `DATA-129`. */
private fun Shaping.loadSettingsFrom(profile: Item, index: Map<String, Int>) {
    fun read(field: String, setting: NumberSetting, into: (String) -> Unit) {
        (profile.single<Double>(field) as? Result.Usable)?.value?.let { into(shownOf(setting, it)) }
    }
    read("po2_max_bottom", Settings.DEFAULT_PO2_MAX_BOTTOM) { bottomOxygen = it }
    read("po2_max_deco", Settings.DEFAULT_PO2_MAX_DECO) { decoOxygen = it }
    read("po2_min", Settings.DEFAULT_PO2_MIN) { leastOxygen = it }
    read("descent_rate", Settings.DEFAULT_DESCENT_RATE) { descentRate = it }
    read("ascent_rate", Settings.DEFAULT_ASCENT_RATE) { ascentRate = it }
    read("last_stop", Settings.DEFAULT_LAST_STOP) { lastStop = it }
    (profile.single<Boolean>("gas_switch_stops") as? Result.Usable)?.value?.let { switchStops = it }
    read("safety_stop_depth", Settings.DEFAULT_SAFETY_STOP_DEPTH) { safetyDepth = it }
    read("safety_stop_duration", Settings.DEFAULT_SAFETY_STOP_DURATION) { safetyMinutes = it }
    read("stress_factor", Settings.DEFAULT_STRESS_FACTOR) { stressFactor = it }
    read("problem_solving_time", Settings.DEFAULT_PROBLEM_SOLVING_TIME) { problemMinutes = it }
    (profile.single<Boolean>("lost_gas_reserve") as? Result.Usable)?.value?.let { lostGasScenario = it }
    (profile.single<KeyReference>("lost_gas") as? Result.Usable)?.value?.let { lostGas = index[it.key] }
    (profile.single<Boolean>("shared_gas_reserve") as? Result.Usable)?.value?.let { sharedScenario = it }
}

/** The lines [profile] keeps as they were typed, in their order, or null where it keeps none. */
private fun typedLinesOf(profile: Item, index: Map<String, Int>): List<Segment>? {
    val kept = keyedEntriesOf(profile, "runtime").ifEmpty { return null }
    return kept.sortedBy { (key, _) -> key.toIntOrNull() ?: Int.MAX_VALUE }.map { (_, line) ->
        Segment(
            depth = numberSaid(line, "depth"),
            duration = (line.single<Double>("duration") as? Result.Usable)?.value
                ?.let { clockOf(it.roundToInt()) }.orEmpty(),
            rate = numberSaid(line, "rate"),
            gas = (line.single<KeyReference>("gas_source") as? Result.Usable)?.value?.let { index[it.key] },
        )
    }
}

/**
 * Whether [planned] still makes the plan saved as [points] and [switches], the cylinder each switch
 * names given by its place in the list.
 */
private fun leadsTo(
    planned: Planned,
    universe: Universe?,
    points: List<Pair<Int, Double>>,
    switches: List<Pair<Int, Int>>,
): Boolean {
    val ready = shapedOf(planned, universe) as? Shaped.Ready ?: return false
    val whole = (workedOf(ready) as? Worked.Done)?.whole ?: return false
    if (whole.depth.size != points.size) return false
    val same = whole.depth.zip(points).all { (made, saved) ->
        made.first == saved.first && abs(made.second - saved.second) < SAVED_DEPTH
    }
    return same && whole.switches.map { (second, key) -> second to gasIndexOf(key) } == switches
}

/** Empties [shaping] to a plan of one blank line on one cylinder, under the settings' defaults. */
internal fun Shaping.startAfresh(settings: Settings?) {
    segments.clear()
    segments += Segment()
    gases.clear()
    gases += Breathed()
    // A cylinder chosen as lost names a place in the list just emptied, so the choice goes with it.
    lostGas = null
    startDate = ""
    startTime = ""
    following = null
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
            val profile =
                dive?.let { keyedEntriesOf(it, "profiles").firstOrNull { (key, _) -> key == bound.key }?.second }
            if (profile != null) shaping.loadFrom(profile, dive, universe)
            name = prettyOf(bound.key)
        }
    }
    kept = shaping.described()
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
    val planned = shaping.described()
    val ready = shapedOf(planned, universe) as? Shaped.Ready
    val done = ready?.let { workedOf(it) as? Worked.Done }
    val unsaved = when {
        universe == null -> "Open a logbook to save a plan into it."
        done == null -> "Fix the plan's errors before saving"
        else -> null
    }

    // Whether it was saved, which a new plan asked for after saving waits on.
    fun save(target: Bound?): Boolean {
        val fields = planFieldsOf(planned, ready!!.conditions, done!!.whole)
        val key = if (target is Bound.Editing) target.key else planKeyOf(saving.name)
        val outcome = when (target) {
            null -> changer.change(newDiveOf(key, fields, diveFieldsOf(planned, universe)))
            else -> changer.change(
                onDiveOf(
                    universe!!.logbook[target.dive]!!,
                    key,
                    fields,
                    diveFieldsOf(planned, universe)
                )
            )
        }
        saving.said = when (outcome) {
            is Outcome.Refused -> outcome.reason
            is Outcome.Done -> {
                val id = if (target == null) outcome.added.firstOrNull() else target.dive
                id?.let { saving.bound = Bound.Editing(it, key) }
                saving.kept = planned
                if (target == null) "Saved as ${prettyOf(key)} on a new dive." else "Saved as ${prettyOf(key)}."
            }
        }
        return outcome is Outcome.Done
    }

    val nameWrong = when {
        planKeyOf(saving.name).isEmpty() -> "Name is missing"
        bound is Bound.Adding && dive != null &&
                keyedEntriesOf(dive, PROFILES).any { (key, _) -> key == planKeyOf(saving.name) } ->
            "Name should be new on this dive: ${saving.name.trim()} exists"

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
                Compact(
                    value = saving.name,
                    onChange = { saving.name = it; saving.said = null },
                    dense = true,
                    wrong = nameWrong != null
                )
            }
        }
        val target = when (bound) {
            null -> null
            else -> dive?.let { titleOf(it) }
        }
        val clash = dive?.let { followingClashOf(planned, it) }
        val blocked = unsaved ?: nameWrong ?: clash
        if (target != null) {
            Explained(blocked) {
                SmallButton(if (bound is Bound.Editing) "Save" else "Add to $target", blocked == null) {
                    save(bound)
                }
            }
        }
        // A new dive follows whatever the plan says, so a clash with the bound dive does not stop it.
        val blockedNew = unsaved ?: nameWrong
        Explained(blockedNew) {
            SmallButton("Save as new dive", blockedNew == null, quiet = target != null) { save(null) }
        }
        Explained(unsaved) {
            Attach(universe, enabled = unsaved == null) { chosen ->
                val id = universe!!.logbook.idOf(chosen) ?: return@Attach
                val key = attachedKeyOf(chosen, saving.name)
                val fields = planFieldsOf(planned, ready!!.conditions, done!!.whole)
                followingClashOf(planned, chosen)?.let { saving.said = it; return@Attach }
                saving.said = when (val outcome =
                    changer.change(onDiveOf(chosen, key, fields, diveFieldsOf(planned, universe)))) {
                    is Outcome.Refused -> outcome.reason
                    is Outcome.Done -> {
                        saving.bound = Bound.Editing(id, key)
                        saving.kept = planned
                        "Saved as ${prettyOf(key)} on ${titleOf(chosen)}."
                    }
                }
            }
        }
        OpenPlan(universe) { chosen -> saving.open(chosen, universe, shaping) }
        // Saved where Save would put it, and as a new dive where it has nowhere to go or cannot go there.
        val toBound = target != null && blocked == null
        NewPlan(
            changed = { saving.isChanged(shaping, universe?.settings) },
            name = saving.name.trim().ifEmpty { "the plan" },
            savedAs = if (toBound) "Save" else "Save as new dive",
            unsavable = if (toBound) null else blockedNew,
            onSave = { save(if (toBound) bound else null) },
            onNew = { saving.startNew(shaping, universe?.settings) },
        )
        if (bound != null) {
            TextButton(
                onClick = { saving.bound = null; saving.said = null },
                modifier = Modifier.height(SMALL),
                contentPadding = PaddingValues(horizontal = GAP / 2),
            ) { Text("Forget the dive", style = MaterialTheme.typography.labelMedium) }
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

/** A button of the save row, smaller than a form's, being one of several in a line. */
@Composable
private fun SmallButton(label: String, enabled: Boolean, quiet: Boolean = false, onClick: () -> Unit) {
    val padding = PaddingValues(horizontal = GAP)
    val text = @Composable { Text(label, style = MaterialTheme.typography.labelMedium) }
    if (quiet) {
        OutlinedButton(onClick, Modifier.height(SMALL), enabled = enabled, contentPadding = padding) { text() }
    } else {
        Button(onClick, Modifier.height(SMALL), enabled = enabled, contentPadding = padding) { text() }
    }
}

/**
 * The deed that empties the planner for a new plan, asking first where the plan has changes not
 * saved: save them, leave them, or stay. A save that fails stays, its reason said in the row.
 * `GUI-54`.
 */
@Composable
private fun NewPlan(
    changed: () -> Boolean,
    name: String,
    savedAs: String,
    unsavable: String?,
    onSave: () -> Boolean,
    onNew: () -> Unit,
) {
    var asking by remember { mutableStateOf(false) }
    SmallButton("New plan", enabled = true, quiet = true) { if (changed()) asking = true else onNew() }
    if (!asking) return
    AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("Save $name first?") },
        text = {
            Text(
                "It has changes that are not saved, and a new plan starts empty." +
                    // The reason is a sentence of the row's own, which may or may not end in a stop.
                    (unsavable?.let { "\n\n" + it.removeSuffix(".") + "." } ?: ""),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    asking = false
                    if (onSave()) onNew()
                },
                enabled = unsavable == null,
            ) { Text(savedAs) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { asking = false }) { Text("Cancel") }
                TextButton(onClick = { asking = false; onNew() }) {
                    Text("Don't save", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

/** The deed that opens a plan saved before: a menu of them, the one chosen loaded to be changed. */
@Composable
private fun OpenPlan(universe: Universe?, onChoose: (Bound.Editing) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box {
        SmallButton("Open plan", universe != null, quiet = true) { choosing = true }
        Menu(expanded = choosing, onDismissRequest = { choosing = false }) {
            val plans = universe?.let { plansIn(it) }.orEmpty()
            if (plans.isEmpty()) {
                DropdownMenuItem(text = { Text("No saved plans") }, onClick = { choosing = false }, enabled = false)
            }
            for ((bound, said) in plans) {
                DropdownMenuItem(
                    text = { Text(said) },
                    onClick = {
                        choosing = false
                        onChoose(bound)
                    },
                )
            }
        }
    }
}

/**
 * The deed that puts the plan on to a dive already in the logbook: a menu of them, newest first,
 * the plan going on to the one chosen. `GUI-44`.
 */
@Composable
private fun Attach(universe: Universe?, enabled: Boolean, onChoose: (ReferenceableItem) -> Unit) {
    var choosing by remember { mutableStateOf(false) }
    Box {
        SmallButton("Attach to existing dive", enabled, quiet = true) { choosing = true }
        Menu(expanded = choosing, onDismissRequest = { choosing = false }) {
            val dives = universe?.logbook?.allOf(Types.DIVE).orEmpty().sortedByDescending { titleOf(it) }
            if (dives.isEmpty()) {
                DropdownMenuItem(text = { Text("No dives yet") }, onClick = { choosing = false }, enabled = false)
            }
            for (dive in dives) {
                DropdownMenuItem(
                    text = { Text(titleOf(dive)) },
                    onClick = {
                        choosing = false
                        onChoose(dive)
                    },
                )
            }
        }
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

/** How far a depth made again may lie from the one saved, which was rounded to a millimetre. */
private const val SAVED_DEPTH = 0.01

/** How wide the box a plan's name is typed in is. */
private val NAME = 120.dp

/** How tall the save row's buttons are. */
private val SMALL = 30.dp
