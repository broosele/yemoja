package yemoja.ui.gui

import yemoja.data.Cardinality
import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.KeyReference
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItem
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Role
import yemoja.data.Section
import yemoja.data.Series
import yemoja.data.TextDescription
import yemoja.data.Units
import yemoja.logic.Types
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
            (editing || (!it.housekeeping && !it.source)) &&
            // The card is titled with the name, so a card saying it again says it twice. A form
            // keeps it where it can be typed: a site is renamed by writing in that box. `GUI-16`.
            (it.name != NAME || (editing && it.role !is Role.Derived))
    }

/** The field every card is titled by, which [titleOf] reads. */
private const val NAME = "name"

/**
 * Whether [field] is worth offering on [item], or belongs under the fold.
 *
 * **A field that only means something for one kind of thing is folded away on the rest.** Gear
 * is one type whatever the item is — the data layer settles that under *Types are not
 * subdivided* — and a drysuit therefore has an access code and a salt density, both of which are
 * a dive computer's. Offering them is how the form reads as though nobody thought about it.
 *
 * **Folded, never hidden.** What decides is the item's own `category`, which is free text a
 * reader typed: a cylinder filed under the wrong word would lose its capacity for good if this
 * took fields away rather than tucking them out of the way. So everything stays reachable and
 * the fold is the only thing that moves. The data layer's refusal was about validation, not
 * about what a screen offers, which is `GUI-16`'s to decide. `GUI-29`.
 *
 * [saying] is the category as a form says it, which is what an item being typed goes by: a new
 * cylinder holds nothing yet, and its capacity belongs in front of the reader the moment the word
 * is in the box rather than after it is saved.
 */
internal fun forwardOf(field: FieldDescription, item: Item, saying: String? = null): Boolean {
    val only = ONLY_FOR[item.description.name]?.get(field.name) ?: return true
    val stored = (item.single<String>("category") as? Result.Usable)?.value
    return (saying ?: stored)?.lowercase() in only
}

/**
 * The items a form offers for [field] of an item of [holder]: every item of the type it points
 * at, narrowed where the field wants one category of them.
 *
 * A gas source's `cylinder` offers the gear in the `cylinder` category and no other: its volume is
 * taken from the cylinder's capacity, and a regulator named there is refused as a cylinder, so
 * offering one offers a mistake. What is already written is not narrowed away, only what is
 * offered. `GUI-29`.
 */
internal fun candidatesOf(
    set: ItemSet,
    holder: ItemDescription,
    field: ReferenceDescription,
): List<Chosen> {
    val target = Types.ALL.firstOrNull { it.name == field.targetType } ?: return emptyList()
    val every = entriesOf(set, target)
    val wanted = CHOSEN_FROM[holder.name]?.get(field.name) ?: return every
    return every.filter { chosen ->
        val category = (chosen.item.single<String>("category") as? Result.Usable)?.value
        category.equals(wanted, ignoreCase = true)
    }
}

/** By type and field, the one category of gear a reference is chosen from. */
private val CHOSEN_FROM: Map<String, Map<String, String>> = mapOf(
    "gas_source" to mapOf("cylinder" to "cylinder"),
)

/**
 * By type, the fields that belong to one category of item and to no other.
 *
 * Only gear needs this, being the one type covering things as unalike as a computer and a suit.
 * A category not named here keeps the field folded, which is the safe way round: a reader who
 * has not said what an item is has not said the field applies either.
 */
private val ONLY_FOR: Map<String, Map<String, Set<String>>> = mapOf(
    "gear" to mapOf(
        "serial" to setOf("instruments"),
        "access_code" to setOf("instruments"),
        "salt_density" to setOf("instruments"),
        "capacity" to setOf("cylinder"),
    ),
)

/**
 * By type name, the fields the screen around an item view shows already: a region's children
 * are the tree beside it, and a trip's dives are the box of their statistics under it.
 */
private val ALREADY_SHOWN: Map<String, Set<String>> = mapOf(
    "region" to setOf("children"),
    "dive_trip" to setOf("dives"),
)

