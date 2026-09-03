package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
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
 * **Only fields holding one value are here so far.** Lists, keyed collections and owned items are
 * absent — so a person has no `courses` and a region no `parents` — and the six item types the
 * manual describes besides these three are absent too.
 */
object Types {

    /**
     * Person is anyone who appears in a logbook, whether or not they dive.
     *
     * Absent so far: `emergency_contacts`, `courses`, `medical` and `insurance`.
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
            REMARKS,
        ),
    )

    /**
     * Region is a part of the world: a continent, an ocean, a country, a sea.
     *
     * Absent so far: `parents` and the `children` worked out from them.
     */
    val REGION: ItemDescription = ItemDescription(
        "region",
        listOf(
            TextDescription("name"),
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
     * Absent so far: `buoyancy` and `maintenances`.
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
            REMARKS,
        ),
    )

    /** Every type, which is what an item set is built with. */
    val ALL: List<ItemDescription> = listOf(PERSON, REGION, GEAR)
}

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
