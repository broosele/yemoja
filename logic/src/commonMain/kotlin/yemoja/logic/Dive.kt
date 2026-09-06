package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Direction
import yemoja.data.Element
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.KeyReferenceDescription
import yemoja.data.Moment
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Series
import yemoja.data.TextDescription
import yemoja.data.Time
import yemoja.data.TimeDescription
import yemoja.data.WholeNumberDescription

/*
 * What a dive is, what it holds, and what is worked out from the recording it was made
 * on.
 *
 * The items a dive owns are described here too, and each is declared before the dive
 * itself: a property is unusable above its own declaration, so the file builds from
 * the inside out and the dive is the last of the descriptions rather than the first.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Details is the labels on a dive, and which trip and operator it belonged to. */
private val DETAILS = ItemDescription(
    "details",
    listOf(
        ReferenceDescription("dive_trip", targetType = "dive_trip"),
        ReferenceDescription("operator", targetType = "operator"),
        TextDescription("tags", cardinality = Cardinality.LIST),
        REMARKS,
    ),
)

/** Closed: six steps, for a current and for the state of the surface alike. */
private val STRENGTHS = setOf("none", "very mild", "mild", "moderate", "hard", "very hard")

/** Environment is the conditions a dive was found in. */
private val ENVIRONMENT = ItemDescription(
    "environment",
    listOf(
        TextDescription("current", fixedSet = STRENGTHS),
        TextDescription("waves", fixedSet = STRENGTHS),
        // One figure rather than a range, which is what a diver remembers.
        NumberDescription("visibility", Dimension.LENGTH),
        NumberDescription("air_temperature", Dimension.TEMPERATURE),
        // The water at the surface, which is not the air above it. A computer reporting a
        // surface temperature is nearly always reporting water.
        NumberDescription("surface_temperature", Dimension.TEMPERATURE),
        NumberDescription("bottom_temperature", Dimension.TEMPERATURE),
        // From where the dive was, and absolute: about a bar at sea level.
        NumberDescription("atmospheric_pressure", Dimension.PRESSURE),
        REMARKS,
    ),
)

/** Anything is allowed; these are the ones the manual names. */
private val WARMTHS = setOf("very cold", "cold", "good", "warm", "too warm")

/** Anything is allowed; these are the ones the manual names. */
private val WEIGHTINGS = setOf("way too heavy", "too heavy", "good", "too light", "way too light")

/**
 * DiveGear is what was taken on a dive, and how it worked out.
 *
 * Absent so far: nothing of its own.
 */
