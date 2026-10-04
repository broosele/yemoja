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
import yemoja.data.KeyReference
import yemoja.data.KeyReferenceDescription
import yemoja.data.Layout
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
import yemoja.data.Section
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
        NumberDescription(
            "surface_temperature",
            Dimension.TEMPERATURE,
            role = Role.Overrideable(::environmentsSurfaceTemperature),
        ),
        NumberDescription(
            "bottom_temperature",
            Dimension.TEMPERATURE,
            role = Role.Overrideable(::environmentsBottomTemperature),
        ),
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

/** Anything is allowed; these are the ones the manual names. */
private val GAS_USAGES = setOf("bottom", "stage", "deco", "travel", "bailout")

/** Anything is allowed; these are the ones the manual names. */
private val GAS_CONFIGURATIONS = setOf("back mounted", "sidemount", "pony", "staged")

/**
 * GasSource is one thing breathed from on a dive, under a key on that dive or on one of its
 * profiles.
 *
 * A dive's are what was breathed, and every recording of that dive shares them. A profile keeps
 * its own only where it is a plan, two plans for one dive being free to assume different gases.
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
        // Litres a minute at the surface over the time this source was breathed, from the primary
        // recording's; written by hand where there is no recording to give one, which is every
        // source a plan holds. `LOGIC-33`.
        NumberDescription(
            "sac",
            Dimension.FLOW,
            label = "SAC",
            role = Role.Overrideable(::sourcesSac),
        ),
        REMARKS,
    ),
    proposedId = ::gasSourcesProposedKey,
)

/**
 * A runtime line is one line of a plan as it was typed: a depth, and how it is reached.
 *
 * Keyed by its place, from `1`, which is how its order is kept. A line that gives neither a
 * duration nor a rate moves at the plan's own rate. `DATA-129`.
 */
private val RUNTIME_LINE = ItemDescription(
    "runtime_line",
    listOf(
        NumberDescription("depth", Dimension.LENGTH),
        NumberDescription("duration", Dimension.TIME),
        NumberDescription("rate", Dimension.SPEED),
        // Absent to breathe what the line above breathes.
        KeyReferenceDescription("gas_source", collection = "gas_sources"),
        REMARKS,
    ),
)

/**
 * Profile is one run through a dive, under a key on that dive: what a computer recorded, or what
 * somebody intends.
 *
 * The two are one type because they are the same shape. A plan's depths run to the surface as a
 * recording's do, and what a decompression model says about either is worked out when it is asked
 * for and never stored. What a plan holds that a recording does not is its own gas sources; what a
 * recording holds that a plan does not is everything a device wrote.
 *
 * Absent so far: nothing of its own.
 */