/**
 * By type name, the fields an item view sets in a box of their own under everything else, the
 * box as wide as the card.
 *
 * A list of dives runs to hundreds of names, and in one of two columns it is a narrow ribbon
 * pushing every field after it off the screen. `GUI-16`.
 */
private val AT_FOOT: Map<String, Set<String>> = mapOf(
    "gear" to setOf("dives"),
    "person" to setOf("dives"),
    "dive_site" to setOf("dives"),
    "operator" to setOf("dives"),
)

/**
 * Arranged is how an item view lays a type out: the plain fields, which flow into columns, then
 * the owned items, each an inset of its own, in the type's order, and last the fields at its foot.
 * `GUI-16`.
 */
internal class Arranged(
    val plain: List<FieldDescription>,
    val insets: List<OwnedItemDescription>,
    val foot: List<FieldDescription> = emptyList(),
    /** The type's sections, each with the fields it gathers, in the order they are shown. */
    val sections: List<Sectioned> = emptyList(),
) {
    /** Every field that is not an inset or at the foot, sections included, in their order. */
    val flowing: List<FieldDescription> get() = plain + sections.flatMap { it.fields }
}

/**
 * [held] in rows of [columns], except that one [wide] takes a row of its own.
 *
 * A paragraph of remarks, or a list of buddies stacked one box to a line, is as tall as the four
 * fields beside it and leaves a hole where three of them would have been. Given the row it fills
 * the hole goes, and the fields after it line up again. `GUI-16`.
 */
internal fun <T> rowsOf(held: List<T>, columns: Int = COLUMNS, wide: (T) -> Boolean): List<List<T>> {
    val rows = ArrayList<List<T>>()
    var row = ArrayList<T>()
    for (one in held) {
        if (wide(one)) {
            if (row.isNotEmpty()) {
                rows += row
                row = ArrayList()
            }
            rows += listOf(one)
            continue
        }
        row += one
        if (row.size == columns) {
            rows += row
            row = ArrayList()
        }
    }
    if (row.isNotEmpty()) rows += row
    return rows
}

/** One section of a type, with the fields it gathers in hand. */
internal class Sectioned(val section: Section, val fields: List<FieldDescription>)

/**
 * The fields of [type] as an item view lays them out, or as the edit form does.
 *
 * A series is not laid out at all: the graph is where it is read, and a count of its samples
 * beside the graph says nothing a reader wants. The form has no foot, a field there being one
 * nobody types into.
 */
internal fun arrangedOf(type: ItemDescription, editing: Boolean = false): Arranged {
    val shown = fieldsShownOf(type, editing).filter { it.cardinality !in SERIES }
    val footed = if (editing) emptySet() else AT_FOOT[type.name].orEmpty()
    val sections = type.sections.map { section ->
        Sectioned(section, section.fields.mapNotNull { name -> shown.firstOrNull { it.name == name } })
    }.filter { it.fields.isNotEmpty() }
    val sectioned = sections.flatMap { it.fields }.toSet()
    return Arranged(
        plain = shown.filter { it !is OwnedItemDescription && it.name !in footed && it !in sectioned },
        insets = shown.filterIsInstance<OwnedItemDescription>(),
        foot = shown.filter { it.name in footed },
        sections = sections,
    )
}

/** The two shapes a series comes in. */
private val SERIES = setOf(Cardinality.SERIES, Cardinality.KEYED_SERIES)

/**
 * The entries of the keyed collection [name] of [item], the one it points at by key first.
 *
 * A dive's primary recording is the one every figure on it comes from, so it is the tab a
 * reader meets first; the rest keep the order they are held in. **The collection itself is not
 * reordered.** What is stored is the order the source held them in, and this is a reading of
 * it: putting one first on a screen must not rewrite a file. `GUI-16`.
 */
