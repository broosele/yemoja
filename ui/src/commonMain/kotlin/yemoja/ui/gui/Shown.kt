package yemoja.ui.gui

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.OwnedItem
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Units

/*
 * What an item and its fields read as on a screen.
 *
 * Text only, and nothing about layout: these answer what a reader is told, which
 * ../../../../../../gui/doc.md makes one definition for both form factors.
 *
 * See ../../../../../../doc.md.
 */

/**
 * What a type is called on a screen.
 *
 * The same rule a field's label uses, applied to a type, which has none of its own: a
 * `dive_site` is a *Dive site*. Reading a name for a label is what the model does everywhere
 * else, so this is a gap in `ItemDescription` rather than a decision of this front end's.
 */
internal fun labelOf(type: ItemDescription): String =
    type.name.replace('_', ' ').replaceFirstChar { it.uppercase() }

/**
 * What titles [item], which is what a link to it reads as.
 *
 * **Never its id.** An id names a file and is what a reference points at, and the interface does
 * not surface one. Every type has a name and every one of them works it out where nothing was
 * written, so an item with nothing to be called is a logbook that could not have been made here
 * rather than a case to design for. It still says something rather than showing a blank row.
 */
internal fun titleOf(item: Item): String {
    val named = item.description["name"] ?: return UNNAMED
    val read = item.read(named.name)
    return (read as? Result.Usable)?.value?.toString()?.ifBlank { null } ?: UNNAMED
}

/** An item with nothing to be called, which nothing in a logbook made here should be. */
private const val UNNAMED = "(unnamed)"


/**
 * What one field says, or absent where it says nothing.
 *
 * **Absent and unusable are not the same silence**, and only one of them is silence: a field
 * nobody filled in has nothing to show, while a value that would not read has a reason, and the
 * reason is the useful part. `GUI-8` is how the difference is made visible; this is the text it
 * has to work with.
 */
internal fun shownOf(field: FieldDescription, item: Item): Shown? =
    when (val read = item.read(field.name)) {
        Result.Absent -> null
        is Result.Unusable -> Shown(field.label, read.reason, wrong = true)
        is Result.Usable -> Shown(
            field.label,
            said(field, read.value, item),
            worked = read.origin == Result.Origin.DERIVED,
        )
    }

/** One field as a reader is told it. */
internal class Shown(
    val label: String,
    val text: String,
    /** Whether the value would not read, and so what is shown is the reason rather than it. */
    val wrong: Boolean = false,
    /** Whether nobody wrote it and the model worked it out. An override counts as written. */
    val worked: Boolean = false,
)

/**
 * One value as text: a count for a series and for a collection, the entries for a list, and
 * otherwise what a file would write.
 *
 * A series and a keyed collection are counted rather than spelt out because neither belongs in a
 * line: five hundred depths and four profiles are both *how many*, and opening one is a separate
 * act. `GUI-16` leaves what an item view shows to the interface, and this is the interface's
 * answer for the shapes that do not fit.
 */
private fun said(field: FieldDescription, value: Any, within: Item): String = when {
    value is Series -> counted(value.size, "sample")
    field.cardinality == Cardinality.KEYED -> counted(keyedIn(value).size, "entry", "entries")
    field.cardinality == Cardinality.LIST -> listed(value, within)
    value is OwnedItem -> counted(filledIn(value), "field")
    else -> one(value, within) ?: field.format(value, Units.DEFAULT)
}

/**
 * A reference as the name of what it points at, or absent where it is not one.
 *
 * **An id is never shown**, and a reference written out is an id shown by the back door: a dive's
 * site read as `@shaab_el_erg_-_dolphin_house` tells a reader how the file is spelt rather than
 * where they were. A reference to nothing keeps what was written, which is the only case where
 * the spelling is the useful part.
 */
private fun one(value: Any, within: Item): String? {
    if (value !is Reference.Identified) return null
    return within.set[value.id]?.let { titleOf(it) }
}

/** As many entries as read comfortably, and how many did not fit. */
private fun shortened(entries: List<String>): String {
    if (entries.size <= MANY) return entries.joinToString(", ")
    val kept = entries.take(MANY).joinToString(", ")
    return "$kept, and ${entries.size - MANY} more"
}

/** How many of a list are worth spelling out before a count says more than the entries do. */
private const val MANY = 6

/** The entries of a list, or that somebody wrote a list with nothing in it. */
@Suppress("UNCHECKED_CAST")
private fun listed(value: Any, within: Item): String {
    val entries = (value as List<Element<Any>>).map {
        when (it) {
            is Element.Usable -> one(it.value, within) ?: it.value.toString()
            is Element.Unusable -> "!"
        }
    }
    return if (entries.isEmpty()) EMPTY else shortened(entries)
}

@Suppress("UNCHECKED_CAST")
private fun keyedIn(value: Any): Map<String, Element<Any>> =
    (value as? Map<String, Element<Any>>).orEmpty()

/** How many of an owned item's own fields say anything, which is all a line has room for. */
private fun filledIn(item: OwnedItem): Int =
    item.description.fields.count { item.read(it.name) != Result.Absent }

private fun counted(many: Int, one: String, several: String = one + "s"): String =
    "$many " + if (many == 1) one else several

/** A list somebody wrote with nothing in it, which is not a field nobody wrote. */
private const val EMPTY = "(empty)"
