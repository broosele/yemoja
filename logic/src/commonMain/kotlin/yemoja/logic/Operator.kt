package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.ItemDescription
import yemoja.data.Ordering
import yemoja.data.ReferenceDescription
import yemoja.data.TextDescription
import yemoja.data.WholeNumberDescription

/*
 * What an operator is.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Anything is allowed; these are the ones the manual names. */
private val OPERATOR_CATEGORIES = setOf(
    "dive center", "hotel", "dive resort", "boat operator", "dive club", "liveaboard operator",
)

/**
 * Operator is anyone who takes a user diving or looks after their gear.
 *
 * Absent so far: nothing of its own.
 */
internal val OPERATOR: ItemDescription = ItemDescription(
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
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)
