package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.Date
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.KeyReferenceDescription
import yemoja.data.Moment
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
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
 * What a dive, a person, a place and a piece of gear are. The machinery that reads and resolves
 * these is in the data layer, which knows nothing about diving; the descriptions are here
 * because they do.
 *
 * manual/data-fields.md is the source of truth for every field. Where this and that document
 * disagree, that one is right and this one is a bug.
 *
 * See ../../../../../doc.md — "Scope".
 */

/**
 * Types is every item type the application knows.
 *
 * An `ItemSet` is built with these, so this list is what the whole application shares as its
 * vocabulary. A front end may name a type; it may not describe one.
 *
 * **Every item type the manual defines, and every shape a field can take.** What is absent is
 * a field that is worked out rather than recorded, wherever what it would be worked out from is
 * not there to work from. Each type says which of its own are missing.
 */
object Types {

    /**
     * Dive is one dive, and the largest thing here.
     *
     * Absent so far: nothing of its own.
     */
    val DIVE: ItemDescription = ItemDescription(
        "dive",
        listOf(
            // The id, which is what a dive is listed and linked as. Not correctable: writing
            // one would be renaming the dive, which the Universe does with its references.
            TextDescription("name", role = Role.Derived(::divesId)),
            // The user's own numbering, which nothing renumbers. Not every diver keeps one.
            WholeNumberDescription("dive_number"),
            ReferenceDescription("dive_site", targetType = "dive_site"),
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
            ReferenceDescription(
                "buddies",
                targetType = "person",
                cardinality = Cardinality.LIST,
                oneOffAllowed = true,
            ),
            // Corrected where the names are fewer than the people: a diver remembers how many
            // were there without remembering all of them.
            WholeNumberDescription("buddy_count", role = Role.Overrideable(::buddyCount)),
            WholeNumberDescription("rating", range = 1..10),
            OwnedItemDescription("details", DETAILS),
            OwnedItemDescription("environment", ENVIRONMENT),
            OwnedItemDescription("gear", DIVE_GEAR),
            OwnedItemDescription("profiles", PROFILE, cardinality = Cardinality.KEYED),
            // Which of them to work from. Leaving it out where there is one is the ordinary case.
            KeyReferenceDescription("primary_profile", collection = "profiles"),
            OwnedItemDescription("gas_sources", GAS_SOURCE, cardinality = Cardinality.KEYED),
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
            BooleanDescription("deco", role = Role.Overrideable(::divesDeco)),
            REMARKS,
        ),
    )

    /**
     * Person is anyone who appears in a logbook, whether or not they dive.
     *
     * Absent so far: nothing of their own.
     */
    val PERSON: ItemDescription = ItemDescription(
        "person",
        listOf(
            // Worked out from the parts, and corrected where the assembly reads wrong: names do
            // not all follow one pattern. An id is worked out from this.
            TextDescription("name", role = Role.Overrideable(::assembledName)),
            TextDescription("first_name"),
            // All of them together, where there are several.
            TextDescription("middle_names"),
            TextDescription("last_name"),
            DateDescription("birthday"),
            TextDescription("address"),
            TextDescription("email"),
            TextDescription("phone"),
            // A plain name stands in for someone with no item of their own, which is what makes
            // this the one reference here that allows one.
            ReferenceDescription(
                "emergency_contacts",
                targetType = "person",
                cardinality = Cardinality.LIST,
                oneOffAllowed = true,
            ),
            OwnedItemDescription("medical", MEDICAL),
            OwnedItemDescription("insurance", INSURANCE),
            OwnedItemDescription("courses", COURSE, cardinality = Cardinality.KEYED),
            REMARKS,
        ),
    )

