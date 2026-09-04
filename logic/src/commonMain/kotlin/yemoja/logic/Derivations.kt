package yemoja.logic

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.Moment
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Time

/*
 * What every worked-out field answers with.
 *
 * The walks these share live in Recordings.kt; each function here says what it takes from one.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

// --- A recording's own times ----------------------------------------------------------------

/** When the last sample was taken, as a moment in GMT, or absent where there is none. */
private fun ended(profile: Item): Moment? {
    val start = began(profile) ?: return null
    val ran = ranFor(profile) ?: return null
    return start.plusSeconds(ran.toLong())
}

internal fun profilesEndDate(profile: Item): Result<Any> =
    ended(profile)?.let { dateOf(it) } ?: Result.Absent

internal fun profilesEndTime(profile: Item): Result<Any> =
    ended(profile)?.let { timeOf(it) } ?: Result.Absent

/**
 * How long a recording ran, in seconds.
 *
 * The last sample's own time, which is already seconds from the start. `DATA-58`.
 */
internal fun profilesDuration(profile: Item): Result<Any> =
    ranFor(profile)?.let { Result.Usable(it.toDouble(), Result.Origin.DERIVED) } ?: Result.Absent

// --- A dive's times, taken from the recording -----------------------------------------------

internal fun divesStartDate(dive: Item): Result<Any> =
    fromProfile(dive) { began(it)?.let { moment -> dateOf(moment) } ?: Result.Absent }

internal fun divesStartTime(dive: Item): Result<Any> =
    fromProfile(dive) { began(it)?.let { moment -> timeOf(moment) } ?: Result.Absent }

internal fun divesEndDate(dive: Item): Result<Any> = fromProfile(dive, ::profilesEndDate)

internal fun divesEndTime(dive: Item): Result<Any> = fromProfile(dive, ::profilesEndTime)

internal fun divesDuration(dive: Item): Result<Any> = fromProfile(dive, ::profilesDuration)

/**
 * The deepest point a recording reached.
 *
 * The largest depth of the `depth` series. A sample that could not be read is passed over: one
 * bad number does not hide how deep the rest of the dive went.
 */
internal fun divesMaxDepth(dive: Item): Result<Any> = fromProfile(dive) { profile ->
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series
    val deepest = depth?.usable()?.filterIsInstance<Double>()?.maxOrNull()
    if (deepest == null) Result.Absent else Result.Usable(deepest, Result.Origin.DERIVED)
}

/**
 * How deep a dive was on average, weighted by time.
 *
 * **Not the mean of the samples.** A computer records unevenly — often densely on the way
 * down and sparsely at a safety stop — so counting samples would weight a crowded minute the
 * same as an empty ten. Each interval between two samples contributes the depth it spent
 * there, which is the area under the profile divided by how long it ran.
 *
 * A profile is read as piecewise linear, so an interval's depth is the mean of its two ends.
 * A sample that could not be read breaks the pair it belongs to and both its intervals are
 * passed over; the rest of the dive is unaffected.
 */
internal fun divesAverageDepth(dive: Item): Result<Any> = fromProfile(dive) { profile ->
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series
        ?: return@fromProfile Result.Absent
    var area = 0.0
    var ran = 0
    for (at in 1..<depth.size) {
        val before = (depth.valueAt(at - 1) as? Element.Usable)?.value as? Double
        val after = (depth.valueAt(at) as? Element.Usable)?.value as? Double
        if (before == null || after == null) continue
        val seconds = depth.secondAt(at) - depth.secondAt(at - 1)
        area += (before + after) / 2 * seconds
        ran += seconds
    }
    if (ran == 0) Result.Absent else Result.Usable(area / ran, Result.Origin.DERIVED)
}

/**
 * Whether a dive went past the no-decompression limit.
 *
 * A `decostop` above zero at any point means yes. Failing that, a `no_deco_time` that never
 * reached zero means no. Most computers write stops only where there are stops, which is why
 * the second reading matters.
 *
 * **Absent where the recording has neither**, and left for the user to answer. The manual is
 * deliberate that Yemoja does not decide this one: the computer decided it at the time, with
 * settings this application cannot reproduce, and an opinion arrived at years later would be
 * answering a different question.
 */
internal fun divesDeco(dive: Item): Result<Any> = fromProfile(dive) { profile ->
    val stops = (profile.read("decostop") as? Result.Usable)?.value as? Series
    val held = stops?.usable()?.filterIsInstance<Double>().orEmpty()
    if (held.any { it > 0.0 }) return@fromProfile Result.Usable(true, Result.Origin.DERIVED)
    val remaining = (profile.read("no_deco_time") as? Result.Usable)?.value as? Series
    val left = remaining?.usable()?.filterIsInstance<Double>().orEmpty()
    when {
        held.isNotEmpty() -> Result.Usable(false, Result.Origin.DERIVED)
        left.isEmpty() -> Result.Absent
        left.all { it > 0.0 } -> Result.Usable(false, Result.Origin.DERIVED)
        else -> Result.Usable(true, Result.Origin.DERIVED)
    }
}

