package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.TextDescription

/*
 * What a wreck is.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Anything is allowed; these are the ones the manual names. */
private val SHIP_TYPES = setOf("freighter", "tanker", "warship", "hospital ship")

/**
 * Wreck is a ship lying at a site.
 *
 * Absent so far: nothing of its own.
 */
internal val WRECK: ItemDescription = ItemDescription(
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
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)