internal fun shownEntriesOf(item: Item, name: String): List<Pair<String, OwnedItem>> {
    val entries = keyedEntriesOf(item, name)
    val key = pointedKeyOf(item, name) ?: return entries
    val first = entries.firstOrNull { it.first == key } ?: return entries
    return listOf(first) + entries.filterNot { it.first == key }
}

/**
 * The entry of the keyed collection [name] that [item] points at by a key reference, as its
 * index among the entries as [shownEntriesOf] gives them, or absent where nothing points.
 *
 * Nought wherever there is one, the pointed-at entry being first, and this rather than the
 * constant so that the marking and the ordering cannot come apart.
 */
internal fun pointedEntryOf(item: Item, name: String): Int? {
    val key = pointedKeyOf(item, name) ?: return null
    return shownEntriesOf(item, name).indexOfFirst { it.first == key }.takeIf { it >= 0 }
}

/** Which entry of [name] the item points at by key reference, where it has one to point with. */
private fun pointedKeyOf(item: Item, name: String): String? {
    val pointer = item.description.fields
        .filterIsInstance<KeyReferenceDescription>().firstOrNull { it.collection == name }
        ?: return null
    return ((item.read(pointer.name) as? Result.Usable)?.value as? KeyReference)?.key
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
        return briefOf(target)
    }
    return prettyOf(key)
}

/**
 * What an item is called where there is room for a word and not a name.
 *
 * Its `abbreviation` where it has one, which is the field's whole purpose: *the short form it
 * is usually known by*. A tab is the shortest place an interface has, and a row of them reading
 * *Advanced Open Water Diver* is a row nobody can see the ends of. `GUI-16`.
 *
 * **The first letter is raised and the rest is left alone.** A label begins with a capital, and
 * the rest of an abbreviation is not a label's to touch: a specialty written *nitrox* reads
 * *Nitrox* beside the others, and `OW` stays `OW` rather than becoming `Ow`.
 */
