package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.OwnedItemDescription
import yemoja.data.Result
import yemoja.data.Series

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

/**
 * Row is one line of an item's fields.
 *
 * Immutable.
 *
 * [top] is which of the item's own fields the row belongs to, so that choosing a field can mark
 * it however deep its own rows go. [indent] is how far in it sits, one step per item inside an
 * item. [value] is empty on a row that only names what follows it.
 */
internal class Row(
    val top: Int,
    val indent: Int,
    val label: String,
    val value: List<Span> = emptyList(),
)

/**
 * Every field of [item], with what is inside an item indented under its own name.
 *
 * A field holding one item is its name and then that item's fields. A field holding several is
 * its name, then each key, then that entry's fields under it. Nothing is folded away, because a
 * reader who cannot see that a dive has three profiles cannot ask for them either.
 *
 * A series is how many samples it has rather than the samples themselves, however deep it sits.
 * Expanding one belongs where a reader asked for that field and nowhere else.
 */
internal fun rowsOf(item: Item): List<Row> =
    item.description.fields.flatMapIndexed { top, field -> rowsOf(top, field, item, 0) }

/**
 * One row per field of [item], without going into anything inside it.
 *
 * A field holding items says how many rather than showing them, since where this is used a
 * reader is choosing which to go into rather than reading them all.
 */
internal fun ownRowsOf(item: Item): List<Row> =
    item.description.fields.mapIndexed { top, field ->
        val read = item.read(field.name)
        val keyed = field.cardinality == Cardinality.KEYED ||
            field.cardinality == Cardinality.KEYED_SERIES
        when {
            keyed -> Row(top, 0, field.label, listOf(Span(counted(keyedOf(
                (read as? Result.Usable)?.value).size))))

            field is OwnedItemDescription ->
                Row(top, 0, field.label, listOf(Span(if (read is Result.Usable) "..." else "")))

            else -> Row(top, 0, field.label, valueOf(field, read))
        }
    }

/** How many things sit under a field, said so that nothing reads as one. */
private fun counted(many: Int): String = "$many entr" + if (many == 1) "y" else "ies"

/** The rows one field of [item] takes, its own name first. */
internal fun rowsOf(field: FieldDescription, item: Item): List<Row> =
    rowsOf(0, field, item, 0)

private fun rowsOf(top: Int, field: FieldDescription, item: Item, indent: Int): List<Row> {
    val read = item.read(field.name)
    if (field is OwnedItemDescription) return inside(top, field, read, indent)
    return when (field.cardinality) {
        Cardinality.KEYED, Cardinality.KEYED_SERIES -> underKeys(top, field, read, indent)
        else -> listOf(Row(top, indent, field.label, valueOf(field, read)))
    }
}

/**
 * A field holding items: its name, and then what is in them.
 *
 * A shape nothing writes gets a name and nothing under it. Reading an owned item as though it
 * were a value gives what a Kotlin object calls itself, which is worse than saying nothing.
 */
private fun inside(
    top: Int,
    field: OwnedItemDescription,
    read: Result<Any>,
    indent: Int,
): List<Row> {
    val name = Row(top, indent, field.label)
    val held = (read as? Result.Usable)?.value ?: return listOf(name)
    return when (field.cardinality) {
        Cardinality.SINGLE -> listOf(name) + fieldsOf(top, held, indent + 1)

        Cardinality.KEYED -> listOf(name) + keyed(held).flatMap { (key, entry) ->
            listOf(Row(top, indent + 1, key)) + fieldsOf(top, entry, indent + 2)
        }

        else -> listOf(name)
    }
}

/** A field holding one value per key: its name, and then a row apiece. */
private fun underKeys(
    top: Int,
    field: FieldDescription,
    read: Result<Any>,
    indent: Int,
): List<Row> {
    val name = Row(top, indent, field.label)
    val held = (read as? Result.Usable)?.value ?: return listOf(name)
    return listOf(name) + keyed(held).map { (key, value) ->
        Row(top, indent + 1, key, listOf(Span(said(field, value))))
    }
}

/** The fields of an item held inside another, or nothing where what is there is not one. */
private fun fieldsOf(top: Int, held: Any, indent: Int): List<Row> {
    val within = held as? Item ?: return emptyList()
    return within.description.fields.flatMap { rowsOf(top, it, within, indent) }
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
        is Value ->
            if (field.cardinality != Cardinality.LIST) null
            else ((read as? Result.Usable)?.value as? List<*>)?.size ?: 0

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