    /**
     * Region is a part of the world: a continent, an ocean, a country, a sea.
     *
     * Absent so far: nothing of its own.
     */
    val REGION: ItemDescription = ItemDescription(
        "region",
        listOf(
            TextDescription("name"),
            // More than one, since a region often sits inside several at once.
            ReferenceDescription("parents", targetType = "region", cardinality = Cardinality.LIST),
            // The other side of `parents`, gathered from every region there is, the supplied
            // ones included. Never written: it would be the same fact twice.
            ReferenceDescription(
                "children",
                targetType = "region",
                cardinality = Cardinality.LIST,
                role = Role.Derived(::regionsChildren),
            ),
            TextDescription("category", suggestedSet = REGION_CATEGORIES),
            // The four edges of a box holding the region, for placing it on a map. `east` is the
            // edge reached travelling east from `west`, which is what makes the date line
            // unremarkable: the Pacific runs from 120 to -70. Latitude does not wrap.
            NumberDescription("west", Dimension.ANGLE, range = LONGITUDE),
            NumberDescription("east", Dimension.ANGLE, range = LONGITUDE),
            NumberDescription("south", Dimension.ANGLE, range = LATITUDE),
            NumberDescription("north", Dimension.ANGLE, range = LATITUDE),
            REMARKS,
        ),
    )

    /**
     * Gear is a piece of equipment. A dive computer is gear like anything else.
     *
     * Absent so far: nothing of their own.
     */
    val GEAR: ItemDescription = ItemDescription(
        "gear",
        listOf(
            TextDescription("name"),
            TextDescription("brand"),
            TextDescription("model"),
            TextDescription("serial"),
            TextDescription("category", suggestedSet = GEAR_CATEGORIES),
            // What the item is, more finely than its category: a wing within BCD, gloves within
            // suit. No vocabulary is suggested, because the manual gives examples rather than a
            // list and inventing one here would make it the list.
            TextDescription("kind"),
            TextDescription("description"),
            // For a cylinder, how much water it would hold. A twelve-litre has a capacity of 12.
            // An American cylinder named for the gas it delivers is not named by this.
            NumberDescription("capacity", Dimension.VOLUME),
            // For a dive computer, what it takes salt water to weigh. A computer measures
            // pressure and divides by this to show a depth, so the maker's figure is baked
            // into every depth it wrote.
            NumberDescription("salt_density", Dimension.DENSITY),
            // Whether this describes a kind of item rather than one the user owns. Absent is
            // false: your own gear is your own.
            BooleanDescription("generic"),
            OwnedItemDescription("buoyancy", BUOYANCY),
            OwnedItemDescription("maintenances", MAINTENANCE, cardinality = Cardinality.KEYED),
            REMARKS,
        ),
    )

    /**
     * DiveSite is a place dived at.
     *
     * Absent so far: nothing of its own.
     */
    val DIVE_SITE: ItemDescription = ItemDescription(
        "dive_site",
        listOf(
            TextDescription("name"),
            TextDescription("alternative_names", cardinality = Cardinality.LIST),
            ReferenceDescription("regions", targetType = "region", cardinality = Cardinality.LIST),
            NumberDescription("longitude", Dimension.ANGLE, range = LONGITUDE),
            NumberDescription("latitude", Dimension.ANGLE, range = LATITUDE),
            // The height of the water above sea level, which changes how a dive is worked out.
            NumberDescription("elevation", Dimension.LENGTH),
            TextDescription("water_type", fixedSet = WATER_TYPES),
            // The site's own depth, not how deep anybody went.
            NumberDescription("max_depth", Dimension.LENGTH),
            WholeNumberDescription("rating", range = 1..10),
            TextDescription("environment_type", fixedSet = ENVIRONMENT_TYPES),
            ReferenceDescription("wrecks", targetType = "wreck", cardinality = Cardinality.LIST),
            // A description rather than a classification, so no list to choose from.
            TextDescription("substrate"),
            TextDescription("facilities", cardinality = Cardinality.LIST),
            REMARKS,
        ),
    )

    /**
     * Wreck is a ship lying at a site.
     *
     * Absent so far: nothing of its own.
     */
    val WRECK: ItemDescription = ItemDescription(
        "wreck",
        listOf(
            TextDescription("name"),
            TextDescription("alternative_names", cardinality = Cardinality.LIST),
            TextDescription("ship_type", suggestedSet = SHIP_TYPES),
            TextDescription("nationality"),
            TextDescription("shipyard"),
            DateDescription("launched"),
            DateDescription("sunk"),
            NumberDescription("length", Dimension.LENGTH),
            NumberDescription("beam", Dimension.LENGTH),
            NumberDescription("draught", Dimension.LENGTH),
            // The weight of water she pushed aside, which is a mass like any other.
            NumberDescription("displacement", Dimension.MASS),
            REMARKS,
        ),
    )