internal fun briefOf(item: Item): String {
    if (item.description["abbreviation"] == null) return titleOf(item)
    val short = (item.single<String>("abbreviation") as? Result.Usable)?.value?.ifBlank { null }
    return short?.replaceFirstChar { it.uppercase() } ?: titleOf(item)
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
 * What [fields] say, with a start and an end of one kind shown as a range on one line.
 *
 * A dive says `Time 09:15 – 09:58` rather than a start time and an end time under each other:
 * the two are one fact, and a reader takes in the span without subtracting. `GUI-47`.
 *
 * The pair has to be whole and of one kind. Where only one end is written, or the two are
 * described differently, each says itself under its own label, because a lone `2026-02-23` beside
 * *Date* would not say which end it was.
 */
internal fun shownAllOf(fields: List<FieldDescription>, item: Item): List<Shown> {
    val named = fields.associateBy { it.name }
    val ends = HashSet<String>()
    val out = ArrayList<Shown>()
    for (field in fields) {
        if (field.name in ends) continue
        val end = rangedWith(field, named)
        val ranged = end?.let { rangeOf(field, it, item) }
        if (ranged != null && end != null) {
            ends += end.name
            out += ranged
        } else {
            shownOf(field, item)?.let { out += it }
        }
    }
    return out
}

/** The field that ends what [field] starts, among [named], or absent where there is none. */
private fun rangedWith(field: FieldDescription, named: Map<String, FieldDescription>): FieldDescription? {
    val stem = field.name.removePrefix("start_")
    if (stem == field.name) return null
    val end = named["end_$stem"] ?: return null
    return end.takeIf { it::class == field::class && it.cardinality == field.cardinality }
}

/** [start] and [end] on one line, or absent where either says nothing. */
private fun rangeOf(start: FieldDescription, end: FieldDescription, item: Item): Shown? {
    val from = shownOf(start, item) ?: return null
    val to = shownOf(end, item) ?: return null
    if (from.wrong || to.wrong) return null
    return Shown(
        label = prettyOf(start.name.removePrefix("start_")),
        parts = from.parts + Part(RANGE) + to.parts,
        worked = from.worked && to.worked,
        // Either end corrected marks the range, one written value being what a reader is
        // looking for on a line the model would otherwise have filled in on its own.
        overridden = from.overridden || to.overridden,
    )
}

/** What sits between the two ends of a range. */
private const val RANGE = " \u2013 "

/**
 * What one field says, or absent where it says nothing.
 *
 * **Absent and unusable are not the same silence**, and only one of them is silence: a field
 * nobody filled in has nothing to show, while a value that would not read has a reason, and the
 * reason is the useful part. `GUI-8` is how the difference is made visible; this is the text it
 * has to work with.
 *
 * **A list with nothing in it says nothing too.** A worked-out list always answers, so a region
 * no site names has an empty list of sites rather than none, and showing it as *(empty)* put a
 * line on every such card that an absent field would never have. A stored empty list is kept in
 * the file as something written, and is not worth a line on the screen either. `GUI-16`.
 */
internal fun shownOf(field: FieldDescription, item: Item): Shown? =
    shownOf(field, item.read(field.name), item)

/**
 * [read] as [field] of [item] says it, for a caller that has the value in hand.
 *
 * A form uses it to show what a field will say once a correction it is dropping is gone: the
 * logbook still holds that correction until Save, so reading the item would give it back.
 */
internal fun shownOf(field: FieldDescription, read: Result<Any>, item: Item): Shown? =
    when (read) {
        Result.Absent -> null
        is Result.Unusable -> Shown(field.label, listOf(Part(read.reason)), wrong = true)
        is Result.Usable -> if ((read.value as? List<*>)?.isEmpty() == true) null else Shown(
            labelOf(field, read),
            said(field, read.value, item),
            worked = read.origin == Result.Origin.DERIVED,
            overridden = read.origin == Result.Origin.OVERRIDDEN,
            rating = ratingOf(field, read.value),
            wide = field is MultilineTextDescription,
        )
    }

/**
 * What [field] is called where [read] is what it holds: its own label, and for a list of dives
 * how many it holds, *Dives (5)*.
 *
 * A site's, a person's or a piece of gear's dives run to dozens, and how many is the first thing
 * asked of them and the last a reader should have to count.
 */
internal fun labelOf(field: FieldDescription, read: Result<Any>): String {
    val dives = field is ReferenceDescription && field.targetType == DIVES_OF &&
        field.cardinality == Cardinality.LIST
    val many = ((read as? Result.Usable)?.value as? List<*>)?.size
    return if (dives && many != null) "${field.label} ($many)" else field.label
}

/** The type a list of dives points at. */
private const val DIVES_OF = "dive"

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
    /** Whether nobody wrote it and the model calculated it. An override counts as written. */
    val worked: Boolean = false,
    /** Whether it was written on a field the model would otherwise have calculated. */
    val overridden: Boolean = false,
    /** Out of ten, where the field is a rating; shown as stars in place of what it says. */
    val rating: Int? = null,
    /** Whether it takes a row to itself, a paragraph in half a card being a ribbon. `GUI-16`. */
    val wide: Boolean = false,
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
    if (field.name in OFFSETS) return offsetOf(number)
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
    Dimension.FLOW to "l/min",
)

/**
 * The fields holding how far one clock is ahead of another, rather than how long something took.
 *
 * Seconds in the file like any time, and read as hours and minutes with a sign, `+2:00`, which is
 * how a zone is written everywhere: as minutes and seconds two hours would read `120:00`.
 * `LOGIC-32`.
 */
internal val OFFSETS: Set<String> = setOf("time_zone_offset", "recorded_time_offset")

/** Seconds as hours and minutes with a sign always in front: `+2:00`, `-3:30`, `+0:00`. */
internal fun offsetOf(seconds: Double): String {
    val minutes = (seconds / 60).roundToLong()
    val sign = if (minutes < 0) "-" else "+"
    val whole = abs(minutes)
    return sign + (whole / 60) + ":" + (whole % 60).toString().padStart(2, '0')
}

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
    Dimension.FLOW to 1,
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