internal val PROFILE = ItemDescription(
    "profile",
    listOf(
        // Whether the series below are what is intended rather than what happened. Absent is a
        // recording: every profile written before plans existed is one, and a dive is one that was
        // made unless something says otherwise.
        BooleanDescription("planned", housekeeping = true),
        // Worked out from the serial: the gear item carrying it. Written where the user says
        // otherwise, or names a computer they keep no item for. `LOGIC-23`.
        ReferenceDescription(
            "dive_computer",
            targetType = "gear",
            oneOffAllowed = true,
            role = Role.Overrideable(::profilesComputer),
        ),
        // What the device says it is, which is what tells two of one model apart. `LOGIC-23`.
        TextDescription("serial", housekeeping = true),
        // What the device knows this recording by, kept so a later download can say where it
        // got to. `DATA-90`. One per stretch where a computer cut the dive up: `LOGIC-25`.
        TextDescription("fingerprint", cardinality = Cardinality.LIST, housekeeping = true),
        // What the dive works its own start from, and the dive's is the one read.
        DateDescription("start_date", source = true),
        TimeDescription("start_time", source = true),
        // How far the computer's clock read ahead of local time: one that drifted, one left on
        // home time, one that missed summer time. The user's to set, and a download never writes
        // it. A length of time like any other, and scoped like one. `LOGIC-32`, `DATA-10`.
        NumberDescription("recorded_time_offset", Dimension.TIME, housekeeping = true),
        NumberDescription(
            "duration",
            Dimension.TIME,
            role = Role.Overrideable(::profilesDuration),
        ),
        // Worth correcting: a computer usually reports a better figure than its own samples,
        // which are taken only every few seconds.
        NumberDescription(
            "max_depth",
            Dimension.LENGTH,
            role = Role.Overrideable(::profilesMaxDepth),
        ),
        NumberDescription(
            "average_depth",
            Dimension.LENGTH,
            role = Role.Overrideable(::profilesAverageDepth),
        ),
        // What the computer was set to while it recorded, which is not what the site is.
        TextDescription("water_type", fixedSet = WATER_TYPES),
        // What the recorded depths were made with, which is what reading them back needs.
        NumberDescription(
            "density",
            Dimension.DENSITY,
            role = Role.Overrideable(::profilesDensity),
        ),
        // The air above this run, absolute: about a bar at sea level. Each recording carries its
        // own, a computer measuring it, and a plan writes what it assumes. `DATA-124`.
        NumberDescription("atmospheric_pressure", Dimension.PRESSURE),
        // The coldest water this run saw, and the water at the surface. A computer reports both,
        // and the samples give each where it does not.
        NumberDescription(
            "bottom_temperature",
            Dimension.TEMPERATURE,
            role = Role.Overrideable(::profilesBottomTemperature),
        ),
        NumberDescription(
            "surface_temperature",
            Dimension.TEMPERATURE,
            role = Role.Overrideable(::profilesSurfaceTemperature),
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
        OwnedItemDescription("tolerances", TOLERANCES, housekeeping = true),
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
        // Litres a minute at the surface, through time, from the pressure of whichever source was
        // being breathed. Never written. `LOGIC-33`.
        NumberDescription(
            "sac",
            Dimension.FLOW,
            label = "SAC",
            cardinality = Cardinality.SERIES,
            role = Role.Derived(::profilesSac),
        ),
        // Which run the gas still in the user was carried from, written `@2026-09-20#0*b`. The
        // dive's `previous_dive` and its primary profile, and written to name one plan of it
        // rather than another: a chain of plans beside a chain of dives. `DATA-57`.
        KeyReferenceDescription(
            "previous_profile",
            collection = "profiles",
            targetType = "dive",
            role = Role.Overrideable(::profilesPrevious),
        ),
        // The run's own cylinders, which its switches and its pressures name. A plan keeps what it
        // assumes and a recording what its computer said. `JSON-19`, `RECON-7`.
        OwnedItemDescription("gas_sources", GAS_SOURCE, cardinality = Cardinality.KEYED),
        // What the planner was set to when it made a plan, so a plan opened again is the plan that
        // was saved. A setting a new plan starts from is each name with `default_` before it. The
        // points above are the plan, and these only make them again. `DATA-129`.
        NumberDescription("po2_max_bottom", Dimension.PRESSURE, label = "pO₂ max bottom", housekeeping = true),
        NumberDescription("po2_max_deco", Dimension.PRESSURE, label = "pO₂ max deco", housekeeping = true),
        NumberDescription("po2_min", Dimension.PRESSURE, label = "pO₂ min", housekeeping = true),
        NumberDescription("descent_rate", Dimension.SPEED, housekeeping = true),
        NumberDescription("ascent_rate", Dimension.SPEED, housekeeping = true),
        NumberDescription("last_stop", Dimension.LENGTH, housekeeping = true),
        // Whether the way up stops to switch gas where no deco stop is owed. `LOGIC-35`.
        BooleanDescription("gas_switch_stops", housekeeping = true),
        NumberDescription("safety_stop_depth", Dimension.LENGTH, housekeeping = true),
        // Nought is no safety stop.
        NumberDescription("safety_stop_duration", Dimension.TIME, housekeeping = true),
        // Times the usual SAC each of two divers sharing gas breathes at. `LOGIC-40`.
        NumberDescription("stress_factor", Dimension.DIMENSIONLESS, housekeeping = true),
        NumberDescription("problem_solving_time", Dimension.TIME, housekeeping = true),
        BooleanDescription("lost_gas_reserve", housekeeping = true),
        // Absent for the first deco cylinder, which is what the reserve loses unless told.
        KeyReferenceDescription("lost_gas", collection = "gas_sources", housekeeping = true),
        BooleanDescription("shared_gas_reserve", housekeeping = true),
        // The lines as they were typed, without the way up the planner adds to them.
        OwnedItemDescription("runtime", RUNTIME_LINE, cardinality = Cardinality.KEYED, housekeeping = true),
        REMARKS,
    ),
    proposedId = ::profilesProposedKey,
)


/**
 * The run whose gas is still in the user when this one begins.
 *
 * The dive's own `previous_dive`, and that dive's primary profile within it. Absent where the dive
 * names no earlier one, which is how most dives start, and where the earlier dive has nothing to
 * work from.
 *
 * **Written to follow one plan rather than another.** A dive names the dive before it, and that is
 * enough while there is one thing that happened. Two plans for one afternoon are two things that
 * might, so a plan for the dive after says which of them it assumes, and a chain of plans runs
 * beside the chain of dives.
 */
private fun profilesPrevious(profile: Item): Result<Any> {
    val dive = (profile as? OwnedItem)?.parent ?: return Result.Absent
    val named = dive.single<Reference>("previous_dive") as? Result.Usable ?: return Result.Absent
    val id = (named.value as? Reference.Identified)?.id
        ?: return unusable("the run before this one needs a dive with an id to be found in")
    val earlier = dive.set[id] ?: return unusable("$id is not in this logbook")
    if (earlier.description != DIVE) {
        return unusable("$id is a ${earlier.description.name} rather than a dive")
    }
    val chosen = primaryProfile(earlier)
    if (chosen !is Result.Usable) return chosen as? Result.Unusable ?: Result.Absent
    val key = (earlier.keyed<OwnedItem>("profiles") as? Result.Usable)?.value.orEmpty()
        .entries.firstOrNull { (_, entry) -> (entry as? Element.Usable)?.value === chosen.value }
        ?.key
        ?: return Result.Absent
    return Result.Usable(KeyReference(key, id), Result.Origin.DERIVED)
}

/**
 * The coldest water this run sampled, or absent where it sampled none.
 *
 * A computer that reports its own writes it and this is not consulted. `DATA-124`.
 */
private fun profilesBottomTemperature(profile: Item): Result<Any> {
    val read = (profile.read("temperature") as? Result.Usable)?.value as? Series
        ?: return Result.Absent
    val coldest = read.usable().filterIsInstance<Double>().minOrNull() ?: return Result.Absent
    return Result.Usable(coldest, Result.Origin.DERIVED)
}

/**
 * The water at the surface as this run sampled it, or absent where it sampled none.
 *
 * The last sample, taken as the dive ends at the surface with the sensor an hour in the water.
 * The first is not used: a computer goes in warm from the air, and its first reading is of
 * itself. A computer that reports its own writes it and this is not consulted. `DATA-124`.
 */
private fun profilesSurfaceTemperature(profile: Item): Result<Any> {
    val read = (profile.read("temperature") as? Result.Usable)?.value as? Series
        ?: return Result.Absent
    val last = read.usable().filterIsInstance<Double>().lastOrNull() ?: return Result.Absent
    return Result.Usable(last, Result.Origin.DERIVED)
}

/** The coldest water the dive's primary run saw. */
private fun environmentsBottomTemperature(environment: Item): Result<Any> =
    ofPrimary(environment) { it.single<Double>("bottom_temperature") }

/** The water at the surface, as the dive's primary run has it. */
private fun environmentsSurfaceTemperature(environment: Item): Result<Any> =
    ofPrimary(environment) { it.single<Double>("surface_temperature") }

/**
 * What [take] reads on the primary run of the dive owning [environment].
 *
 * Absent where the environment sits outside a dive, which nothing that reads a logbook produces.
 */
private fun ofPrimary(environment: Item, take: (Item) -> Result<Double>): Result<Any> {
    val dive = (environment as? OwnedItem)?.parent ?: return Result.Absent
    return fromProfile(dive) { profile ->
        val read = take(profile) as? Result.Usable ?: return@fromProfile Result.Absent
        Result.Usable(read.value, Result.Origin.DERIVED)
    }
}

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

/**
 * The gear item carrying the serial this recording carries, or absent where none does.
 *
 * Absent rather than the device's name: a brand and a model are not which computer, and the
 * profile's key already says what the recording was filed under. `LOGIC-23`.
 */
private fun profilesComputer(profile: Item): Result<Any> {
    val serial = (profile.single<String>("serial") as? Result.Usable)?.value
        ?: return Result.Absent
    for (gear in profile.set.allOf(GEAR)) {
        val held = (gear.single<String>("serial") as? Result.Usable)?.value ?: continue
        if (!sameSerial(serial, held)) continue
        val id = profile.set.idOf(gear) ?: continue
        return Result.Usable(Reference.Identified(id), Result.Origin.DERIVED)
    }
    return Result.Absent
}

/** What the computer this recording came off takes salt water to weigh. */
private fun saltDensity(profile: Item): Double {
    val named = profile.single<Reference>("dive_computer") as? Result.Usable
    val id = ((named?.value) as? Reference.Identified)?.id ?: return USUAL_SALT
    val computer = profile.set[id] ?: return USUAL_SALT
    return (computer.single<Double>("salt_density") as? Result.Usable)?.value ?: USUAL_SALT
}

/**
 * The density [waterType] is taken at where nothing more is known, in kilograms a cubic metre, or
 * null for a type this does not know.
 *
 * What a plan uses, having no computer whose setting could say otherwise, so a plan in salt water
 * is worked out at the density a recording in salt water falls back to. A recording of salt water
 * reads its computer's setting instead.
 */
fun densityOfWater(waterType: String): Double? =
    FIXED_DENSITIES[waterType] ?: if (waterType == SALT) USUAL_SALT else null

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
 * Dive is one dive, and the largest thing here.
 *
 * Absent so far: nothing of its own.
 */
private val TAGS = setOf(
    "wreck",
    "night",
    "drift",
    "course",
    "teaching",
    "training",
    "cave",
    "cavern",
    "ice",
)

internal val DIVE: ItemDescription = ItemDescription(
    "dive",
    listOf(
        // The dive's start, from the primary profile and in GMT. Correctable, as everything
        // else a recording gives is: the computer was there and the user was busy, and a
        // recording can still be wrong.
        DateDescription("start_date", role = Role.Overrideable(::divesStartDate)),
        TimeDescription("start_time", role = Role.Overrideable(::divesStartTime)),
        NumberDescription(
            "duration",
            Dimension.TIME,
            role = Role.Overrideable(::divesDuration),
        ),
        // The user's own numbering, which nothing renumbers. Not every diver keeps one.
        WholeNumberDescription("dive_number"),
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
        // A written name is allowed, as it is for a buddy: a quarry dived once on holiday is a
        // name somebody remembers rather than an item worth keeping. `DATA-126`.
        ReferenceDescription("dive_site", targetType = "dive_site", oneOffAllowed = true),
        BooleanDescription("deco", role = Role.Overrideable(::divesDeco)),
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
        ReferenceDescription("dive_trip", targetType = "dive_trip"),
        ReferenceDescription("operator", targetType = "operator"),
        TextDescription("tags", cardinality = Cardinality.LIST, suggestedSet = TAGS),
        OwnedItemDescription("environment", ENVIRONMENT),
        OwnedItemDescription("gear", DIVE_GEAR),
        OwnedItemDescription("profiles", PROFILE, cardinality = Cardinality.KEYED),
        // The primary profile's cylinders, unless the user wrote the dive's own. `RECON-7`.
        OwnedItemDescription(
            "gas_sources",
            GAS_SOURCE,
            cardinality = Cardinality.KEYED,
            role = Role.Overrideable(::divesSources),
        ),
        // The id, which is what a dive is listed and linked as. Not correctable: writing
        // one would be renaming the dive, which the Universe does with its references.
        TextDescription("name", role = Role.Derived(::divesId)),
        // Whether this dive is still ahead: it holds profiles and every one of them is a plan.
        // What counts dives leaves it out, and so does what leaves the logbook.
        BooleanDescription("planned", role = Role.Derived(::divesPlanned), housekeeping = true),
        // How far local time was ahead of GMT where the dive was made. The times above are local
        // and shown as they are; this puts two dives on one clock to compare them, and a dive
        // saying nothing is taken to be on GMT. `LOGIC-32`.
        NumberDescription("time_zone_offset", Dimension.TIME, housekeeping = true),
        // Which of `profiles` to work from. Leaving it out where there is one is the
        // ordinary case.
        KeyReferenceDescription("primary_profile", collection = "profiles", housekeeping = true),
        REMARKS,
    ),
    orderedBy = listOf(
        Ordering("start_date", Direction.DESCENDING),
        Ordering("start_time", Direction.DESCENDING),
    ),
    proposedId = ::divesProposedId,
    // How the dive was arranged, which is one thing to read and three fields to hold. `DATA-122`.
    sections = listOf(Section("details", listOf("dive_trip", "operator", "tags"), layout = Layout.BOX)),
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

private fun divesDuration(dive: Item): Result<Any> = fromProfile(dive, ::profilesDuration)

/**
 * The deepest point a recording reached.
 *
 * The largest depth of the `depth` series. A sample that could not be read is passed over: one
 * bad number does not hide how deep the rest of the dive went.
 */
private fun divesMaxDepth(dive: Item): Result<Any> =
    fromProfile(dive) { profile -> asDerived(profile.single<Double>("max_depth")) }

/** The deepest sample a recording took, or absent where it took none. */
private fun profilesMaxDepth(profile: Item): Result<Any> {
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series
    val deepest = depth?.usable()?.filterIsInstance<Double>()?.maxOrNull() ?: return Result.Absent
    return Result.Usable(deepest, Result.Origin.DERIVED)
}

/** [read] as a figure the dive worked out, whoever wrote it on the recording. */
private fun asDerived(read: Result<Double>): Result<Any> = when (read) {
    is Result.Usable -> Result.Usable(read.value, Result.Origin.DERIVED)
    is Result.Unusable -> read
    Result.Absent -> Result.Absent
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
private fun divesAverageDepth(dive: Item): Result<Any> =
    fromProfile(dive) { profile -> asDerived(profile.single<Double>("average_depth")) }

/** How deep a recording was on average, weighted by how long it held each depth. */
private fun profilesAverageDepth(profile: Item): Result<Any> {
    val depth = (profile.read("depth") as? Result.Usable)?.value as? Series
        ?: return Result.Absent
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
    return if (ran == 0) Result.Absent else Result.Usable(area / ran, Result.Origin.DERIVED)
}

/**
 * Whether a dive went past the no-decompression limit.
 *
 * A `decostop` above zero at any point means yes. Failing that, a `no_deco_time` that never
 * reached zero means no. Most computers write stops only where there are stops, which is why
 * the second reading matters. A zero counts only once a positive value has come before it: a
 * computer's first reading precedes its first calculation and reads zero at the surface, and a
 * dive cannot begin in deco.
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
        left.dropWhile { it <= 0.0 }.all { it > 0.0 } -> Result.Usable(false, Result.Origin.DERIVED)
        else -> Result.Usable(true, Result.Origin.DERIVED)
    }
}

/**
 * Whether a dive is one that was made, which is what a list of dives gathers, what a figure counts
 * and what a screen shows as diving done.
 *
 * A dive says so itself through `planned`, so this is one reading of one field. It is public
 * because a front end asks the same question, and two answers to it would be two defaults for a
 * dive that says nothing. `LOGIC-36`.
 */
fun wasMade(dive: Item): Boolean =
    (dive.single<Boolean>("planned") as? Result.Usable)?.value != true

/**
 * Whether a dive is one nobody has made yet.
 *
 * True where it holds profiles and every one of them is a plan. A dive with no profile at all was
 * made — one typed out of a paper logbook is the ordinary case — and so is one carrying a
 * recording beside its plans, the recording being the evidence.
 *
 * A profile that cannot be read says nothing, so it is not a plan, and the dive is one that
 * happened. The safe answer in a figure is the dive that counts.
 */
private fun divesPlanned(dive: Item): Result<Any> {
    val profiles = dive.keyed<OwnedItem>("profiles") as? Result.Usable
        ?: return Result.Usable(false, Result.Origin.DERIVED)
    if (profiles.value.isEmpty()) return Result.Usable(false, Result.Origin.DERIVED)
    val planned = profiles.value.values.all { entry ->
        val profile = (entry as? Element.Usable)?.value
        profile != null && (profile.single<Boolean>("planned") as? Result.Usable)?.value == true
    }
    return Result.Usable(planned, Result.Origin.DERIVED)
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
 * From `previous_dive`'s end to this dive's start, each taken off its own local time by its
 * `time_zone_offset`, which is what makes it right when two dives sit in different zones.
 *
 * **Absent where no previous dive is named**: whether a surface interval was long enough to
 * ignore is a judgement, and any threshold deciding it would be wrong for somebody.
 */
private fun surfaceInterval(dive: Item): Result<Any> {
    val named = dive.single<Reference>("previous_dive") as? Result.Usable ?: return Result.Absent
    val id = (named.value as? Reference.Identified)?.id
        ?: return unusable("a surface interval needs a dive with an id to measure from")
    val before = dive.set[id] ?: return unusable("$id is not in this logbook")
    val out = endOf(before)?.let { absoluteOf(before, it) } ?: return Result.Absent
    val back = momentOf(dive, "start_date", "start_time")?.let { absoluteOf(dive, it) }
        ?: return Result.Absent
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
