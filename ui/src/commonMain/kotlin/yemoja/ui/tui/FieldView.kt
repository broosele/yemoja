package yemoja.ui.tui

import yemoja.data.BooleanDescription
import yemoja.data.Cardinality
import yemoja.data.DateDescription
import yemoja.data.Dimension
import yemoja.data.FieldDescription
import yemoja.data.GasDescription
import yemoja.data.Item
import yemoja.data.KeyReferenceDescription
import yemoja.data.MultilineTextDescription
import yemoja.data.NumberDescription
import yemoja.data.OwnedItemDescription
import yemoja.data.ReferenceDescription
import yemoja.data.Result
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
internal fun fieldLines(item: Item, field: FieldDescription): List<Line> {
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
        heading("What it holds") +
        held(item, field)
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

    is ReferenceDescription -> listOf(
        "names a" to field.targetType,
        "or a plain name" to if (field.oneOffAllowed) "yes" else "no",
    )

    is KeyReferenceDescription -> listOf("points into" to field.collection)
    is OwnedItemDescription -> listOf("is a" to field.description.name)
    else -> emptyList()
}

/**
 * What the field holds on this item, whole and uncut.
 *
 * A value that could not be read shows both what was written and why it was refused, since the
 * two together are what a reader needs to fix it.
 */
private fun held(item: Item, field: FieldDescription): List<Line> =
    when (val read = item.read(field.name)) {
        is Result.Usable -> listOf(
            Line(listOf(Span("  " + originOf(read.origin)))),
            Line(listOf(Span(""))),
            Line(listOf(Span("  " + field.format(read.value, Units.DEFAULT)))),
        )

        is Result.Unusable -> listOf(
            Line(listOf(Span("  written, and could not be read"))),
            Line(listOf(Span(""))),
            Line(listOf(Span("  " + read.reason))),
            Line(listOf(Span(""))),
            Line(listOf(Span("  as written: " + read.raw))),
        )

        Result.Absent -> listOf(Line(listOf(Span("  nothing"))))
    }

private fun originOf(origin: Result.Origin): String = when (origin) {
    Result.Origin.STORED -> "written"
    Result.Origin.DERIVED -> "worked out"
    Result.Origin.OVERRIDDEN -> "written over what would have been worked out"
}
