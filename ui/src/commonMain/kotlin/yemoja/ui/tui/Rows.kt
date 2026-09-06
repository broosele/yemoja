package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.Mention
import yemoja.data.MultilineTextDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.mentionsIn

/*
 * An item's fields as rows, and the fields of anything inside it under them.
 *
 * Both views walk this. What differs between them is how much room they have, not what there is
 * to show: a value is cut to fit here and shown whole where a field is opened.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/** How much of a value a row shows. Past this it is cut and marked. */
internal const val VALUE_WIDTH: Int = 32

/** How far an item inside an item is set in. */
internal const val STEP: Int = 2

/** Steps is the way from an item to a field inside it: a field, and where in it once keyed. */
internal typealias Steps = List<Pair<FieldDescription, String?>>

/**
 * Row is one line of an item's fields, and one field a reader can choose.
 *
 * Immutable.
 *
 * [steps] is the way to it, which is what opening it needs and what marking it goes by.
 * [indent] is how far in it sits, one step per item inside an item. [value] is empty on a row
 * that names a field holding an item, whose own rows follow it.
 */
internal class Row(
    val steps: Steps,
    val indent: Int,
    val label: String,
    val value: List<Span> = emptyList(),
) {

    /** The field this row is. */
    val field: FieldDescription get() = steps.last().first
}

/**
 * Every field of [item], with the fields of an item inside it indented under its own name.
 *
 * **One row is one field, and every one of them can be chosen.** A field holding one item is
 * its name and then that item's fields, however deep that goes.
 *
 * Everything else says what it amounts to and no more: a list as its values, a series as how
 * many samples it took, and a field holding several as its keys. Those are unbounded — a dive
 * with three profiles of twenty fields would be sixty rows of somebody else's business — and
 * they are shown whole where a reader asks for that field.
 */
internal fun rowsOf(item: Item): List<Row> = rowsOf(item, emptyList(), 0)

private fun rowsOf(item: Item, above: Steps, indent: Int): List<Row> =
    item.description.fields.flatMap { field ->
        val steps = above + (field to null)
        val read = item.read(field.name)
        val name = Row(steps, indent, field.label)
        when {
            field is OwnedItemDescription && field.cardinality == Cardinality.SINGLE -> {
                val held = (read as? Result.Usable)?.value as? Item
                listOf(name) + (held?.let { rowsOf(it, steps, indent + 1) } ?: emptyList())
            }

            field.cardinality == Cardinality.KEYED ||
                field.cardinality == Cardinality.KEYED_SERIES ->
                listOf(Row(steps, indent, field.label, keysIn(read)))

            else -> listOf(Row(steps, indent, field.label, valueOf(field, read)))
        }
    }

/** The keys of a field holding one thing per key, as many as fit and how many did not. */
private fun keysIn(read: Result<Any>): List<Span> {
    val keys = keyedOf((read as? Result.Usable)?.value).map { it.first }
    return if (keys.isEmpty()) emptyList() else listOf(Span(shortened(keys)))
}

/**
 * One row per field of [item], without going into anything inside it.
 *
 * A field holding items says how many rather than showing them, since where this is used a
 * reader is choosing which to go into rather than reading them all.
 */
internal fun ownRowsOf(item: Item): List<Row> =
    item.description.fields.map { field ->
        val steps = listOf(field to null)
        val read = item.read(field.name)
        val keyed = field.cardinality == Cardinality.KEYED ||
            field.cardinality == Cardinality.KEYED_SERIES
        when {
            keyed -> Row(steps, 0, field.label, keysIn(read))

            field is OwnedItemDescription ->
                Row(steps, 0, field.label, listOf(Span(if (read is Result.Usable) "..." else "")))

            else -> Row(steps, 0, field.label, valueOf(field, read))
        }
    }

/** What one field holds, cut to the room a row has for it. */
private fun valueOf(field: FieldDescription, read: Result<Any>): List<Span> = when (read) {
    is Result.Usable -> listOf(
        Span(said(field, read.value), setsApart(read) + underlining(field, read.value)),
    )

    is Result.Unusable -> listOf(Span(cut(flat("! " + read.reason))))
    Result.Absent -> emptyList()
}

