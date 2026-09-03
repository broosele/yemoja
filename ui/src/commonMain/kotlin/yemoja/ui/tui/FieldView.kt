package yemoja.ui.tui

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Role
import yemoja.data.TextDescription
import yemoja.data.TimeDescription
import yemoja.data.Units
import yemoja.data.WholeNumberDescription

/*
 * One field on its own: what it is, and the whole of what it holds.
 *
 * The list shows a value cut to fit beside its neighbours. This is where the rest of it is, so
 * nothing in the logbook is only ever seen in part.
 *
 * See ../../../../../../doc.md — the TUI's own document is ui/tui/doc.md.
 */

/**
 * Everything worth saying about [field] on [item], as rows of text.
 *
 * What the field is comes first and what it holds comes last, because a reader who opened this
 * to check a value would rather scroll past a description than hunt for one.
 *
 * The rows are logical: each is as long as it needs to be, and whoever draws them cuts, wraps
 * and scrolls. Nothing here knows how wide a screen is.
 */
internal fun fieldLines(
    field: FieldDescription,
    read: Result<Any>,
    item: Item?,
    chosen: Int = 0,
): List<Line> {
    val about = listOf(
        "field" to field.name,
        "label" to field.label,
        "kind" to kindOf(field),
        "holds" to holding(field.cardinality),
        "role" to roleOf(field.role),
    ) + particulars(field)
    val width = about.maxOf { it.first.length }
    return heading("This field") +
        about.map { (name, said) -> Line(listOf(Span("  ${name.padEnd(width)}  $said"))) } +
        heading("What it holds (${saying(read)})") +
        held(item, field, read, chosen)
}

/**
 * How the field came to hold what it holds, which goes beside the heading rather than under it.
 *
 * It is one word about the whole of what follows rather than a thing the field holds, so putting
 * it in the list would make a reader count it among the values.
 */
private fun saying(read: Result<Any>): String = when (read) {
    is Result.Usable -> originOf(read.origin)
    is Result.Unusable -> "written, and could not be read"
    Result.Absent -> "nothing"
}

/** A section, with a blank row above it so the sections read apart. */
private fun heading(said: String): List<Line> =
    listOf(Line(listOf(Span(""))), Line(listOf(Span(said, setOf(Style.BOLD)))))

/**
 * What kind of field it is, in the words the manual uses for the same thing.
 *
 * Exhaustive over the kinds, so a kind added below is a compiler error here rather than a field
 * this view has nothing to say about.
 */
private fun kindOf(field: FieldDescription): String = when (field) {
    is TextDescription -> if (field.fixedSet != null) "fixed set" else "text"
    is MultilineTextDescription -> "multiline text"
    is NumberDescription -> "number"
    is WholeNumberDescription -> "whole number"
    is DateDescription -> "date"
    is TimeDescription -> "time"
    is BooleanDescription -> "true or false"
    is GasDescription -> "gas"
    is ReferenceDescription -> "reference"
    is KeyReferenceDescription -> "key reference"
    is OwnedItemDescription -> "owned item"
}

/** `DIMENSIONLESS` reads oddly as a measurement, and a ratio is what it means. */
private fun said(dimension: Dimension): String =
    if (dimension == Dimension.DIMENSIONLESS) "nothing, being a ratio"
    else dimension.name.lowercase()

private fun holding(cardinality: Cardinality): String = when (cardinality) {
    Cardinality.SINGLE -> "one value"
    Cardinality.LIST -> "several, in the order written"
    Cardinality.KEYED -> "several, each under a key"
    Cardinality.SERIES -> "values against time"
    Cardinality.KEYED_SERIES -> "one series under each key"
}

private fun roleOf(role: Role): String = when (role) {
    is Role.Primary -> "recorded"
    is Role.Derived -> "worked out, and never written"
    is Role.Overrideable -> "worked out, unless something is written"
}

