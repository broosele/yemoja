package yemoja.ui.gui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.ScubaDiving
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Four of the six are not a list, which is why the question had to be asked per tab rather
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

    /** A tree of chapters and their sections, and the chosen chapter shown whole. `GUI-15`. */
    MANUAL,

    /** The greeting, what the application can be asked to do, and a plot. `GUI-30`. */
    HOME,

    /** A list of what can be worked out, and the form for the one chosen. `GUI-43`. */
    CALCULATIONS,
}

/**
 * Tab is one subject the application is divided into.
 *
 * **A subject is not an item type.** A dive and the trip it was made on are one subject; a
 * region, a site and a wreck are one place. What the application does to a logbook as a whole
 * is not a subject at all and is not a tab: it sits under the greeting on Home, where a reader
 * meets it before they have chosen anything. `GUI-30`.
 */
internal class Tab(
    /** What the tab is called, which is what a user reads. */
    val name: String,
    /** Its glyph, beside the name. The platform's, until `GUI-3` gives it one of ours. */
    val icon: ImageVector,
    /** The types it holds, in the order they are offered. Empty where it holds none yet. */
    val types: List<ItemDescription> = emptyList(),
    /** What its selector is. */
    val shape: Shape,
) {

    /**
     * Whether the tab is about what a logbook holds, and so has nothing to offer without one.
     *
     * By whether it lists item types, which is the same question asked of the model rather than
     * of a list kept here: a tab that lists types needs items. Home and Manuals list none, and
     * are what a window with no logbook open still shows. `GUI-30`.
     */
    val needsLogbook: Boolean get() = types.isNotEmpty()
}

/**
 * Every tab, in the order they are offered.
 *
 * The order runs from what a diver came for to what the application needs of itself: the
 * logbook first, then what is counted from it, then the machinery.
 */
internal val TABS: List<Tab> = listOf(
    Tab("Home", Icons.Filled.Home, shape = Shape.HOME),
    Tab("Dives", Icons.Filled.ScubaDiving, listOf(Types.DIVE, Types.DIVE_TRIP), Shape.DIVES),
    Tab("Gear", Icons.Filled.PropaneTank, listOf(Types.GEAR), Shape.GEAR),
    Tab(
        "Community", Icons.Filled.Groups,
        listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION), Shape.TYPES,
    ),
    Tab(
        "Locations", Icons.Filled.Map,
        listOf(Types.REGION, Types.DIVE_SITE, Types.WRECK), Shape.PLACES,
    ),
    Tab("Calculations", Icons.Filled.Calculate, shape = Shape.CALCULATIONS),
    Tab("Manuals", Icons.Filled.MenuBook, shape = Shape.MANUAL),
)
