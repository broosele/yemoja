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
 * Shape is what a tab's selector is, which `GUI-14` settled is decided per tab.
 *
 * Three of the five are not a list, which is why the question had to be asked per tab rather
 * than answered once for all of them.
 */
internal enum class Shape {

    /** A table of dives, the trip cell spanning the consecutive dives on it. `GUI-19`. */
    DIVES,

    /** A tree of categories and the kinds within them. `GUI-21`. */
    GEAR,

    /** A subtab per type, each a plain list. */
    TYPES,

    /** A tree of regions, and what is at the chosen one. `GUI-20`, `GUI-22`. */
    PLACES,

    /** None, the tab holding no collection. */
    NONE,
}

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
    /** What its selector is. */
    val shape: Shape = Shape.NONE,
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
    Tab("Home", owed = "a greeting, what needs attention, and everything counted"),
    Tab("Dive", listOf(Types.DIVE, Types.DIVE_TRIP), Shape.DIVES),
    Tab("Gear", listOf(Types.GEAR), Shape.GEAR),
    Tab("Community", listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION), Shape.TYPES),
    Tab("Location", listOf(Types.REGION, Types.DIVE_SITE, Types.WRECK), Shape.PLACES),
    Tab("System", owed = "settings, syncing and the rest of the machinery"),
    Tab("Manuals", owed = "the documentation, read inside the application"),
)