/** What only this kind of field has to say. Empty for the kinds that have nothing. */
private fun particulars(field: FieldDescription): List<Pair<String, String>> = when (field) {
    is NumberDescription -> listOfNotNull(
        "measures" to said(field.dimension),
        Units.defaultName(field.dimension)
            ?.let { "written in" to "$it, unless a file says otherwise" },
        field.range?.let { "between" to "${it.start} and ${it.endInclusive}" },
    )

    is WholeNumberDescription -> listOfNotNull(
        field.range?.let { "between" to "${it.first} and ${it.last}" },
    )

    is TextDescription -> listOfNotNull(
        field.fixedSet?.let { "one of" to it.sorted().joinToString(", ") },
        field.suggestedSet?.let { "usually" to it.sorted().joinToString(", ") },
    )

    // Whether a plain name may stand in belongs to what the field names rather than beside it:
    // on its own it read as a question with yes or no under it, which said no more than the
    // question did. Only the permission is worth a word; refusing one is what every other
    // reference does.
    is ReferenceDescription -> listOf(
        "names a" to field.targetType +
            if (field.oneOffAllowed) ", or a plain name where there is no item" else "",
    )

    is KeyReferenceDescription -> listOf("points into" to field.collection)
    is OwnedItemDescription -> listOf("is a" to field.description.name)
    else -> emptyList()
}

/**
 * What a field holds, one entry apiece: one for a single value, several for a list.
 *
 * An entry that could not be read shows why in its place. The rest of the list is unaffected —
 * `DATA-77` — and dropping it would leave a shorter list than the file holds.
 */
@Suppress("UNCHECKED_CAST")
internal fun entriesOf(field: FieldDescription, value: Any): List<Span> =
    if (field.cardinality != Cardinality.LIST) listOf(said(field, value))
    else (value as List<Element<Any>>).map { entry ->
        when (entry) {
            is Element.Usable -> said(field, entry.value)
            is Element.Unusable -> Span("! " + entry.reason)
        }
    }

/**
 * One value as a file writes it, underlined where it names an item.
 *
 * Underlined by what the value is rather than by what the field is, which the row cannot do: a
 * plain name asserting no id opens nothing, and neither does an entry that would not read, so
 * neither is marked as though it did.
 */
private fun said(field: FieldDescription, value: Any): Span = Span(
    field.format(value, Units.DEFAULT),
    if (value is Reference.Identified) setOf(Style.UNDERLINED) else emptySet(),
)

/**
 * A series as one row per sample: when it was taken, and what was read.
 *
 * The time is seconds from the start of the recording, which is what the file holds and what
 * `DATA-58` fixes whatever else a file declares. Times are lined up on the right so that the
 * values stand in a column of their own, a profile being read down rather than across.
 */
private fun samples(field: FieldDescription, series: Series): List<Line> {
    if (series.size == 0) return listOf(Line(listOf(Span("  (empty)"))))
    val width = (0..<series.size).maxOf { series.secondAt(it).toString().length }
    return (0..<series.size).map { at ->
        val said = when (val value = series.valueAt(at)) {
            is Element.Usable -> said(field, value.value)
            is Element.Unusable -> Span("! " + value.reason)
        }
        Line(listOf(Span("  " + series.secondAt(at).toString().padStart(width) + "  "), said))
    }
}

/**
 * What the field holds, whole and uncut.
 *
 * A value that could not be read shows both what was written and why it was refused, since the
 * two together are what a reader needs to fix it. A list is a bullet apiece, which is what makes
 * it possible to see where one entry ends and the next begins.
 *
 * A field holding nothing says so in the heading and adds nothing here, there being nothing to
 * add.
 */
private fun held(
    item: Item?,
    field: FieldDescription,
    read: Result<Any>,
    chosen: Int,
): List<Line> {
    // A field holding items is shown as the rows it takes, where there is an item to read them
    // from. A series taken from under a key belongs to no item by itself, and is a value.
    if (nested(field) && item != null) return inside(field, item)
    return when (read) {
        is Result.Usable -> entries(field, read.value, chosen)

        is Result.Unusable -> listOf(
            Line(listOf(Span("  " + read.reason))),
            Line(listOf(Span(""))),
            Line(listOf(Span("  as written: " + read.raw))),
        )

        Result.Absent -> emptyList()
    }
}

