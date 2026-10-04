package yemoja.ui.gui

import androidx.compose.material.icons.Icons
import yemoja.ui.icons.Calculate
import yemoja.ui.icons.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import yemoja.ui.icons.BarChart
import yemoja.ui.icons.Map
import yemoja.ui.icons.MenuBook
import yemoja.ui.icons.PropaneTank
import yemoja.ui.icons.ScubaDiving
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

    /** A table of dives, the trip cell spanning the consecutive dives on it. `DESK-7`. */
    DIVES,

    /** A tree of categories and the kinds within them. `GUI-21`. */
    GEAR,

    /** A subtab per type, each a plain list. */
    TYPES,

    /** A tree of regions, and what is at the chosen one. `GUI-20`, `GUI-22`. */
    PLACES,

    /** A tree of chapters and their sections, and the chosen chapter shown whole. `GUI-15`. */
    MANUAL,

    /** The greeting, what is owed, and a tile for every other tab. `GUI-30`. */
    HOME,

    /** A list of what can be worked out, and the form for the one chosen. `GUI-43`. */
    CALCULATIONS,

    /** Charts of the logbook's dives, chosen by name or put together. `GUI-30`. */
    STATISTICS,

    /** What can be done to a logbook as a whole, and the settings. `GUI-30`. */
    SYSTEM,
}

/**
 * Tab is one subject the application is divided into.
 *
 * **A subject is not an item type.** A dive and the trip it was made on are one subject; a
 * region and the sites in it are one place. What the application does to a logbook as a whole is
 * a tab of its own, System, and so are the charts of it, Statistics. `GUI-30`.
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
    /** What its tile on Home says it holds. */
    val holds: String,
    /** Whether it reads a logbook though it lists no type, as a chart of the dives does. */
    private val readsLogbook: Boolean = false,
) {

    /**
     * Whether the tab is about what a logbook holds, and so has nothing to offer without one.
     *
     * By whether it lists item types, which is the same question asked of the model rather than
     * of a list kept here: a tab that lists types needs items. Statistics lists none and counts
     * them all, so it says so. Home, Calculations, System and Manuals are what a window with no
     * logbook open still shows. `GUI-30`.
     */
    val needsLogbook: Boolean get() = types.isNotEmpty() || readsLogbook
}

/**
 * Every tab, in the order they are offered, which is also the order of Home's tiles.
 *
 * The order runs from what a diver came for to what the application needs of itself: the
 * logbook first, then what is worked out and counted from it, then the machinery, and last how
 * all of it works.
 */
internal val TABS: List<Tab> = listOf(
    Tab("Home", Icons.Filled.Home, shape = Shape.HOME, holds = "The greeting, and what is owed."),
    Tab(
        "Dives", Icons.Filled.ScubaDiving, listOf(Types.DIVE, Types.DIVE_TRIP), Shape.DIVES,
        "Every dive and trip, newest first, with its profile.",
    ),
    Tab(
        "Gear", Icons.Filled.PropaneTank, listOf(Types.GEAR), Shape.GEAR,
        "Your equipment, and when each piece is due a service.",
    ),
    Tab(
        "Community", Icons.Filled.Groups,
        listOf(Types.PERSON, Types.OPERATOR, Types.CERTIFICATION), Shape.TYPES,
        "Buddies, operators and certifications.",
    ),
    Tab(
        "Locations", Icons.Filled.Map,
        listOf(Types.REGION, Types.DIVE_SITE), Shape.PLACES,
        "Dive sites by region, on a map.",
    ),
    Tab(
        "Calculations", Icons.Filled.Calculate, shape = Shape.CALCULATIONS,
        holds = "Dive plans, gas mixes, SAC, NDL, MOD, EAD, END and tides.",
    ),
    Tab(
        "Statistics", Icons.Filled.BarChart, shape = Shape.STATISTICS,
        holds = "Charts of your diving.", readsLogbook = true,
    ),
    Tab(
        "System", Icons.Filled.Settings, shape = Shape.SYSTEM,
        holds = "Download from a dive computer, import, export, the logbook and settings.",
    ),
    Tab(
        "Manuals", Icons.Filled.MenuBook, shape = Shape.MANUAL,
        holds = "How Yemoja works, and how a logbook is written.",
    ),
)