/**
 * One value as a row says it: a count for a series, several separated by commas for a list, and
 * otherwise the value as a file writes it.
 */
private fun said(field: FieldDescription, value: Any): String = when {
    value is Series -> samplesIn(value)
    field.cardinality == Cardinality.LIST ->
        shortened(entriesOf(field, value).map { flat(it.text) })

    else -> cut(flat(entriesOf(field, value).single().text))
}

/** How many samples a series holds, which is all a row has room to say about one. */
internal fun samplesIn(series: Series): String =
    "${series.size} sample" + if (series.size == 1) "" else "s"

/**
 * Several values on one row: as many as fit, and how many did not.
 *
 * Saying how many were left out is what a plain cut cannot: three dots at the end of a list of
 * regions could be one more or forty, and the difference is what decides whether opening the
 * field is worth it.
 */
private fun shortened(entries: List<String>): String {
    if (entries.isEmpty()) return EMPTY
    val whole = entries.joinToString(SEPARATOR)
    if (entries.size <= 1 || whole.length <= VALUE_WIDTH) return cut(whole)
    for (count in entries.size - 1 downTo 1) {
        val rest = entries.size - count
        val room = entries.take(count).joinToString(SEPARATOR) +
            " $MORE ($rest other${if (rest == 1) "" else "s"})"
        if (room.length <= VALUE_WIDTH) return room
    }
    // Not even the first entry fits beside the count, so only the count is worth saying.
    return cut("(${entries.size} entries)")
}

/**
 * [text] short enough to sit beside its neighbours.
 *
 * A column wide enough for the longest remark anybody writes would be a column of mostly
 * nothing, so a value is cut here and shown whole where it is opened. The mark says which values
 * have more to them, which a value simply ending does not.
 */
internal fun cut(text: String): String =
    if (text.length <= VALUE_WIDTH) text else text.take(VALUE_WIDTH - MORE.length) + MORE

/**
 * [text] with no line break left in it.
 *
 * `remarks` is multiline and a terminal row is not, so a break is shown as the escape a file
 * writes it with rather than taken: a value holding one would otherwise occupy three rows while
 * measuring as one, and in raw mode leave the cursor wherever the last row ended.
 */
internal fun flat(text: String): String =
    text.replace("\r\n", ESCAPED).replace("\n", ESCAPED).replace("\r", ESCAPED)

/** Bold is written over what would have been worked out, and italic is worked out. */
private fun setsApart(read: Result.Usable<*>): Set<Style> = when (read.origin) {
    Result.Origin.OVERRIDDEN -> setOf(Style.BOLD)
    Result.Origin.DERIVED -> setOf(Style.ITALIC)
    Result.Origin.STORED -> emptySet()
}

/**
 * Underlined where the field names items.
 *
 * A row holds one piece of text for the whole field, so it can only say that the field names
 * them. Where a field is opened each value stands alone and says whether it names one, which is
 * [entriesOf]'s to answer.
 */
private fun underlining(field: FieldDescription, value: Any): Set<Style> =
    if (entriesOf(field, value).any { Style.UNDERLINED in it.styles }) setOf(Style.UNDERLINED)
    else emptySet()

/** The entries of a keyed field, in the order they were written. */
@Suppress("UNCHECKED_CAST")
private fun keyed(held: Any): List<Pair<String, Any>> =
    (held as? Map<String, Element<Any>>)?.mapNotNull { (key, entry) ->
        (entry as? Element.Usable)?.let { key to it.value }
    }.orEmpty()

/** What sits between two values of one field on a row. */
private const val SEPARATOR = ", "

/** A list somebody wrote with nothing in it, which is not a field nobody wrote. */
private const val EMPTY = "(empty)"

/**
 * That a value goes on past where the row stopped showing it.
 *
 * Three dots rather than the one character that means them. A Windows console on a code page
 * that is not UTF-8 shows that character as a question mark, which reads as a value nobody could
 * make sense of rather than as a value that was cut.
 */
private const val MORE = "..."

