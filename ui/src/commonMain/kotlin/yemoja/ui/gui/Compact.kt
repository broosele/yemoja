package yemoja.ui.gui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/*
 * A phone's screen, which shows one thing at a time where a desktop shows them side by side.
 *
 * See ../../../../../../gui/phone/doc.md — `PHONE-2`.
 */

/** Whether the screen is a phone's. A desktop and a tablet are not. `PHONE-3`. */
internal val LocalCompact = staticCompositionLocalOf { false }

/** How many fields stand side by side: one on a phone, `COLUMNS` everywhere else. */
@Composable
internal fun columns(): Int = if (LocalCompact.current) 1 else COLUMNS

/** Page is which of a tab's views a phone shows, the others waiting behind back. */
internal enum class Page {
    /** What the tab holds: the dive table, the gear tree, the people, the regions. */
    LIST,

    /** On Locations, the region chosen: its map, what is at it, and the region itself. */
    PLACE,

    /** One item, several dives' figures, or the form for a new item. */
    ITEM,
}

/** Which page a phone shows of [tab], given what [kept] has chosen. */
internal fun pageOf(tab: Tab, kept: Kept): Page = when {
    kept.making != null || kept.chosen != null || kept.chosenMany.size > 1 -> Page.ITEM
    tab.shape == Shape.PLACES && (kept.place != null || kept.unplaced) -> Page.PLACE
    else -> Page.LIST
}

/**
 * What back does on a phone, or absent where the page is as far back as the tab goes.
 *
 * One step at a time: a form is left before the item it was opened over, the item before the
 * list, on Locations what is at a region before the regions, in Manuals a chapter before the
 * chapters, in Calculations a calculation before the list of them, which keeps what was typed,
 * and on Home a review before Home.
 * A form left by back is cancelled, as its Cancel would.
 */
internal fun backOf(tab: Tab, kept: Kept): (() -> Unit)? = when {
    kept.making != null -> ({ kept.making = null })
    kept.editing != null -> ({ kept.editing = null })
    kept.chosenMany.size > 1 -> ({ kept.chosenMany = emptySet() })
    kept.chosen != null -> ({ kept.chosen = null })
    tab.shape == Shape.MANUAL && kept.inChapter -> ({ kept.inChapter = false })
    tab.shape == Shape.HOME && kept.inReview -> ({ kept.inReview = false })
    tab.shape == Shape.CALCULATIONS && kept.working.inForm -> ({ kept.working.inForm = false })
    tab.shape == Shape.PLACES && (kept.place != null || kept.unplaced) -> ({
        kept.place = null
        kept.unplaced = false
    })
    else -> null
}
