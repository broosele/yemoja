package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.NumberDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription

/*
 * What a region, a piece of gear and a person are. The machinery that reads and resolves these
 * is in the data layer, which knows nothing about diving; the descriptions are here because
 * they do.
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
 * **Every shape but a series.** A person carries their medical, their insurance and their
 * courses; a piece of gear its buoyancy and its maintenances. What is absent is a series, which
 * only a dive profile has, and the six item types the manual describes besides these three.
 */
object Types {

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

    /** Every type, which is what an item set is built with. */
    val ALL: List<ItemDescription> = listOf(PERSON, REGION, GEAR)
}

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
    ),
)

/** Anything is allowed; these are the ones the manual names. */
private val MAINTENANCE_TYPES =
    setOf("visual inspection", "repair", "service", "cleaning")

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
        MultilineTextDescription("remarks"),
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
