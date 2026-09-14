package yemoja.ui.gui

import yemoja.data.Cardinality
import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.KeyReference
import yemoja.data.KeyReferenceDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.TextDescription
import yemoja.data.Units
import kotlin.math.abs
import kotlin.math.roundToLong

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
 * The fields of [type] an item view shows: all of them but the ones the screen around it
 * already shows, and, unless [editing], but the ones the type keeps for the machinery or solely
 * as a source for others. `GUI-16`, `DATA-115`.
 *
 * A region's children are its branch of the tree beside it, and a list of them under the map
 * says the same thing again in worse form.
 */
internal fun fieldsShownOf(
    type: ItemDescription,
    editing: Boolean = false,
): List<FieldDescription> =
    type.fields.filter {
        it.name !in ALREADY_SHOWN[type.name].orEmpty() &&
            (editing || (it.name !in type.housekeeping && it.name !in type.sources))
    }

/**
 * By type name, the fields the screen around an item view shows already: a region's children
 * are the tree beside it, and a trip's dives are the box of their statistics under it.
 */
private val ALREADY_SHOWN: Map<String, Set<String>> = mapOf(
    "region" to setOf("children"),
    "dive_trip" to setOf("dives"),
)

/**
 * Arranged is how an item view lays a type out: the plain fields, which flow into columns, and
 * then the owned items, each an inset of its own, in the type's order. `GUI-16`.
 */
internal class Arranged(val plain: List<FieldDescription>, val insets: List<OwnedItemDescription>)

/**
 * The fields of [type] as an item view lays them out, or as the edit form does.
 *
 * A series is not laid out at all: the graph is where it is read, and a count of its samples
 * beside the graph says nothing a reader wants.
 */
internal fun arrangedOf(type: ItemDescription, editing: Boolean = false): Arranged {
    val shown = fieldsShownOf(type, editing).filter { it.cardinality !in SERIES }
    return Arranged(
        plain = shown.filter { it !is OwnedItemDescription },
        insets = shown.filterIsInstance<OwnedItemDescription>(),
    )
}

/** The two shapes a series comes in. */
private val SERIES = setOf(Cardinality.SERIES, Cardinality.KEYED_SERIES)

/**
 * The entry of the keyed collection [name] that [item] points at by a key reference, as its
 * index among the entries, or absent where nothing points: a dive's primary recording, marked
 * on its tab.
 */
internal fun pointedEntryOf(item: Item, name: String): Int? {
    val pointer = item.description.fields
        .filterIsInstance<KeyReferenceDescription>().firstOrNull { it.collection == name }
        ?: return null
    val key = ((item.read(pointer.name) as? Result.Usable)?.value as? KeyReference)?.key
        ?: return null
    val keys = ((item.read(name) as? Result.Usable)?.value as? Map<*, *>)?.keys?.toList()
        ?: return null
    return keys.indexOf(key).takeIf { it >= 0 }
}

/**
 * What a tab in a keyed inset is called: the entry's name where it has one, else what the first
 * reference on it points at, else its key.
 *
 * A recording has no name and its key is a spelling, but it names the computer that made it,
 * which is what a reader calls it; a course likewise names its certification. The key is what
 * is left when nothing on the entry says anything, which is the recorded gap for key references.
 */
internal fun entryLabelOf(key: String, entry: Item): String {
    if (entry.description["name"] != null) {
        val title = titleOf(entry)
        if (title != UNNAMED) return title
    }
    for (field in entry.description.fields) {
        if (field.cardinality != Cardinality.SINGLE) continue
        val read = (entry.read(field.name) as? Result.Usable)?.value as? Reference.Identified
        val target = read?.let { entry.set[it.id] } ?: continue
        return titleOf(target)
    }
    return prettyOf(key)
}

/**
 * A key as a screen shows it: the underscores as spaces and the first letter up, so `tank_1`
 * reads *Tank 1*.
 *
 * A key is what a file spells an entry by, and the one thing an interface has to show that is
 * neither a name nor an id; reading it the way a field's label is read is as far from the
 * spelling as it can honestly get. `GUI-28`.
 */