/** What a line break is shown as, which is how a file writes one. */
private const val ESCAPED = "\\n"

/**
 * Ends is where a path into an item stops, and so what is in front of the reader.
 *
 * Three, because there are three things a reader can be looking at: values, the fields of one
 * item, or the keys of a field holding several. Each answers to the same keys and shows a
 * different thing under them.
 */
internal sealed class Ends {

    /**
     * At a field holding values: one, a list of them, or a series.
     *
     * [item] is what the field belongs to, where that is an item this reached by name. It is
     * absent for a series taken from under a key, which no item holds by itself.
     */
    class Value(
        val field: FieldDescription,
        val read: Result<Any>,
        val item: Item? = null,
    ) : Ends()

    /** Inside one item, at its own fields. */
    class Within(val item: Item) : Ends()

    /** At the keys of a field holding one thing per key. */
    class Keys(val field: FieldDescription, val held: List<Pair<String, Any>>) : Ends()

    /**
     * How many things a reader moves between here, or absent where there is nothing to.
     *
     * A function rather than a property, because inside a property's own accessor `field` is
     * Kotlin's word for the backing field rather than this class's.
     */
    fun count(): Int? = when (this) {
        is Value -> when {
            // One more than there are values. A value goes between two of them, before the
            // first or after the last, which is n+1 places for n values and one place for
            // none: a list with nothing in it still has somewhere to put something.
            field.cardinality == Cardinality.LIST ->
                (((read as? Result.Usable)?.value as? List<*>)?.size ?: 0) + 1

            // A remark holding nothing to follow is one value like any other, so up and down
            // go back to scrolling rather than moving over a cursor with one stop.
            else -> mentionsOf(field, read, item).size.takeIf { it > 0 }
        }

        is Within -> item.description.fields.size
        is Keys -> held.size
    }
}

/**
 * Where [steps] lead from [item], or absent where they lead nowhere.
 *
 * A step names a field, and once a key has been chosen it names that too. Reading a field that
 * holds items walks into them; reading one that holds values stops there, whatever is left of
 * the path, because there is nothing further in to go.
 */
internal fun endsOf(item: Item, steps: List<Pair<FieldDescription, String?>>): Ends? {
    var here = item
    for ((at, step) in steps.withIndex()) {
        val (naming, key) = step
        val read = here.read(naming.name)
        val keyed = naming.cardinality == Cardinality.KEYED ||
            naming.cardinality == Cardinality.KEYED_SERIES
        if (naming !is OwnedItemDescription && !keyed) return Ends.Value(naming, read, here)
        val usable = read as? Result.Usable
        if (keyed) {
            val held = keyedOf(usable?.value)
            if (key == null) return Ends.Keys(naming, held)
            val entry = held.firstOrNull { it.first == key }?.second ?: return null
            if (entry !is Item) return Ends.Value(naming, Result.Usable(entry, usable!!.origin))
            here = entry
        } else {
            // A field that should hold an item and holds none is not a way in. It stops here,
            // and what it says is that there is nothing in it.
            here = usable?.value as? Item ?: return Ends.Value(naming, read, here)
        }
        if (at == steps.size - 1) return Ends.Within(here)
    }
    return null
}

/** The entries of a keyed field, in the order they were written. */
internal fun keyedOf(held: Any?): List<Pair<String, Any>> = keyed(held ?: return emptyList())

/**
 * The mentions in a field of free text that name something this logbook holds.
 *
 * **Only the ones that resolve.** `JSON-23` gives a mention no fixed meaning and asks an
 * interface to mark what resolves and nothing else: one that flagged every candidate would
 * light up each address and each `@media` in the logbook. So an id nothing answers to is left
 * as the text it always was.
 *
 * Empty for every field but multiline text, mentions being a convention of free text.
 */
internal fun mentionsOf(field: FieldDescription, read: Result<Any>, item: Item?): List<Mention> {
    if (field !is MultilineTextDescription) return emptyList()
    val set = item?.set ?: return emptyList()
    val text = (read as? Result.Usable)?.value as? String ?: return emptyList()
    return mentionsIn(text).filter { set[it.id] != null }
}
