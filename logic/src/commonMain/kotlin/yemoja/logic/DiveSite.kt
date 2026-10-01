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
import yemoja.data.WholeNumberDescription

/*
 * What a dive site is.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** Closed: what kind of place a site is. */
private val ENVIRONMENT_TYPES = setOf(
    "ocean", "sea", "lake", "river", "quarry", "spring", "cave", "cavern", "under ice", "pool",
    "hyperbaric chamber",
)

/**
 * DiveSite is a place dived at.
 *
 * Absent so far: nothing of its own.
 */
/**
 * How a diver crosses the waterline here.
 *
 * Suggested rather than fixed. A closed list would have to be right the first time and this one
 * is not closeable — ice, a marina ladder, a helicopter. `DATA-123`.
 */
private val ENTRIES = setOf(
    "shore",
    "jetty",
    "steps",
    "rope",
    "boat",
    "poolside",
)

private val FACILITIES = setOf(
    "parking",
    "filling station",
    "nitrox",
    "trimix",
    "300bar",
    "toilets",
    "showers",
    "changing rooms",
    "rinse tanks",
    "gear rental",
    "food",
)

internal val DIVE_SITE: ItemDescription = ItemDescription(
    "dive_site",
    listOf(
        TextDescription("name"),
        TextDescription("alternative_names", cardinality = Cardinality.LIST),
        ReferenceDescription("regions", targetType = "region", cardinality = Cardinality.LIST),
        TextDescription("environment_type", fixedSet = ENVIRONMENT_TYPES),
        TextDescription("water_type", fixedSet = WATER_TYPES),
        TextDescription("entry", suggestedSet = ENTRIES),
        // The site's own depth, not how deep anybody went.
        NumberDescription("max_depth", Dimension.LENGTH),
        WholeNumberDescription("rating", range = 1..10),
        // A description rather than a classification, so no list to choose from.
        TextDescription("substrate"),
        TextDescription("facilities", cardinality = Cardinality.LIST, suggestedSet = FACILITIES),
        // The height of the water above sea level, which changes how a dive is worked out.
        NumberDescription("elevation", Dimension.LENGTH),
        NumberDescription("longitude", Dimension.ANGLE, range = LONGITUDE),
        NumberDescription("latitude", Dimension.ANGLE, range = LATITUDE),
        // Every dive naming this site. Never written: each dive says where it was.
        ReferenceDescription(
            "dives",
            targetType = "dive",
            cardinality = Cardinality.LIST,
            role = Role.Derived(::sitesDives),
        ),
        REMARKS,
    ),
    orderedBy = listOf(Ordering("name")),
    proposedId = ::namedById,
)

/** The dives naming [site], the ones not yet made left out. */
private fun sitesDives(site: Item): Result<Any> = divesPointingAt(site, Naming("dive_site"))