    /**
     * Certification is a diving qualification.
     *
     * Absent so far: nothing of its own.
     */
    val CERTIFICATION: ItemDescription = ItemDescription(
        "certification",
        listOf(
            TextDescription("name"),
            TextDescription("abbreviation"),
            // Who awards it, written as a plain name rather than as an item.
            TextDescription("organisation"),
            NumberDescription("max_depth", Dimension.LENGTH),
            ReferenceDescription(
                "supersedes",
                targetType = "certification",
                cardinality = Cardinality.LIST,
            ),
            TextDescription("category", suggestedSet = CERTIFICATION_CATEGORIES),
            REMARKS,
        ),
    )

    /**
     * Operator is anyone who takes a user diving or looks after their gear.
     *
     * Absent so far: nothing of its own.
     */
    val OPERATOR: ItemDescription = ItemDescription(
        "operator",
        listOf(
            TextDescription("name"),
            // What they were called before. Dive centres are bought and rebranded, and the dives
            // done there were with the old name.
            TextDescription("alternative_names", cardinality = Cardinality.LIST),
            ReferenceDescription("region", targetType = "region"),
            TextDescription("address"),
            TextDescription("phone"),
            TextDescription("email"),
            TextDescription("website"),
            TextDescription("category", suggestedSet = OPERATOR_CATEGORIES),
            WholeNumberDescription("rating", range = 1..10),
            REMARKS,
        ),
    )

    /**
     * DiveTrip is diving done on one occasion or in one place.
     *
     * Absent so far: nothing of its own.
     */
    val DIVE_TRIP: ItemDescription = ItemDescription(
        "dive_trip",
        listOf(
            TextDescription("name"),
            // The larger trip this one is part of, where there is one.
            ReferenceDescription("parent", targetType = "dive_trip"),
            // The other side of `parent`. A leg names the trip; the trip does not name its legs.
            ReferenceDescription(
                "parts",
                targetType = "dive_trip",
                cardinality = Cardinality.LIST,
                role = Role.Derived(::tripsParts),
            ),
            ReferenceDescription("region", targetType = "region"),
            ReferenceDescription("operator", targetType = "operator"),
            // Every dive naming this trip, and every dive of a trip beneath it. Never written:
            // each dive says which trip it belongs to, so the two cannot disagree.
            ReferenceDescription(
                "dives",
                targetType = "dive",
                cardinality = Cardinality.LIST,
                role = Role.Derived(::tripsDives),
            ),
            // From the dives on it, and corrected where the trip was longer than the diving:
            // a travelling day at either end, or a trip set up before anything was logged.
            DateDescription("start_date", role = Role.Overrideable(::tripsStartDate)),
            DateDescription("end_date", role = Role.Overrideable(::tripsEndDate)),
            REMARKS,
        ),
    )

    /** Every type, which is what an item set is built with. */
    val ALL: List<ItemDescription> = listOf(
        DIVE,
        PERSON,
        REGION,
        DIVE_SITE,
        WRECK,
        GEAR,
        CERTIFICATION,
        OPERATOR,
        DIVE_TRIP,
    )
}

/** Closed: what a site is in, and what a computer was set to. */
private val WATER_TYPES = setOf("salt", "fresh", "en13319")

/** Closed: what kind of place a site is. */
private val ENVIRONMENT_TYPES = setOf(
    "ocean", "sea", "lake", "river", "quarry", "spring", "cave", "cavern", "under ice", "pool",
    "hyperbaric chamber",
)

/** Closed: six steps, for a current and for the state of the surface alike. */
private val STRENGTHS = setOf("none", "very mild", "mild", "moderate", "hard", "very hard")