internal fun prettyOf(key: String): String =
    key.replace('_', ' ').replaceFirstChar { it.uppercase() }

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
        is Result.Unusable -> Shown(field.label, listOf(Part(read.reason)), wrong = true)
        is Result.Usable -> Shown(
            field.label,
            said(field, read.value, item),
            worked = read.origin == Result.Origin.DERIVED,
            rating = ratingOf(field, read.value),
        )
    }

/** A rating out of ten, where [field] is one and [value] reads as a whole number. */
private fun ratingOf(field: FieldDescription, value: Any): Int? =
    if (field.name == RATING) (value as? Number)?.toInt() else null

/** The one field that is a rating, on a dive, a site and an operator alike, out of ten. */
private const val RATING = "rating"

/** Star is one of five, and how much of it is filled. */
internal enum class Star { FULL, HALF, EMPTY }

/**
 * A rating out of ten as five stars, two points to a star, so an odd rating ends in a half.
 *
 * Below the range a rating is written in reads as no stars, and above it as all five, rather
 * than as a fault. `GUI-16`.
 */
internal fun starsOf(rating: Int): List<Star> = (1..STARS).map { star ->
    when {
        rating >= 2 * star -> Star.FULL
        rating == 2 * star - 1 -> Star.HALF
        else -> Star.EMPTY
    }
}

/** How many stars a rating is shown as. */
internal const val STARS = 5

/**
 * One field as a reader is told it: a label, and what it says in parts, of which the ones that
 * name another item lead to it.
 */
internal class Shown(
    val label: String,
    val parts: List<Part>,
    /** Whether the value would not read, and so what is shown is the reason rather than it. */
    val wrong: Boolean = false,
    /** Whether nobody wrote it and the model worked it out. An override counts as written. */
    val worked: Boolean = false,
    /** Out of ten, where the field is a rating; shown as stars in place of what it says. */
    val rating: Int? = null,
) {
    /** What it says, read straight through. */
    val text: String get() = parts.joinToString("") { it.text }
}

/**
 * Part is a run of what a field says, and where it leads.
 *
 * A reference reads as the name of what it points at and leads there; everything else reads as
 * text and leads nowhere. `GUI-28`.
 */
internal class Part(val text: String, val leadsTo: String? = null)

/**
 * One value as parts: a count for a series and for a collection, the entries for a list, and
 * otherwise what a file would write.
 *
 * A series and a keyed collection are counted rather than spelt out because neither belongs in a
 * line: five hundred depths and four profiles are both *how many*, and opening one is a separate
 * act. `GUI-16` leaves what an item view shows to the interface, and this is the interface's
 * answer for the shapes that do not fit.
 */
private fun said(field: FieldDescription, value: Any, within: Item): List<Part> = when {
    value is Series -> plain(counted(value.size, "sample"))
    field.cardinality == Cardinality.KEYED -> {
        plain(counted(keyedIn(value).size, "entry", "entries"))
    }
    field.cardinality == Cardinality.LIST -> listed(value, within)
    value is OwnedItem -> plain(counted(filledIn(value), "field"))
    value is KeyReference -> plain(prettyOf(value.key))
    else -> one(value, within)?.let { listOf(it) } ?: plain(displayOf(field, value))
}

/**
 * A number as a screen shows it: a time as minutes and seconds, and the rest to as many decimals
 * as a reader wants, which is fewer than a file keeps.
 *
 * A file keeps three decimals of a metre so nothing measured is lost; a reader wants one. A time
 * is seconds in the model and `61:16` on a screen, minutes and seconds however long, since a
 * dive is quoted in minutes. A dimension not listed, an angle among them, reads as the file
 * writes it. `GUI-16`.
 */
internal fun displayOf(field: FieldDescription, value: Any): String {
    val number = numberOf(field, value)
    val unit = unitOf(field)
    return if (unit.isEmpty()) number else "$number $unit"
}

/** As [displayOf], without the unit, for where the unit is said once for several numbers. */
internal fun numberOf(field: FieldDescription, value: Any): String {
    if (field !is NumberDescription) return wordOf(field, value)
    val number = (value as? Number)?.toDouble() ?: return field.format(value, Units.DEFAULT)
    if (field.dimension == Dimension.TIME) return clockOf(number)
    val decimals = DECIMALS[field.dimension] ?: return field.format(value, Units.DEFAULT)
    return rounded(number, decimals)
}

