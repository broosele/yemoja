package yemoja.ui.gui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PropaneTank
import androidx.compose.material.icons.filled.ScubaDiving
import androidx.compose.material.icons.filled.Settings
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

    /** None, the tab holding no collection. */
    NONE,
}

/**
 * Tab is one subject the application is divided into.
 *
 * **A subject is not an item type.** A dive and the trip it was made on are one subject; a
 * region, a site and a wreck are one place. Two tabs hold nothing yet and are here so that the
 * list is the whole of what the application offers rather than the part that happens to be
 * built.
 */
internal class Tab(
    /** What the tab is called, which is what a user reads. */
    val name: String,
    /** Its glyph, beside the name. The platform's, until `GUI-3` gives it one of ours. */
    val icon: ImageVector,
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
    Tab(
        "Home", Icons.Filled.Home,
        owed = "a greeting, what needs attention, and everything counted",
    ),
    Tab("Dive", Icons.Filled.ScubaDiving, listOf(Types.DIVE, Types.DIVE_TRIP), Shape.DIVES),
    Tab("Gear", Icons.Filled.PropaneTank, listOf(Types.GEAR), Shape.GEAR),
    Tab(
        "Community", Icons.Filled.Groups,
        listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION), Shape.TYPES,
    ),
    Tab(
        "Location", Icons.Filled.Map,
        listOf(Types.REGION, Types.DIVE_SITE, Types.WRECK), Shape.PLACES,
    ),
    Tab(
        "System", Icons.Filled.Settings,
        owed = "settings, syncing and the rest of the machinery",
    ),
    Tab("Manuals", Icons.Filled.MenuBook, shape = Shape.MANUAL),
)