/** Closed: what a dive computer warns about. */
private val ALARMS = setOf(
    "ascent", "breath", "deco", "error", "link", "microbubbles", "rbt", "skincooling", "surface",
)

/** Anything is allowed; these are the ones the manual names. */
private val WARMTHS = setOf("very cold", "cold", "good", "warm", "too warm")

/** Anything is allowed; these are the ones the manual names. */
private val WEIGHTINGS = setOf("way too heavy", "too heavy", "good", "too light", "way too light")

/** Anything is allowed; these are the ones the manual names. */
private val GAS_USAGES = setOf("bottom", "stage", "deco", "travel")

/** Anything is allowed; these are the ones the manual names. */
private val GAS_CONFIGURATIONS = setOf("back mounted", "sidemount", "pony", "staged")

/** Anything is allowed; these are the ones the manual names. */
private val SHIP_TYPES = setOf("freighter", "tanker", "warship", "hospital ship")

/** Anything is allowed; these are the ones the supplied certifications use. */
private val CERTIFICATION_CATEGORIES =
    setOf("progression", "specialisation", "technical", "professional")

/** Anything is allowed; these are the ones the manual names. */
private val OPERATOR_CATEGORIES = setOf(
    "dive center", "hotel", "dive resort", "boat operator", "dive club", "liveaboard operator",
)

/** Anything is allowed; these are the ones the supplied regions use. */
private val REGION_CATEGORIES = setOf("world", "continent", "ocean", "sea", "country", "area")

/** Anything is allowed; these are the ones the manual names. */
/** The models a dive computer may be running, as libdivecomputer names them. */
private val DECO_MODELS = setOf("buhlmann", "vpm", "rgbm", "dciem")

private val GEAR_CATEGORIES = setOf(
    "ABC",
    "BCD",
    "regulator",
    "cylinder",
    "suit",
    "weights",
    "instruments",
    "lighting",
    "photography",
    "accessory",
)

private val LONGITUDE = -180.0..180.0

private val LATITUDE = -90.0..90.0

/** Anything is allowed; these are the ones the manual names. */
private val MAINTENANCE_TYPES =
    setOf("visual inspection", "repair", "service", "cleaning")

/** Details is the labels on a dive, and which trip and operator it belonged to. */
private val DETAILS = ItemDescription(
    "details",
    listOf(
        TextDescription("tags", cardinality = Cardinality.LIST),
        ReferenceDescription("dive_trip", targetType = "dive_trip"),
        ReferenceDescription("operator", targetType = "operator"),
        REMARKS,
    ),
)

/** Environment is the conditions a dive was found in. */
private val ENVIRONMENT = ItemDescription(
    "environment",
    listOf(
        TextDescription("current", fixedSet = STRENGTHS),
        TextDescription("waves", fixedSet = STRENGTHS),
        // One figure rather than a range, which is what a diver remembers.
        NumberDescription("visibility", Dimension.LENGTH),
        NumberDescription("air_temperature", Dimension.TEMPERATURE),
        NumberDescription("bottom_temperature", Dimension.TEMPERATURE),
        // From where the dive was, and absolute: about a bar at sea level.
        NumberDescription("atmospheric_pressure", Dimension.PRESSURE),
        REMARKS,
    ),
)

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
        NumberDescription("depth", Dimension.LENGTH, cardinality = Cardinality.SERIES),
        NumberDescription(
            "temperature",
            Dimension.TEMPERATURE,
            cardinality = Cardinality.SERIES,
        ),
        // Gauge pressure left in each cylinder: one series per gas source.
        NumberDescription(
            "pressures",
            Dimension.PRESSURE,
            cardinality = Cardinality.KEYED_SERIES,
        ),
        TextDescription("alarms", fixedSet = ALARMS, cardinality = Cardinality.SERIES),
        // Each naming the gas source moved to.
        KeyReferenceDescription(
            "gas_switches",
            collection = "gas_sources",
            cardinality = Cardinality.SERIES,
        ),
        // A rounded depth rather than a continuous ceiling: three metres, six, nine.
        NumberDescription("decostop", Dimension.LENGTH, cardinality = Cardinality.SERIES),
        NumberDescription("no_deco_time", Dimension.TIME, cardinality = Cardinality.SERIES),
        NumberDescription("no_flight_time", Dimension.TIME),
        NumberDescription("desaturation_time", Dimension.TIME),
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
        // What the computer was set to while it recorded, which is not what the site is.
        TextDescription("water_type", fixedSet = WATER_TYPES),
        // What the figures above were computed with. Suggested rather than fixed: a maker may
        // run something none of the four names, and nothing exports this to a closed list.
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
        // From the last sample, and correctable where the recording stopped before the user
        // surfaced.
        DateDescription("end_date", role = Role.Overrideable(::profilesEndDate)),
        TimeDescription("end_time", role = Role.Overrideable(::profilesEndTime)),
        NumberDescription(
            "duration",
            Dimension.TIME,
            role = Role.Overrideable(::profilesDuration),
        ),
        // What the recorded depths were made with, which is what reading them back needs.
        NumberDescription(
            "density",
            Dimension.DENSITY,
            role = Role.Overrideable(::profilesDensity),
        ),
        OwnedItemDescription("tolerances", TOLERANCES),
        REMARKS,
    ),
)