/**
 * A value that is not a number, as a screen reads it.
 *
 * **A word from a vocabulary is a word rather than a name.** A file writes one the way a file
 * writes anything, in lower case and with an underscore where a reader puts a space, and a
 * screen reads it back as what it says: `back_mounted` is *Back mounted*. Only a field with a
 * set of words to draw on has words in it, so free text is left exactly as it was typed, and a
 * gas is `EAN32` rather than `Ean32` because a mix is drawn from no set. `GUI-16`.
 */
private fun wordOf(field: FieldDescription, value: Any): String {
    val written = field.format(value, Units.DEFAULT)
    val drawn = field is TextDescription && (field.fixedSet != null || field.suggestedSet != null)
    return if (drawn) prettyOf(written) else written
}

/** The unit a screen writes after a number of [field], or nothing where it has none to write. */
internal fun unitOf(field: FieldDescription): String =
    if (field is NumberDescription) SYMBOLS[field.dimension].orEmpty() else ""

/** What each dimension is written in on a screen; a time is written as a clock instead. */
private val SYMBOLS: Map<Dimension, String> = mapOf(
    Dimension.LENGTH to "m",
    Dimension.TEMPERATURE to "°C",
    Dimension.MASS to "kg",
    Dimension.VOLUME to "l",
    Dimension.PRESSURE to "bar",
    Dimension.DENSITY to "kg/m³",
    Dimension.ANGLE to "°",
)

/** Seconds as minutes and seconds, `61:16`, a sign in front where they are negative. */
internal fun clockOf(seconds: Double): String {
    val whole = seconds.roundToLong()
    val sign = if (whole < 0) "-" else ""
    val total = abs(whole)
    return sign + (total / 60) + ":" + (total % 60).toString().padStart(2, '0')
}

/** [number] to at most [decimals] places, trailing zeros dropped. */
private fun rounded(number: Double, decimals: Int): String {
    val text = "%.${decimals}f".format(number)
    return if ('.' in text) text.trimEnd('0').trimEnd('.') else text
}

/** How many decimals a screen shows of each dimension, where fewer than the file's are wanted. */
private val DECIMALS: Map<Dimension, Int> = mapOf(
    Dimension.LENGTH to 1,
    Dimension.TEMPERATURE to 1,
    Dimension.MASS to 1,
    Dimension.VOLUME to 1,
    Dimension.PRESSURE to 0,
    Dimension.DENSITY to 0,
)

private fun plain(text: String): List<Part> = listOf(Part(text))

/**
 * A reference as the name of what it points at, leading there, or absent where it is not one.
 *
 * **An id is never shown**, and a reference written out is an id shown by the back door: a dive's
 * site read as `@shaab_el_erg_-_dolphin_house` tells a reader how the file is spelt rather than
 * where they were. A reference to nothing keeps what was written and leads nowhere, which is the
 * only case where the spelling is the useful part.
 */
private fun one(value: Any, within: Item): Part? {
    if (value !is Reference.Identified) return null
    val item = within.set[value.id] ?: return null
    return Part(titleOf(item), leadsTo = value.id)
}

/**
 * The entries of a list, every one, with a comma between.
 *
 * All of them rather than the first few and a count: an entry is a link, and a link folded
 * into *and twelve more* leads nowhere. A long list wraps, which is what a field does.
 */
internal fun joined(entries: List<Part>): List<Part> {
    val parts = ArrayList<Part>()
    for ((index, entry) in entries.withIndex()) {
        if (index > 0) parts += Part(", ")
        parts += entry
    }
    return parts
}

/** The entries of a list, or that somebody wrote a list with nothing in it. */
@Suppress("UNCHECKED_CAST")
private fun listed(value: Any, within: Item): List<Part> {
    val entries = (value as List<Element<Any>>).map {
        when (it) {
            is Element.Usable -> one(it.value, within) ?: Part(it.value.toString())
            is Element.Unusable -> Part("!")
        }
    }
    return if (entries.isEmpty()) plain(EMPTY) else joined(entries)
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