private val DIVE_GEAR = ItemDescription(
    "dive_gear",
    listOf(
        ReferenceDescription("items", targetType = "gear", cardinality = Cardinality.LIST),
        NumberDescription("mass", Dimension.MASS),
        // The lead among the items. Corrected where they do not tell the whole story.
        NumberDescription("weight", Dimension.MASS, role = Role.Overrideable(::leadCarried)),
        TextDescription("temperature_evaluation", suggestedSet = WARMTHS),
        TextDescription("buoyancy_evaluation", suggestedSet = WEIGHTINGS),
        REMARKS,
    ),
)

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
private fun leadCarried(gear: Item): Result<Any> {
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

/**
 * Tolerances is how much detail a recording dropped, one figure per thing recorded.
 *
 * The most any kept point may differ from what was measured.
 */
private val TOLERANCES = ItemDescription(
    "tolerances",
    listOf(
        NumberDescription("depth", Dimension.LENGTH),
        NumberDescription("temperature", Dimension.TEMPERATURE),
        NumberDescription("pressure", Dimension.PRESSURE),
        REMARKS,
    ),
)

/** Closed: what a dive computer warns about. */
private val ALARMS = setOf(
    "ascent", "breath", "deco", "error", "link", "microbubbles", "rbt", "skincooling", "surface",
)

/** The models a dive computer may be running, as libdivecomputer names them. */
private val DECO_MODELS = setOf("buhlmann", "vpm", "rgbm", "dciem")

/**
 * Profile is one recording through a dive, under a key on that dive.
 *
 * Absent so far: nothing of its own.
 */
private val PROFILE = ItemDescription(
    "profile",
    listOf(
        // A plain name works for a computer that is nobody's item.
        ReferenceDescription("dive_computer", targetType = "gear", oneOffAllowed = true),
        DateDescription("start_date"),
        TimeDescription("start_time"),
        // A length of time like any other, and scoped like one. `DATA-10`.
        NumberDescription("gmt_offset", Dimension.TIME, label = "GMT offset"),
        // From the last sample, and correctable where the recording stopped before the user
        // surfaced.
        DateDescription("end_date", role = Role.Overrideable(::profilesEndDate)),
        TimeDescription("end_time", role = Role.Overrideable(::profilesEndTime)),
        NumberDescription(
            "duration",
            Dimension.TIME,
            role = Role.Overrideable(::profilesDuration),
        ),
        // What the computer was set to while it recorded, which is not what the site is.
        TextDescription("water_type", fixedSet = WATER_TYPES),
        // What the recorded depths were made with, which is what reading them back needs.
        NumberDescription(
            "density",
            Dimension.DENSITY,
            role = Role.Overrideable(::profilesDensity),
        ),
        // What `decostop` and `no_deco_time` were computed with. Suggested rather than fixed: a
        // maker may run something none of the four names, and nothing exports this to a closed
        // list.
        TextDescription("deco_model", suggestedSet = DECO_MODELS),
        // A proportion, so from 0 to 1: a computer set to 30/70 records 0.3 and 0.7.
        NumberDescription(
            "gradient_factor_low",
            Dimension.DIMENSIONLESS,
            range = 0.0..1.0,
        ),
        NumberDescription(
            "gradient_factor_high",
            Dimension.DIMENSIONLESS,
            range = 0.0..1.0,
        ),
        // A dial position, whose meaning is the device's own. No range, since each make
        // numbers its own scale.
        WholeNumberDescription("conservatism"),
        NumberDescription("no_flight_time", Dimension.TIME),
        NumberDescription("desaturation_time", Dimension.TIME),
        OwnedItemDescription("tolerances", TOLERANCES),
        NumberDescription("depth", Dimension.LENGTH, cardinality = Cardinality.SERIES),
        NumberDescription(
            "temperature",
            Dimension.TEMPERATURE,
            cardinality = Cardinality.SERIES,
        ),
        // A rounded depth rather than a continuous ceiling: three metres, six, nine.
        NumberDescription("decostop", Dimension.LENGTH, cardinality = Cardinality.SERIES),
        NumberDescription("no_deco_time", Dimension.TIME, cardinality = Cardinality.SERIES),
        // Recognised as a percentage, which is how everyone quotes it.
        NumberDescription(
            "cns",
            Dimension.DIMENSIONLESS,
            label = "CNS",
            cardinality = Cardinality.SERIES,
        ),
        NumberDescription(
            "otu",
            Dimension.DIMENSIONLESS,
            label = "OTU",
            cardinality = Cardinality.SERIES,
        ),
        TextDescription("alarms", fixedSet = ALARMS, cardinality = Cardinality.SERIES),
        // Each naming the gas source moved to.
        KeyReferenceDescription(
            "gas_switches",
            collection = "gas_sources",
            cardinality = Cardinality.SERIES,
        ),
        // Gauge pressure left in each cylinder: one series per gas source.
        NumberDescription(
            "pressures",
            Dimension.PRESSURE,
            cardinality = Cardinality.KEYED_SERIES,
        ),
        REMARKS,
    ),
    proposedId = ::profilesProposedKey,
)

/** When the last sample was taken, as a moment in GMT, or absent where there is none. */
private fun ended(profile: Item): Moment? {
    val start = began(profile) ?: return null
    val ran = ranFor(profile) ?: return null
    return start.plusSeconds(ran.toLong())
}

private fun profilesEndDate(profile: Item): Result<Any> =
    ended(profile)?.let { dateOf(it) } ?: Result.Absent

private fun profilesEndTime(profile: Item): Result<Any> =
    ended(profile)?.let { timeOf(it) } ?: Result.Absent

/**
 * How long a recording ran, in seconds.
 *
 * The last sample's own time, which is already seconds from the start. `DATA-58`.
 */
private fun profilesDuration(profile: Item): Result<Any> =
    ranFor(profile)?.let { Result.Usable(it.toDouble(), Result.Origin.DERIVED) } ?: Result.Absent

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
private fun profilesDensity(profile: Item): Result<Any> {
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

/** Anything is allowed; these are the ones the manual names. */
private val GAS_USAGES = setOf("bottom", "stage", "deco", "travel")

/** Anything is allowed; these are the ones the manual names. */
private val GAS_CONFIGURATIONS = setOf("back mounted", "sidemount", "pony", "staged")

/**
 * GasSource is one thing breathed from on a dive, under a key on that dive.
 *
 * Absent so far: nothing of its own.
 */
private val GAS_SOURCE = ItemDescription(
    "gas_source",
    listOf(
        GasDescription("gas_type"),
        NumberDescription("start_pressure", Dimension.PRESSURE),
        NumberDescription("end_pressure", Dimension.PRESSURE),
        TextDescription("usage", suggestedSet = GAS_USAGES),
        TextDescription("configuration", suggestedSet = GAS_CONFIGURATIONS),
        // Left out where what was breathed from is nobody's item.
        ReferenceDescription("cylinder", targetType = "gear"),
        // From the cylinder's own `capacity`, and written by hand where no cylinder is named
        // or the one dived was not the one recorded.
        NumberDescription(
            "volume",
            Dimension.VOLUME,
            role = Role.Overrideable(::cylindersVolume),
        ),
        REMARKS,
    ),
    proposedId = ::gasSourcesProposedKey,
)

/**
 * What a cylinder holds, from the `capacity` of the gear item it names.
 *
 * **Unusable rather than absent where the reference leads anywhere but a cylinder.** Pointing at
 * a regulator, or at a cylinder whose capacity was never filled in, is a mistake worth seeing,
 * and a blank field looks like one nobody wrote. Absent only where no cylinder is named, which
 * is the ordinary case for a rented one.
 */
private fun cylindersVolume(source: Item): Result<Any> {
    val named = source.single<Reference>("cylinder") as? Result.Usable ?: return Result.Absent
    val id = (named.value as? Reference.Identified)?.id
        ?: return unusable("a volume needs a cylinder with an id to take it from")
    val gear = source.set[id]
        ?: return unusable("$id is not in this logbook, so its capacity cannot be read")
    if (gear.description != Types.GEAR) {
        return unusable("$id is a ${gear.description.name} rather than a piece of gear")
    }
    val category = (gear.single<String>("category") as? Result.Usable)?.value
    if (category != CYLINDER) return unusable("$id is not in the $CYLINDER category")
    val capacity = gear.single<Double>("capacity") as? Result.Usable
        ?: return unusable("$id has no capacity written on it")
    return Result.Usable(capacity.value, Result.Origin.DERIVED)
}

/** The gear category a cylinder is in, without which its capacity means nothing. */
private const val CYLINDER = "cylinder"

/**
 * How a diver crosses the waterline, at either end of a dive.
 *
 * Suggested rather than fixed. A closed list would have to be right the first time and this
 * one is not closeable — ice, a marina ladder, a helicopter — and nothing exports it to
 * another format's closed list, which is what `water_type` and `environment_type` are held to.
 */
private val ENTRIES_AND_EXITS = setOf(
    "shore",
    "pier",
    "boat",
    "hard boat",
    "rib",
    "liveaboard",
    "platform",
    "pool",
)

/**
 * Dive is one dive, and the largest thing here.
 *
 * Absent so far: nothing of its own.
 */
internal val DIVE: ItemDescription = ItemDescription(
    "dive",
    listOf(
        // The id, which is what a dive is listed and linked as. Not correctable: writing
        // one would be renaming the dive, which the Universe does with its references.
        TextDescription("name", role = Role.Derived(::divesId)),
        // The user's own numbering, which nothing renumbers. Not every diver keeps one.
        WholeNumberDescription("dive_number"),
        // All five from the primary profile, in GMT, and all five correctable: the computer
        // was there and the user was busy, but a recording can still be wrong.
        DateDescription("start_date", role = Role.Overrideable(::divesStartDate)),
        TimeDescription("start_time", role = Role.Overrideable(::divesStartTime)),
        DateDescription("end_date", role = Role.Overrideable(::divesEndDate)),
        TimeDescription("end_time", role = Role.Overrideable(::divesEndTime)),
        NumberDescription(
            "duration",
            Dimension.TIME,
            role = Role.Overrideable(::divesDuration),
        ),
        // Worth correcting: a computer usually reports a better figure than its own
        // recorded profile, which is sampled only every few seconds.
        NumberDescription(
            "max_depth",
            Dimension.LENGTH,
            role = Role.Overrideable(::divesMaxDepth),
        ),
        NumberDescription(
            "average_depth",
            Dimension.LENGTH,
            role = Role.Overrideable(::divesAverageDepth),
        ),
        BooleanDescription("deco", role = Role.Overrideable(::divesDeco)),
        ReferenceDescription("dive_site", targetType = "dive_site"),
        // How the water was got into and out of. Two fields because they differ: a drift
        // dive goes in off a boat and comes out on a beach. Neither is assumed from the
        // other, which would hide exactly that case.
        TextDescription("entry", suggestedSet = ENTRIES_AND_EXITS),
        TextDescription("exit", suggestedSet = ENTRIES_AND_EXITS),
        ReferenceDescription(
            "buddies",
            targetType = "person",
            cardinality = Cardinality.LIST,
            oneOffAllowed = true,
        ),
        // Corrected where `buddies` names fewer than were there: a diver remembers how many
        // came without remembering all of them.
        WholeNumberDescription("buddy_count", role = Role.Overrideable(::buddyCount)),
        WholeNumberDescription("rating", range = 1..10),
        // The dive still being carried gas from when this one began.
        ReferenceDescription("previous_dive", targetType = "dive"),
        // From `previous_dive`'s end to this dive's start. Written by hand for a dive whose
        // predecessor is not in this logbook, which an import often knows without knowing
        // the dive.
        NumberDescription(
            "surface_interval",
            Dimension.TIME,
            role = Role.Overrideable(::surfaceInterval),
        ),
        // Which of `profiles` to work from. Leaving it out where there is one is the
        // ordinary case.
        KeyReferenceDescription("primary_profile", collection = "profiles"),
        OwnedItemDescription("details", DETAILS),
        OwnedItemDescription("environment", ENVIRONMENT),
        OwnedItemDescription("gear", DIVE_GEAR),
        OwnedItemDescription("profiles", PROFILE, cardinality = Cardinality.KEYED),
        OwnedItemDescription("gas_sources", GAS_SOURCE, cardinality = Cardinality.KEYED),
        REMARKS,
    ),
    orderedBy = listOf(
        Ordering("start_date", Direction.DESCENDING),
        Ordering("start_time", Direction.DESCENDING),
    ),
    proposedId = ::divesProposedId,
)

/**
 * A dive's name, which is its id.
 *
 * An item does not carry its id, so this asks the set it belongs to. Absent for a dive inside
 * no set, which nothing that reads a logbook produces.
 */
private fun divesId(dive: Item): Result<Any> {
    val id = (dive as? ReferenceableItem)?.let { dive.set.idOf(it) } ?: return Result.Absent
    return Result.Usable(id, Result.Origin.DERIVED)
}

private fun divesStartDate(dive: Item): Result<Any> =
    fromProfile(dive) { began(it)?.let { moment -> dateOf(moment) } ?: Result.Absent }

private fun divesStartTime(dive: Item): Result<Any> =
    fromProfile(dive) { began(it)?.let { moment -> timeOf(moment) } ?: Result.Absent }

private fun divesEndDate(dive: Item): Result<Any> =
    fromProfile(dive, ::profilesEndDate).orElse { finished(dive)?.let(::dateOf) ?: Result.Absent }

private fun divesEndTime(dive: Item): Result<Any> = fromProfile(dive, ::profilesEndTime)

private fun divesDuration(dive: Item): Result<Any> =
    fromProfile(dive, ::profilesDuration).orElse { lasted(dive) }

/**
 * [this] unless there was nothing to work it out from, in which case what [instead] makes of it.
 *
 * A recording that cannot be chosen still travels. `primaryProfile` reports a dive holding
 * several profiles and naming none, and falling back there would answer a question the dive has
 * asked twice and settled neither time.
 */
private fun Result<Any>.orElse(instead: () -> Result<Any>): Result<Any> =
    if (this == Result.Absent) instead() else this

/**
 * When a dive ended, from what it says about itself rather than from a recording.
 *
 * The day it started, or the day after where the end time is earlier than the start time. No
 * dive runs for twenty-four hours, so an end before a start is the following morning and nothing
 * else. The manual states this to the user under `end_date`.
 *
 * **A dive with no start time is left alone.** There is then nothing for the end time to be
 * earlier than, so whether midnight was crossed is unknown rather than unlikely.
 */
private fun finished(dive: Item): Moment? {
    val began = begun(dive) ?: return null
    val end = (dive.single<Time>("end_time") as? Result.Usable)?.value ?: return null
    val day = if (end < began.time) Date.ofEpochDay(began.date.epochDay + 1) else began.date
    return Moment(day, end)
}

/** How long a dive ran, from its own times, or absent where they do not place both ends. */
private fun lasted(dive: Item): Result<Any> {
    val began = begun(dive) ?: return Result.Absent
    val ended = finished(dive) ?: return Result.Absent
    return Result.Usable(began.secondsUntil(ended).toDouble(), Result.Origin.DERIVED)
}

/** When a dive says it began, both halves being needed before either is any use. */
private fun begun(dive: Item): Moment? {
    val date = (dive.single<Date>("start_date") as? Result.Usable)?.value ?: return null
    val time = (dive.single<Time>("start_time") as? Result.Usable)?.value ?: return null
    return Moment(date, time)
}

/**
 * The deepest point a recording reached.
 *
 * The largest depth of the `depth` series. A sample that could not be read is passed over: one
 * bad number does not hide how deep the rest of the dive went.
 */
private fun divesMaxDepth(dive: Item): Result<Any> = fromProfile(dive) { profile ->
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
private fun divesAverageDepth(dive: Item): Result<Any> = fromProfile(dive) { profile ->
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
private fun divesDeco(dive: Item): Result<Any> = fromProfile(dive) { profile ->
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
 * How many people were on a dive, counted from the list of them.
 *
 * Every entry counts: a plain name as much as a reference, and one that would not read as much
 * as either. The question is how many were there, and an entry nobody can resolve is still
 * somebody. Absent where the list was not written, which is not a dive with nobody on it.
 */
private fun buddyCount(dive: Item): Result<Any> {
    val buddies = dive.list<Reference>("buddies") as? Result.Usable ?: return Result.Absent
    return Result.Usable(buddies.value.size, Result.Origin.DERIVED)
}

/**
 * How long the user was out of the water before a dive, in seconds.
 *
 * From `previous_dive`'s end to this dive's start, both in GMT, which is what makes it right
 * when two dives sit in different countries. **Absent where no previous dive is named**: whether
 * a surface interval was long enough to ignore is a judgement, and any threshold deciding it
 * would be wrong for somebody.
 */
private fun surfaceInterval(dive: Item): Result<Any> {
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