/**
 * GasSource is one thing breathed from on a dive, under a key on that dive.
 *
 * Absent so far: nothing of its own.
 */
private val GAS_SOURCE = ItemDescription(
    "gas_source",
    listOf(
        // Left out where what was breathed from is nobody's item.
        ReferenceDescription("cylinder", targetType = "gear"),
        NumberDescription("start_pressure", Dimension.PRESSURE),
        NumberDescription("end_pressure", Dimension.PRESSURE),
        GasDescription("gas_type"),
        TextDescription("usage", suggestedSet = GAS_USAGES),
        TextDescription("configuration", suggestedSet = GAS_CONFIGURATIONS),
        // From the cylinder's own `capacity`, and written by hand where no cylinder is named
        // or the one dived was not the one recorded.
        NumberDescription(
            "volume",
            Dimension.VOLUME,
            role = Role.Overrideable(::cylindersVolume),
        ),
        REMARKS,
    ),
)

/**
 * Medical is a person's health details, kept together rather than scattered through the item.
 *
 * Yemoja does not work out whether one is still valid. How long a check counts for depends on
 * who is asking — the agency, the operator, the country — rather than on the examination.
 */
private val MEDICAL = ItemDescription(
    "medical",
    listOf(
        DateDescription("last_medical_check"),
        TextDescription("blood_group"),
        NumberDescription("height", Dimension.LENGTH),
        NumberDescription("body_mass", Dimension.MASS),
        REMARKS,
    ),
)

/**
 * Insurance is the cover a person holds.
 *
 * Absent so far: nothing of its own.
 */
private val INSURANCE = ItemDescription(
    "insurance",
    listOf(
        TextDescription("name"),
        TextDescription("policy"),
        DateDescription("start_date"),
        DateDescription("end_date"),
        // Against today, which is the only thing here worked out from outside the logbook.
        WholeNumberDescription("days_left", role = Role.Derived(::insurancesDaysLeft)),
        BooleanDescription("expired", role = Role.Derived(::insurancesExpired)),
        REMARKS,
    ),
)

/** Course is one qualification a person earned, under a key on that person. */
private val COURSE = ItemDescription(
    "course",
    listOf(
        ReferenceDescription("certification", targetType = "certification"),
        ReferenceDescription("instructor", targetType = "person"),
        DateDescription("date"),
        ReferenceDescription("dives", targetType = "dive", cardinality = Cardinality.LIST),
        REMARKS,
    ),
)

/** Buoyancy is what a piece of gear does in the water, so that weighting can be worked out. */
private val BUOYANCY = ItemDescription(
    "buoyancy",
    listOf(
        NumberDescription("mass", Dimension.MASS),
        // What it pushes aside: the item, and any gas sealed inside it. Not a cylinder's
        // capacity, which is what fits in rather than what it displaces.
        NumberDescription("displaced_volume", Dimension.VOLUME),
        // How much of that is gas rather than solid, so that a suit losing lift with depth is
        // accounted for. It is not the foam's real gas content, which is higher.
        NumberDescription("compressible_fraction", Dimension.DIMENSIONLESS, range = 0.0..1.0),
        NumberDescription("lift_volume", Dimension.VOLUME),
        REMARKS,
    ),
)