/** Whether a field holds items rather than values, and so is shown as rows under its own name. */
private fun nested(field: FieldDescription): Boolean =
    field is OwnedItemDescription ||
        field.cardinality == Cardinality.KEYED ||
        field.cardinality == Cardinality.KEYED_SERIES

/**
 * A field holding items, as the rows it takes.
 *
 * The row naming the field is dropped and everything moves in by one, the heading above having
 * said which field this is already.
 */
private fun inside(field: FieldDescription, item: Item): List<Line> {
    val rows = rowsOf(field, item).drop(1)
    if (rows.isEmpty()) return listOf(Line(listOf(Span("  (empty)"))))
    val width = rows.maxOf { (it.indent - 1) * STEP + it.label.length }
    return rows.map { row ->
        val name = " ".repeat((row.indent - 1) * STEP) + row.label
        Line(listOf(Span("  " + name.padEnd(width) + "  ")) + row.value)
    }
}

/**
 * A single value on its own row, and a list as a bullet apiece with [chosen] set apart.
 *
 * Only a list has a chosen entry. A single value is the whole of what the field holds, so there
 * is nothing to choose between and a mark would say there was.
 */
private fun entries(field: FieldDescription, value: Any, chosen: Int): List<Line> {
    if (value is Series) return samples(field, value)
    val held = entriesOf(field, value)
    if (field.cardinality != Cardinality.LIST) return held.map { Line(listOf(Span("  "), it)) }
    // A written list with nothing in it is not the same as a field nobody wrote, and a row of
    // bullets with no bullets in it would look like the second.
    if (held.isEmpty()) return listOf(Line(listOf(Span("  (empty)"))))
    return held.mapIndexed { at, entry ->
        // The mark runs across the bullet and the value alike, so the row reads as one bar.
        val here = if (at == chosen) setOf(Style.SELECTED) else emptySet()
        Line(listOf(Span("  - ", here), Span(entry.text, entry.styles + here)))
    }
}

private fun originOf(origin: Result.Origin): String = when (origin) {
    Result.Origin.STORED -> "written"
    Result.Origin.DERIVED -> "worked out"
    Result.Origin.OVERRIDDEN -> "written over what would have been worked out"
}

/**
 * The fields of one item, one row apiece, with [chosen] set apart.
 *
 * Its own fields and no deeper. A reader here is choosing which to go into, and the rows of
 * everything inside would be rows they cannot choose — the column beside the list is where the
 * whole of an item is seen at once.
 */
internal fun withinLines(item: Item, chosen: Int): List<Line> {
    val rows = ownRowsOf(item)
    if (rows.isEmpty()) return listOf(Line(listOf(Span("  (no fields)"))))
    val width = rows.maxOf { it.label.length }
    return heading("What it holds") + rows.mapIndexed { at, row ->
        val here = if (at == chosen) setOf(Style.SELECTED) else emptySet()
        Line(
            listOf(Span("  " + row.label.padEnd(width) + "  ", here)) +
                row.value.map { Span(it.text, it.styles + here) },
        )
    }
}

/**
 * The keys of a field holding one thing per key, with [chosen] set apart.
 *
 * What sits under each is said in a word — how many fields an item has, how many samples a
 * series took — so that a reader picking one is picking something rather than a name.
 */
internal fun keyLines(field: FieldDescription, held: List<Pair<String, Any>>, chosen: Int):
    List<Line> {
    if (held.isEmpty()) return listOf(Line(listOf(Span("  (empty)"))))
    val width = held.maxOf { it.first.length }
    return heading("Under each key") + held.mapIndexed { at, (key, entry) ->
        val here = if (at == chosen) setOf(Style.SELECTED) else emptySet()
        Line(listOf(Span("  " + key.padEnd(width) + "  " + summary(field, entry), here)))
    }
}

/** What one entry under a key amounts to, in a word. */
private fun summary(field: FieldDescription, entry: Any): String = when (entry) {
    is Series -> samplesIn(entry)
    is Item -> "${entry.description.fields.size} fields"
    else -> field.format(entry, Units.DEFAULT)
}