/**
 * How long the user was out of the water before a dive, in seconds.
 *
 * From `previous_dive`'s end to this dive's start, both in GMT, which is what makes it right
 * when two dives sit in different countries. **Absent where no previous dive is named**: whether
 * a surface interval was long enough to ignore is a judgement, and any threshold deciding it
 * would be wrong for somebody.
 */
internal fun surfaceInterval(dive: Item): Result<Any> {
    val named = dive.single<Reference>("previous_dive") as? Result.Usable ?: return Result.Absent
    val id = (named.value as? Reference.Identified)?.id
        ?: return unusable("a surface interval needs a dive with an id to measure from")
    val before = dive.set[id] ?: return unusable("$id is not in this logbook")
    val out = momentOf(before, "end_date", "end_time") ?: return Result.Absent
    val back = momentOf(dive, "start_date", "start_time") ?: return Result.Absent
    val seconds = out.secondsUntil(back)
    if (seconds < 0) return unusable("$id ended after this dive began")
    return Result.Usable(seconds.toDouble(), Result.Origin.DERIVED)
}

/** A date field and a time field of one item read together, or absent where either is missing. */
private fun momentOf(item: Item, dateField: String, timeField: String): Moment? {
    val date = (item.single<Date>(dateField) as? Result.Usable)?.value ?: return null
    val time = (item.single<Time>(timeField) as? Result.Usable)?.value ?: return null
    return Moment(date, time)
}

/**
 * What a recording's depths were made with, in kilograms per cubic metre.
 *
 * **A computer never measures depth.** It measures the pressure around it and divides by an
 * assumed density, so the figure the maker chose is baked into every depth it wrote, and reading
 * one back to a pressure needs that same figure. `DATA-59`.
 *
 * Fresh and `en13319` are fixed, the second being the nominal figure the European standard for
 * depth gauges lays down. Salt is whatever the computer was set to, taken from the `salt_density`
 * of the gear item it names and falling back to [USUAL_SALT] where there is no computer, no item
 * for it, or no figure on it.
 *
 * Absent where nothing says what the computer was set to, which is every profile imported from
 * UDDF: it records the converted depth and discards the conversion.
 */
internal fun profilesDensity(profile: Item): Result<Any> {
    val water = (profile.single<String>("water_type") as? Result.Usable)?.value
        ?: return Result.Absent
    val fixed = FIXED_DENSITIES[water]
    if (fixed != null) return Result.Usable(fixed, Result.Origin.DERIVED)
    if (water != SALT) return unusable("$water is not a water type this knows a density for")
    return Result.Usable(saltDensity(profile), Result.Origin.DERIVED)
}

/** What the computer this recording came off takes salt water to weigh. */
private fun saltDensity(profile: Item): Double {
    val named = profile.single<Reference>("dive_computer") as? Result.Usable
    val id = ((named?.value) as? Reference.Identified)?.id ?: return USUAL_SALT
    val computer = profile.set[id] ?: return USUAL_SALT
    return (computer.single<Double>("salt_density") as? Result.Usable)?.value ?: USUAL_SALT
}

/**
 * The two water types whose density is settled whatever the computer is.
 *
 * `en13319` is not a measurement of anything: it is the nominal figure the European standard
 * lays down for depth gauges, deliberately below real seawater so a gauge reads slightly deep.
 */
private val FIXED_DENSITIES = mapOf("fresh" to 1000.0, "en13319" to 1020.0)

private const val SALT = "salt"

/** What salt water weighs where nothing says otherwise, which is the usual figure. */
private const val USUAL_SALT = 1030.0

/**
 * How much lead a dive carried, added up from the items taken.
 *
 * **Every item in the `weights` category, and nothing else.** A weight-integrated harness is not
 * one: its own mass is the pockets, and the lead that went in them is a `weights` item of its
 * own, so counting the category counts each block once and no harness twice.
 *
 * An item this logbook does not hold, or one with no mass written on it, adds nothing and is not
 * a fault — `mass` is a property of the gear rather than of the dive, and the manual makes this
 * correctable exactly for the case where the items do not tell the whole story. Absent where no
 * items were listed, which is not a dive done with no lead.
 */
internal fun leadCarried(gear: Item): Result<Any> {
    val items = gear.list<Reference>("items") as? Result.Usable ?: return Result.Absent
    val lead = items.value
        .mapNotNull { (it as? Element.Usable)?.value as? Reference.Identified }
        .mapNotNull { gear.set[it.id] }
        .filter { (it.single<String>("category") as? Result.Usable)?.value == WEIGHTS }
        .mapNotNull { (it.single<OwnedItem>("buoyancy") as? Result.Usable)?.value }
        .mapNotNull { (it.single<Double>("mass") as? Result.Usable)?.value }
    return Result.Usable(lead.sum(), Result.Origin.DERIVED)
}

