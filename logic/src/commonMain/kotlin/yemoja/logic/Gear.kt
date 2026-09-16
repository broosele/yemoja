package yemoja.logic

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription
import yemoja.data.WholeNumberDescription

/*
 * What a piece of gear is, with the buoyancy and the maintenances it owns.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

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

/** Anything is allowed; these are the ones the manual names. */
private val MAINTENANCE_TYPES =
    setOf("visual inspection", "repair", "service", "cleaning")

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
        // Against `valid_until` rather than `end_date`, and otherwise [INSURANCE]'s question.
        WholeNumberDescription("days_left", role = Role.Derived(::maintenancesDaysLeft)),
        BooleanDescription("expired", role = Role.Derived(::maintenancesExpired)),
        TextDescription("follow_up_type", suggestedSet = MAINTENANCE_TYPES),
        ReferenceDescription("operator", targetType = "operator"),
        REMARKS,
    ),
    proposedId = ::maintenancesProposedKey,
)

/** What an item nobody called generic is: one of the user's own. */
private val ownGear: Result<Any> = Result.Usable(false, Result.Origin.DERIVED)

private fun maintenancesDaysLeft(work: Item): Result<Any> = remaining(work, "valid_until")

private fun maintenancesExpired(work: Item): Result<Any> = passed(work, "valid_until")

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

/**
 * Gear is a piece of equipment. A dive computer is gear like anything else.
 *
 * Absent so far: nothing of their own.
 */
internal val GEAR: ItemDescription = ItemDescription(
    "gear",
    listOf(
        TextDescription("name"),
        TextDescription("brand"),
        TextDescription("model"),
        TextDescription("serial"),
        // For a dive computer that asks for a code before it talks: the key it hands back once
        // the code is typed, kept so it is not asked again. Put there by a download. `LOGIC-24`.
        TextDescription("access_code", housekeeping = true),
        // Whether this describes a kind of item rather than one the user owns. Your own gear is
        // your own, so nothing written works out false. A default is a constant computation
        // rather than a field of its own. `DATA-56`.
        BooleanDescription("generic", role = Role.Overrideable { ownGear }),
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
        OwnedItemDescription("buoyancy", BUOYANCY),
        OwnedItemDescription("maintenances", MAINTENANCE, cardinality = Cardinality.KEYED),
        // Every dive that took this item, named on the dive's gear or, for a dive computer, by
        // a profile recorded on it. Never written: each dive says what it took. For a generic
        // item this is every dive that listed the kind.
        ReferenceDescription(
            "dives",
            targetType = "dive",
            cardinality = Cardinality.LIST,
            role = Role.Derived(::gearsDives),
        ),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)

/** The dives whose gear names [gear] among its items, or a profile of which names it. */
private fun gearsDives(gear: Item): Result<Any> = pointingAt(
    gear,
    Types.DIVE,
    Naming("items", inside = "gear"),
    Naming("dive_computer", inside = "profiles"),
)
