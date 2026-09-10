package yemoja.ui.gui

import yemoja.data.ItemDescription
import yemoja.logic.Types

/*
 * What the application is divided into, which is subjects rather than item types.
 *
 * See ../../../../../../gui/doc.md — the tabs and what belongs in each are settled there, and
 * they are one definition for both form factors rather than a desktop layout.
 */

/**
 * Tab is one subject the application is divided into.
 *
 * **A subject is not an item type.** A dive and the trip it was made on are one subject; a
 * region, a site and a wreck are one place. Three tabs hold no items at all and are here so that
 * the list is the whole of what the application offers rather than the part that happens to be
 * built.
 */
internal class Tab(
    /** What the tab is called, which is what a user reads. */
    val name: String,
    /** The types it holds, in the order they are offered. Empty where it holds none yet. */
    val types: List<ItemDescription> = emptyList(),
    /** Why it is empty, where it is. Shown in place of a selector. */
    val owed: String? = null,
)

/**
 * Every tab, in the order they are offered.
 *
 * The order runs from what a diver came for to what the application needs of itself: the
 * logbook first, then what is counted from it, then the machinery.
 */
internal val TABS: List<Tab> = listOf(
    Tab("Home", owed = "a greeting, what needs attention, and a few figures worth seeing"),
    Tab("Dive", listOf(Types.DIVE, Types.DIVE_TRIP)),
    Tab("Gear", listOf(Types.GEAR)),
    Tab("Community", listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION)),
    Tab("Location", listOf(Types.REGION, Types.DIVE_SITE, Types.WRECK)),
    Tab("Statistics", owed = "everything counted and summarised"),
    Tab("System", owed = "settings, syncing and the rest of the machinery"),
    Tab("Manuals", owed = "the documentation, read inside the application"),
)
