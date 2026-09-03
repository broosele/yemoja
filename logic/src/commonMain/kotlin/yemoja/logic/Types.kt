package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.GasDescription
import yemoja.data.ItemDescription
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription
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
     * Absent so far: everything the manual works out. `name`, `start_date`, `start_time`,
     * `end_time`, `end_date`, `duration`, `max_depth` and `deco` come from the primary profile,
     * `surface_interval` from the dive before it, and `buddy_count` from the list of buddies.
     * Each needs a computation over what is around it rather than a description of its own.
     */
    val DIVE: ItemDescription = ItemDescription(
        "dive",
        listOf(
            // The user's own numbering, which nothing renumbers. Not every diver keeps one.
            WholeNumberDescription("dive_number"),
            ReferenceDescription("dive_site", targetType = "dive_site"),
            // The dive still being carried gas from when this one began.
            ReferenceDescription("previous_dive", targetType = "dive"),
            ReferenceDescription(
                "buddies",
                targetType = "person",
                cardinality = Cardinality.LIST,
                oneOffAllowed = true,
            ),
            WholeNumberDescription("rating", range = 1..10),
            OwnedItemDescription("details", DETAILS),
            OwnedItemDescription("environment", ENVIRONMENT),
            OwnedItemDescription("gear", DIVE_GEAR),
            OwnedItemDescription("profiles", PROFILE, cardinality = Cardinality.KEYED),
            // Which of them to work from. Leaving it out where there is one is the ordinary case.
            KeyReferenceDescription("primary_profile", collection = "profiles"),
            OwnedItemDescription("gas_sources", GAS_SOURCE, cardinality = Cardinality.KEYED),
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
     * Absent so far: the `children` worked out from `parents`, which needs a computation over
     * every region rather than a description.
     */
    val REGION: ItemDescription = ItemDescription(
        "region",
        listOf(
            TextDescription("name"),
            // More than one, since a region often sits inside several at once.
            ReferenceDescription("parents", targetType = "region", cardinality = Cardinality.LIST),
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
     * Absent so far: `dives` and `parts`, which follow from what names this trip, and the
     * `start_date` and `end_date` taken from the dives on it.
     */
    val DIVE_TRIP: ItemDescription = ItemDescription(
        "dive_trip",
        listOf(
            TextDescription("name"),
            // The larger trip this one is part of, where there is one.
            ReferenceDescription("parent", targetType = "dive_trip"),
            ReferenceDescription("region", targetType = "region"),
            ReferenceDescription("operator", targetType = "operator"),
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
 * Absent so far: `weight`, added up from the items taken.
 */
private val DIVE_GEAR = ItemDescription(
    "dive_gear",
    listOf(
        ReferenceDescription("items", targetType = "gear", cardinality = Cardinality.LIST),
        NumberDescription("mass", Dimension.MASS),
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
 * Absent so far: `end_date`, `end_time` and `duration`, which come from the last sample, and
 * `density`, which comes from the water type.
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
        OwnedItemDescription("tolerances", TOLERANCES),
        REMARKS,
    ),
)

/**
 * GasSource is one thing breathed from on a dive, under a key on that dive.
 *
 * Absent so far: `volume`, taken from the capacity of the cylinder it names.
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
 * Absent so far: `days_left` and `expired`, which are worked out from `end_date` against today.
 * Nothing in this project knows what today is.
 */
private val INSURANCE = ItemDescription(
    "insurance",
    listOf(
        TextDescription("name"),
        TextDescription("policy"),
        DateDescription("start_date"),
        DateDescription("end_date"),
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
 * Absent so far: `days_left` and `expired`, for the reason [INSURANCE] gives.
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
        REMARKS,
    ),
)

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