/**
 * Maintenance is one thing done to a piece of gear, under a key on that gear.
 *
 * Absent so far: nothing of its own.
 */
private val MAINTENANCE = ItemDescription(
    "maintenance",
    listOf(
        TextDescription("type", suggestedSet = MAINTENANCE_TYPES),
        DateDescription("date"),
        // A date, always. Where an interval is counted in dives rather than months, the user
        // works out roughly when that falls and writes it.
        DateDescription("valid_until"),
        TextDescription("follow_up_type", suggestedSet = MAINTENANCE_TYPES),
        ReferenceDescription("operator", targetType = "operator"),
        // Against `valid_until` rather than `end_date`, and otherwise [INSURANCE]'s question.
        WholeNumberDescription("days_left", role = Role.Derived(::maintenancesDaysLeft)),
        BooleanDescription("expired", role = Role.Derived(::maintenancesExpired)),
        REMARKS,
    ),
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
 * The regions naming [region] as a parent.
 *
 * Gathered from every region in the set, the supplied ones included, so a country added to a
 * logbook appears among the children of a continent nothing touched. Empty rather than absent:
 * a region with nothing inside it has been asked and answered.
 */
private fun regionsChildren(region: Item): Result<Any> =
    pointingAt(region, Types.REGION, "parents")

/**
 * The trips naming [trip] as their parent.
 *
 * The other side of `parent`, gathered the way [regionsChildren] is.
 */
private fun tripsParts(trip: Item): Result<Any> =
    pointingAt(trip, Types.DIVE_TRIP, "parent")

/**
 * Every item of [type] whose [field] names [item], as references to them.
 *
 * One walk for both sides of a back-reference, whether the naming field holds one or several: a
 * region has many `parents` and a trip has one `parent`, and each is asked the same question. An
 * entry that would not read names nothing and is passed over.
 */
private fun pointingAt(item: Item, type: ItemDescription, field: String): Result<Any> {
    val id = (item as? ReferenceableItem)?.let { item.set.idOf(it) } ?: return Result.Absent
    val found = item.set.allOf(type)
        .filter { other -> id in namesIn(other, field) }
        .mapNotNull { other -> item.set.idOf(other) }
        .map { Element.Usable(Reference.Identified(it) as Any) }
    return Result.Usable(found, Result.Origin.DERIVED)
}

/** The ids [field] names on [item], however many it holds and whatever it failed to read. */
internal fun namesIn(item: Item, field: String): List<String> {
    val naming = item.description[field] ?: return emptyList()
    val read = item.read(field) as? Result.Usable ?: return emptyList()
    val values = if (naming.cardinality == Cardinality.LIST) {
        (read.value as? List<*>).orEmpty().mapNotNull { (it as? Element.Usable<*>)?.value }
    } else {
        listOf(read.value)
    }
    return values.filterIsInstance<Reference.Identified>().map { it.id }
}

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
 * A person's full name, assembled from the parts.
 *
 * The parts in order, separated by spaces, and whichever of them are missing left out. Absent
 * where none of them is there, so an empty person is not named after a run of spaces.
 */
private fun assembledName(person: Item): Result<Any> {
    val parts = NAME_PARTS
        .mapNotNull { (person.single<String>(it) as? Result.Usable)?.value }
        .filter { it.isNotBlank() }
    if (parts.isEmpty()) return Result.Absent
    return Result.Usable(parts.joinToString(" "), Result.Origin.DERIVED)
}

private val NAME_PARTS = listOf("first_name", "middle_names", "last_name")

/**
 * Every item type has one, for whatever has no field of its own.
 *
 * A new one each time it is read, because a description is immutable and shared but the field
 * list of each type is its own.
 */
private val REMARKS: MultilineTextDescription get() = MultilineTextDescription("remarks")