/** The gear category lead is in, which is what makes an item count towards the weight carried. */
private const val WEIGHTS = "weights"

// --- What has run out, which is all that is asked of a day outside the logbook --------------

/**
 * How many days are left before [until] on [item], counted from the day the logbook was opened.
 *
 * Negative once the day has passed, which is what makes `days_left` and `expired` two readings
 * of one fact rather than two calculations.
 */
private fun daysLeft(item: Item, until: String): Long? {
    val ends = (item.single<Date>(until) as? Result.Usable)?.value ?: return null
    return today().daysUntil(ends)
}

/** [item]'s remaining days, or absent where it owes nothing by any date. */
private fun remaining(item: Item, until: String): Result<Any> {
    val left = daysLeft(item, until) ?: return Result.Absent
    return Result.Usable(left.toInt(), Result.Origin.DERIVED)
}

/** Whether [until] on [item] has passed, which is the same fact read as a yes or a no. */
private fun passed(item: Item, until: String): Result<Any> {
    val left = remaining(item, until)
    if (left !is Result.Usable) return left
    return Result.Usable((left.value as Int) < 0, Result.Origin.DERIVED)
}

internal fun insurancesDaysLeft(cover: Item): Result<Any> = remaining(cover, "end_date")

internal fun insurancesExpired(cover: Item): Result<Any> = passed(cover, "end_date")

internal fun maintenancesDaysLeft(work: Item): Result<Any> = remaining(work, "valid_until")

internal fun maintenancesExpired(work: Item): Result<Any> = passed(work, "valid_until")

// --- A trip's dives, and the dates that follow from them ------------------------------------

/**
 * Every dive made on a trip, and on any trip beneath it.
 *
 * A dive names the leg it was on rather than the fortnight, so a trip gathers from itself and
 * from everything naming it, however deep that goes.
 *
 * **A trip that is its own ancestor is a fault, not a shorter answer.** `LOGIC-8` leaves what a
 * cycle means to each derivation, and here it would count some dives twice or never stop, so
 * the walk reports it rather than quietly surviving.
 */
internal fun tripsDives(trip: Item): Result<Any> {
    val id = (trip as? ReferenceableItem)?.let { trip.set.idOf(it) } ?: return Result.Absent
    val beneath = LinkedHashSet<String>()
    beneath.add(id)
    var edge = listOf(id)
    while (edge.isNotEmpty()) {
        val next = trip.set.allOf(Types.DIVE_TRIP)
            .filter { child -> namesIn(child, "parent").any { it in edge } }
            .mapNotNull { trip.set.idOf(it) }
        // A trip has one parent, so meeting one twice is a chain that has come back on itself
        // rather than two routes to the same place.
        for (each in next) {
            if (!beneath.add(each)) {
                return unusable("$each is inside itself, so its dives cannot be gathered")
            }
        }
        edge = next
    }
    val found = trip.set.allOf(Types.DIVE)
        .filter { dive -> tripOf(dive) in beneath }
        .mapNotNull { trip.set.idOf(it) }
        .map { Element.Usable(Reference.Identified(it) as Any) }
    return Result.Usable(found, Result.Origin.DERIVED)
}

/** Which trip a dive says it was on, which sits on its details rather than on the dive. */
private fun tripOf(dive: Item): String? {
    val details = (dive.single<OwnedItem>("details") as? Result.Usable)?.value ?: return null
    return namesIn(details, "dive_trip").firstOrNull()
}

/** When a trip ran, taken from the dives on it. */
internal fun tripsStartDate(trip: Item): Result<Any> = spanOf(trip) { it.min() }

internal fun tripsEndDate(trip: Item): Result<Any> = spanOf(trip) { it.max() }

/**
 * One end of the range of dates the dives on [trip] cover.
 *
 * A dive with no date of its own says nothing about when the trip ran and is passed over. Absent
 * where none of them has one, which a trip set up before any diving has by definition.
 */
private fun spanOf(trip: Item, pick: (List<Date>) -> Date): Result<Any> {
    val gathered = tripsDives(trip)
    if (gathered !is Result.Usable) return gathered
    @Suppress("UNCHECKED_CAST")
    val dates = (gathered.value as List<Element<Any>>)
        .mapNotNull { (it as? Element.Usable)?.value as? Reference.Identified }
        .mapNotNull { trip.set[it.id] }
        .mapNotNull { (it.single<Date>("start_date") as? Result.Usable)?.value }
    if (dates.isEmpty()) return Result.Absent
    return Result.Usable(pick(dates), Result.Origin.DERIVED)
}
