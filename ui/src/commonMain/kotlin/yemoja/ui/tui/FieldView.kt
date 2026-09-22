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
import yemoja.data.Mention
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
    suggested: List<String> = emptyList(),
): List<Line> {
    val about = listOf(
        "field" to field.name,
        "label" to field.label,
        "kind" to kindOf(field),
        "holds" to holding(field.cardinality),
        "role" to roleOf(field.role),
    ) + particulars(field, suggested)
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
    Cardinality.SINGLE -> "single value"
    Cardinality.LIST -> "several, in the order written"
    Cardinality.KEYED -> "several, each under a key"
    Cardinality.SERIES -> "values against time"
    Cardinality.KEYED_SERIES -> "a series under each key"
}

private fun roleOf(role: Role): String = when (role) {
    is Role.Primary -> "recorded"
    is Role.Derived -> "derived, and never written"
    is Role.Overrideable -> "derived, unless something is written"
}

/** What only this kind of field has to say. Empty for the kinds that have nothing. */
private fun particulars(
    field: FieldDescription,
    suggested: List<String>,
): List<Pair<String, String>> = when (field) {
    is NumberDescription -> listOfNotNull(
        "measures" to said(field.dimension),
        Units.defaultName(field.dimension)
            ?.let { "written in" to "$it, unless a file says otherwise" },
        field.range?.let { "between" to "${it.start} and ${it.endInclusive}" },
    )

    is WholeNumberDescription -> listOfNotNull(
        field.range?.let { "between" to "${it.first} and ${it.last}" },
    )

    // Neither is sorted. A vocabulary is written in an order somebody chose, and `usually`
    // says what the chooser offers, which is the presets joined with what the logbook uses.
    is TextDescription -> listOfNotNull(
        field.fixedSet?.let { "one of" to it.joinToString(", ") },
        field.suggestedSet?.let { "usually" to suggested.joinToString(", ") },
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
    // A field that should hold items and holds none. What one does hold is reached by going
    // into it, so the only thing left to say here is that there is nothing to go into.
    if (nested(field) && item != null) return listOf(Line(listOf(Span("  (empty)"))))
    return when (read) {
        is Result.Usable -> entries(field, read.value, chosen, item, read.origin)

        is Result.Unusable -> listOf(
            Line(listOf(Span("  " + read.reason))),
            Line(listOf(Span(""))),
            Line(listOf(Span("  as written: " + read.raw))),
        )

        // A list nobody wrote still has one place to put a value, and a reader standing there
        // is what makes the first one typeable.
        Result.Absent -> if (field.cardinality == Cardinality.LIST) listLines(emptyList(), chosen)
        else emptyList()
    }
}

/** Whether a field holds items rather than values, and so is shown as rows under its own name. */
private fun nested(field: FieldDescription): Boolean =
    field is OwnedItemDescription ||
        field.cardinality == Cardinality.KEYED ||
        field.cardinality == Cardinality.KEYED_SERIES

/**
 * A single value on its own row, and a list as a bullet apiece with [chosen] set apart.
 *
 * Only a list has a chosen entry. A single value is the whole of what the field holds, so there
 * is nothing to choose between and a mark would say there was.
 */
private fun entries(
    field: FieldDescription,
    value: Any,
    chosen: Int,
    item: Item?,
    origin: Result.Origin,
): List<Line> {
    if (value is Series) return samples(field, value)
    val held = entriesOf(field, value)
    if (field.cardinality != Cardinality.LIST) {
        val mentions = mentionsOf(field, Result.Usable(value, origin), item)
        return held.flatMap { broken(it, mentions, chosen) }
    }
    return listLines(held, chosen)
}

/**
 * A list as bullets, with the place after the last of them below.
 *
 * That place is a stop like the others and is the only one a list with nothing in it has, which
 * is what makes an empty list something a reader can type into. It carries no bullet: there is
 * nothing there yet, and a bullet with nothing after it reads as a value somebody left blank.
 */
private fun listLines(held: List<Span>, chosen: Int): List<Line> =
    held.mapIndexed { at, entry ->
        // The mark runs across the bullet and the value alike, so the row reads as one bar.
        val here = markIf(at == chosen)
        Line(listOf(Span("  - ", here), Span(entry.text, entry.styles + here)))
    } + Line(listOf(Span("  ", markIf(chosen == held.size))))

private fun markIf(chosen: Boolean): Set<Style> = if (chosen) setOf(Style.SELECTED) else emptySet()

/**
 * One value as a row apiece, a line break in it taken as a break.
 *
 * This is the one place a break is taken rather than shown. Beside the list a row is a row and
 * `remarks` is escaped into one, which `ui/tui/doc.md` explains; opened, the field is being shown
 * whole, and three lines written as three are three. Each carries the same indent, so the value
 * stays in one column.
 *
 * [mentions] are marked where they fall, so what can be followed is visible in the sentence that
 * holds it rather than listed away from it. The [chosen] one is set apart the way a chosen bullet
 * is, since both are the thing the reader is pointing at.
 */
private fun broken(value: Span, mentions: List<Mention> = emptyList(), chosen: Int = -1):
    List<Line> {
    var from = 0
    return value.text.split("\r\n", "\n", "\r").map { line ->
        val at = from
        from += line.length + 1
        Line(listOf(Span("  ")) + marked(line, value.styles, mentions, at, chosen))
    }
}

/**
 * [line] as spans, with any mention falling inside it underlined.
 *
 * [from] is where the line begins in the whole value, since a mention is placed against that and
 * a line is only a stretch of it.
 */
private fun marked(
    line: String,
    styles: Set<Style>,
    mentions: List<Mention>,
    from: Int,
    chosen: Int,
): List<Span> {
    val here = mentions.withIndex()
        .filter { (_, it) -> it.at.first >= from && it.at.last < from + line.length }
    if (here.isEmpty()) return listOf(Span(line, styles))
    val spans = ArrayList<Span>()
    var at = 0
    for ((which, mention) in here) {
        val starts = mention.at.first - from
        if (starts > at) spans.add(Span(line.substring(at, starts), styles))
        at = mention.at.last + 1 - from
        val apart = if (which == chosen) setOf(Style.SELECTED) else emptySet()
        spans.add(Span(line.substring(starts, at), styles + Style.UNDERLINED + apart))
    }
    if (at < line.length) spans.add(Span(line.substring(at), styles))
    return spans
}

private fun originOf(origin: Result.Origin): String = when (origin) {
    Result.Origin.STORED -> "written"
    Result.Origin.DERIVED -> "derived"
    Result.Origin.OVERRIDDEN -> "written over what would have been derived"
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

/**
 * Choice is one row of a chooser: a value to take, a way to type one, or the field left empty.
 *
 * A chooser is offered where a field's values are known, so what a reader does there is pick
 * rather than spell. The two rows that are not values are what picking cannot otherwise reach.
 */
internal sealed class Choice {

    /** A value the field may hold, in the written form. */
    class Value(val said: String) : Choice()

    /**
     * Type something the rows do not offer.
     *
     * Only where a set is suggested rather than fixed. `DATA-25` offers those values without
     * enforcing them, so a chooser that could not leave them would refuse what the field takes.
     */
    object Other : Choice()

    /** The field holding nothing, which is a state every field has. */
    object Nothing : Choice()
}

/**
 * The choices [field] offers, where its values are known, or absent where they are not.
 *
 * Absent for most fields: a number, a date and free text are typed. A fixed set ends with
 * [Choice.Nothing] and a suggested one with [Choice.Other] before it, since a value outside a
 * fixed set is unusable — `DATA-24` — and a value outside a suggested one is ordinary.
 *
 * In the written form, `DATA-76`, so a value picked here is the value a row shows and the value
 * a file holds. A boolean is `true` and `false` for that reason, whatever a form would call
 * them.
 */
internal fun choicesOf(field: FieldDescription, suggested: List<String>): List<Choice>? = when {
    field is BooleanDescription ->
        listOf(Choice.Value("true"), Choice.Value("false"), Choice.Nothing)

    field is TextDescription && field.fixedSet != null ->
        field.fixedSet!!.map { Choice.Value(it) } + Choice.Nothing

    field is TextDescription && field.suggestedSet != null ->
        suggested.map { Choice.Value(it) } + Choice.Other + Choice.Nothing

    else -> null
}

/**
 * The choices of [field], one per row, with the one at [chosen] set apart.
 *
 * A boolean carries a box, since two values that exclude each other is what a box says. A set of
 * words does not: a column of boxes down a vocabulary of six says nothing the cursor has not.
 *
 * [choices] is what [choicesOf] answered, passed in rather than asked for again: what a field
 * suggests depends on what the logbook holds, which this file has no way to reach.
 *
 * [typed] is what is being typed on the *other* row, and [other] what that row shows when it is
 * not being typed into — the value the field holds, where the rows do not offer it.
 */
internal fun chooserLines(
    field: FieldDescription,
    choices: List<Choice>,
    chosen: Int,
    typed: String? = null,
    other: String = "",
): List<Line> {
    val box = field is BooleanDescription
    return choices.mapIndexed { at, choice ->
        val here = if (at == chosen) setOf(Style.SELECTED) else emptySet()
        val mark = if (!box) "" else if (at == chosen) "[x] " else "[ ] "
        val said = when (choice) {
            is Choice.Value -> choice.said
            Choice.Other -> "other: " + if (typed == null) other else typed + CURSOR
            Choice.Nothing -> "(nothing)"
        }
        Line(listOf(Span("  " + mark + said, here)))
    }
}
