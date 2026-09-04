package yemoja.logic

import yemoja.data.Cardinality
import yemoja.data.Dimension
import yemoja.data.ItemDescription
import yemoja.data.NumberDescription
import yemoja.data.Ordering
import yemoja.data.ReferenceDescription
import yemoja.data.TextDescription

/*
 * What a certification is.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Anything is allowed; these are the ones the supplied certifications use. */
private val CERTIFICATION_CATEGORIES =
    setOf("progression", "specialisation", "technical", "professional")

/**
 * Certification is a diving qualification.
 *
 * Absent so far: nothing of its own.
 */
internal val CERTIFICATION: ItemDescription = ItemDescription(
    "certification",
    listOf(
        TextDescription("name"),
        TextDescription("abbreviation"),
        // Who awards it, written as a plain name rather than as an item.
        TextDescription("organisation"),
        TextDescription("category", suggestedSet = CERTIFICATION_CATEGORIES),
        NumberDescription("max_depth", Dimension.LENGTH),
        ReferenceDescription(
            "supersedes",
            targetType = "certification",
            cardinality = Cardinality.LIST,
        ),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("name")),
)
