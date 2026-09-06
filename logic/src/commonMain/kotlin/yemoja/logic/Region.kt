package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Dimension
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.TextDescription

/*
 * What a region is, and how the regions inside one are found.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Anything is allowed; these are the ones the supplied regions use. */
private val REGION_CATEGORIES = setOf("world", "continent", "ocean", "sea", "country", "area")

/**
 * Region is a part of the world: a continent, an ocean, a country, a sea.
 *
 * Absent so far: nothing of its own.
 */
internal val REGION: ItemDescription = ItemDescription(
    "region",
    listOf(
        TextDescription("name"),
        TextDescription("category", suggestedSet = REGION_CATEGORIES),
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
        // The four edges of a box holding the region, for placing it on a map. `east` is the
        // edge reached travelling east from `west`, which is what makes the date line
        // unremarkable: the Pacific runs from 120 to -70. Latitude does not wrap.
        NumberDescription("west", Dimension.ANGLE, range = LONGITUDE),
        NumberDescription("east", Dimension.ANGLE, range = LONGITUDE),
        NumberDescription("south", Dimension.ANGLE, range = LATITUDE),
        NumberDescription("north", Dimension.ANGLE, range = LATITUDE),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)

/**
 * The regions naming [region] as a parent.
 *
 * Gathered from every region in the set, the supplied ones included, so a country added to a
 * logbook appears among the children of a continent nothing touched. Empty rather than absent:
 * a region with nothing inside it has been asked and answered.
 */
private fun regionsChildren(region: Item): Result<Any> =
    pointingAt(region, Types.REGION, "parents")
