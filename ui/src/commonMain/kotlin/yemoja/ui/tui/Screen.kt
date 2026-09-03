package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.Element
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.ReferenceDescription
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Series
import yemoja.data.Units

/** Key is a keystroke this interface answers to. Everything else is ignored. */
enum class Key {
    LEFT, RIGHT, UP, DOWN, NEXT_FIELD, PREVIOUS_FIELD, FOLLOW, OPEN, CLOSE, QUIT
}

/**
 * Screen is the whole terminal interface: which tab is open, which item and field are chosen, and
 * what that looks like as rows of text.
 *
 * **Deliberately not immutable.** Where the user is in the interface is presentation state, which
 * `ui/doc.md` allows a front end to hold, and it changes on every keystroke.
 *
 * It holds no terminal. [paint] answers with rows and [press] takes a key, so what reads the
 * keyboard and what puts characters on a screen is somebody else's, and this is testable without
 * one.
 *
 * **The layout comes from the descriptions and nowhere else.** A tab per type in the order the
 * types are given, and every single-valued field of the chosen item, in the order the type
 * declares them. Nothing here names a type or a field, so a type added to the logic layer appears
 * without this changing.
 */
class Screen(private val set: ItemSet, private val types: List<ItemDescription>) {

    init {
        require(types.isNotEmpty()) { "a screen should have at least one type, and had none" }
    }

    /** Which tab is open, as an index into the types given. */
    var tab: Int = 0
        private set

    /** Whether the interface is still running. False once the user has asked to leave. */
    var running: Boolean = true
        private set

    // One item, one field and one scroll offset per tab, so that leaving a tab and coming back
    // returns to where the user was rather than to the top.
    private val chosen = IntArray(types.size)

    private val chosenField = IntArray(types.size)

    private val first = IntArray(types.size)

    // How far down the column of fields is scrolled, which an item holding items can need.
    private val deep = IntArray(types.size)

    // How far into the chosen item a reader has gone: a field, then a key inside it, then a
    // field of what was under that key, and so on down. Empty where the list is being shown.
    private val path = ArrayList<Pair<FieldDescription, String?>>()

    // How far down what is open is scrolled, and which of the things in it is chosen. One of
    // each, since a reader is in one place at a time.
    private var within = 0

    private var chosenEntry = 0

    /** Whether something is open on its own, rather than the list being shown. */
    val opened: Boolean get() = path.isNotEmpty()

    /** The type whose tab is open. */
    val type: ItemDescription get() = types[tab]

    /** Every item of the open type, in the order the set holds them. */
    val items: List<ReferenceableItem> get() = set.allOf(type)

    /** The chosen item, or absent where the open type has none. */
    val item: ReferenceableItem? get() = items.getOrNull(chosen[tab])

    /**
     * The rows of the chosen item: one per field, and one per field of anything inside it.
     *
     * Every one of them can be chosen, which is what makes a field of a medical reachable
     * without its being a place of its own.
     */
    internal val rows: List<Row> get() = item?.let { rowsOf(it) }.orEmpty()

    /** The chosen row, or absent where the open type has no items. */
    internal val row: Row? get() = rows.getOrNull(chosenField[tab])

    /** The chosen field, or absent where the open type has none. */
    val field: FieldDescription? get() = row?.field

    /** The fields of the open type, for anything counting them. */
    val fields: List<FieldDescription> get() = type.fields

    /**
     * How many values the chosen field holds, or absent where it does not hold a list of them.
     *
     * What decides whether there is anything to move between where a reader is: the values of
     * a list, the fields of an item, or the keys of a field holding several. A single value is
     * one thing however many rows it wraps over, so there is nothing to move between and up and
     * down scroll instead.
     */
    private fun entries(): Int? {
        if (!opened) return null
        return endsOf(item ?: return null, path)?.count()
    }

    /**
     * Answer [key], and say whether the interface is still running.
     *
     * Left and right change tab and wrap round, so three tabs are reached in two presses from
     * either end. Up and down move within the list and stop at its ends, because a list that
     * wraps loses the user's place on a long one. Tab moves between the fields of the chosen item
     * and does wrap, a field list being short enough to see whole.
     */
    fun press(key: Key): Boolean {
        when (key) {
            Key.LEFT -> tab = (tab + types.size - 1) % types.size
            Key.RIGHT -> tab = (tab + 1) % types.size
            Key.UP -> move(-1)
            Key.DOWN -> move(1)
            Key.NEXT_FIELD -> step(1)
            Key.PREVIOUS_FIELD -> step(-1)
            Key.FOLLOW -> follow()
            Key.OPEN -> open()
            Key.CLOSE -> close()
            Key.QUIT -> running = false
        }
        if (!opened) {
            within = 0
            chosenEntry = 0
        }
        // An emptied tab leaves the chosen row past the end, and nothing else corrects it.
        chosen[tab] = chosen[tab].coerceIn(0, (items.size - 1).coerceAtLeast(0))
        chosenField[tab] = chosenField[tab].coerceIn(0, (rows.size - 1).coerceAtLeast(0))
        return running
    }

    /**
     * Move [by] through whatever is in front of the user.
     *
     * The list of items where nothing is open; the values of an open field that holds several;
     * and the rows themselves where an open field holds one value, which may be longer than the
     * screen. A field holds one or the other, never both, so the two never have to be told apart
     * by a second key.
     */
    private fun move(by: Int) {
        val count = entries()
        when {
            !opened -> chosen[tab] = chosen[tab] + by
            count != null ->
                chosenEntry = (chosenEntry + by).coerceIn(0, (count - 1).coerceAtLeast(0))

            else -> within = (within + by).coerceAtLeast(0)
        }
    }

    /**
     * Go one step further in, where there is one.
     *
     * From the list that is the chosen field. Inside an item it is the field the cursor is on,
     * and at a set of keys it is the key. At values there is nowhere further to go, an open
     * field already showing the whole of what it holds.
     */
    private fun open() {
        val item = item ?: return
        if (!opened) {
            path.addAll((row ?: return).steps)
        } else {
            when (val ends = endsOf(item, path)) {
                is Ends.Within -> path.add(ends.item.description.fields[chosenEntry] to null)
                is Ends.Keys -> {
                    val last = path.removeAt(path.size - 1)
                    path.add(last.first to ends.held[chosenEntry].first)
                }

                else -> return
            }
        }
        within = 0
        chosenEntry = 0
    }

    /**
     * Come one step back out, and leave where there is nothing to come out of.
     *
     * A key is a step of its own, so leaving one entry of a keyed field lands on its keys
     * rather than skipping past them.
     */
    private fun close() {
        if (!opened) {
            running = false
            return
        }
        val last = path.removeAt(path.size - 1)
        if (last.second != null) path.add(last.first to null)
        // The cursor lands on what was just left rather than at the top, the way each tab keeps
        // the row it was on: coming out of something is not the same as arriving somewhere.
        chosenEntry = when (val ends = item?.let { endsOf(it, path) }) {
            is Ends.Within ->
                ends.item.description.fields.indexOfFirst { it.name == last.first.name }

            is Ends.Keys -> ends.held.indexOfFirst { it.first == last.second }
            else -> 0
        }.coerceAtLeast(0)
        within = 0
    }

    /** Move [by] fields, round the end. */
    private fun step(by: Int) {
        val many = rows.size
        if (many == 0) return
        chosenField[tab] = (chosenField[tab] + by + many) % many
        // Another field is another value, so where the reader was in the last one means nothing.
        if (opened) {
            path.clear()
            path.addAll(row?.steps.orEmpty())
        }
        within = 0
        chosenEntry = 0
    }

    /**
     * Open the item the chosen field names, where it names one that is there.
     *
     * **The first one that can be opened**, which for a field naming several is the one the row
     * is showing. A field that is not a reference, a reference to nothing, a plain name asserting
     * no id, and a reference to a type this screen has no tab for are all the same answer: stay
     * where we are.
     *
     * **Following is one way and stays that way.** Nothing records where the reader came from,
     * because nothing needs to: every item is a tab and a few rows away, so a stack would be
     * state kept for a journey nobody has to retrace. `TUI-5`.
     */
    private fun follow() {
        val steps = if (opened) path else row?.steps ?: return
        val ends = endsOf(item ?: return, steps) as? Ends.Value ?: return
        val naming = ends.field as? ReferenceDescription ?: return
        val read = ends.read as? Result.Usable ?: return
        // Open, the one the user chose. On the row, the first that can be opened, which is the
        // one the row is showing.
        val order: Iterable<Int> = if (opened) listOf(chosenEntry) else 0..<howMany(read.value)
        for (at in order) {
            val named = namedAt(read.value, at) ?: continue
            val target = set[named.id] ?: continue
            val to = types.indexOf(target.description)
            if (to < 0) continue
            // Where the user has arrived is a different item, so the way into the last one is
            // not a way into this one.
            path.clear()
            tab = to
            chosen[tab] = set.allOf(target.description).indexOf(target)
            chosenField[tab] = 0
            return
        }
    }

    /** How many values a field holds: one, unless it holds a list of them. */
    private fun howMany(value: Any): Int = if (value is List<*>) value.size else 1

    /**
     * The item named at [at], or absent where nothing is named there.
     *
     * Absent covers a plain name asserting no id and a value that would not read, neither of
     * which names anything to open, and both of which still count as an entry: a cursor moves
     * over what a reader sees, not over what happens to be followable.
     */
    private fun namedAt(value: Any, at: Int): Reference.Identified? = when (value) {
        is Reference.Identified -> if (at == 0) value else null
        is List<*> -> (value.getOrNull(at) as? Element.Usable<*>)?.value as? Reference.Identified
        else -> null
    }

    /**
     * The screen as [height] rows, each exactly [width] characters wide.
     *
     * A tab bar, a rule under it, and then the list on the left against the chosen item's fields
     * on the right. Every row is padded and cut to [width], so what draws this puts down a
     * rectangle and never has to measure anything.
     */
    fun paint(width: Int, height: Int): List<Line> {
        require(width >= LEAST_WIDTH) {
            "a screen should be $LEAST_WIDTH wide at least, was $width"
        }
        require(height >= LEAST_HEIGHT) {
            "a screen should be $LEAST_HEIGHT high at least, was $height"
        }
        val rows = height - CHROME
        val body = if (opened) opened(width, rows) else listed(width, rows)
        val lines = ArrayList<Line>(height)
        lines.add(Line(fitted(listOf(Span(where())), width)))
        lines.add(rule(width))
        lines.addAll(body)
        lines.add(rule(width))
        lines.add(Line(fitted(listOf(Span(actions(width))), width)))
        return lines
    }

    private fun rule(width: Int): Line = Line(listOf(Span("-".repeat(width))))

    /** What is being looked at: the types, or how far into an item a reader has gone. */
    private fun where(): String {
        if (!opened) return tabs()
        val said = path.flatMap { (naming, key) -> listOfNotNull(naming.name, key) }
        return (listOf(type.name, item?.let { set.idOf(it) }) + said).joinToString(" / ")
    }

    /**
     * What the keys do, as many of them as the screen is wide enough to say.
     *
     * Cut from the end rather than squeezed, because half a word about a key is worse than no
     * word about it, and it stops at the first that will not fit rather than passing over it for
     * a shorter one: a bar that keeps its order is one a reader learns the front of. What a key
     * does depends on what is in front of the user, so this says what it does here.
     */
    private fun actions(width: Int): String {
        val said = if (opened) {
            listOf(
                "[esc] back",
                "[up,down] " + moving(),
                if (deeper()) "[enter] open" else FIELD,
                "[space] follow",
            )
        } else {
            listOf(
                "[esc] exit",
                "[<-,->] type",
                "[up,down] item",
                FIELD,
                "[enter] open",
                "[space] follow",
            )
        }
        var shown = said.first()
        for (next in said.drop(1)) {
            if (shown.length + BETWEEN.length + next.length > width) break
            shown += BETWEEN + next
        }
        return shown
    }

    /** What up and down move over where the reader is, in the word for that thing. */
    private fun moving(): String = when (endsOf(item ?: return "scroll", path)) {
        is Ends.Within -> "field"
        is Ends.Keys -> "key"
        is Ends.Value -> if (entries() != null) "value" else "scroll"
        null -> "scroll"
    }

    /** Whether there is anywhere further in to go from where the reader is. */
    private fun deeper(): Boolean = when (endsOf(item ?: return false, path)) {
        is Ends.Within, is Ends.Keys -> true
        else -> false
    }

    /** The list of items against the chosen one's fields. */
    private fun listed(width: Int, rows: Int): List<Line> {
        val listWidth = (width / 3).coerceIn(LEAST_LIST_WIDTH, MOST_LIST_WIDTH)
        scrollTo(chosen[tab], rows)
        val list = ids(rows, listWidth)
        val detail = detailed(rows, width - listWidth - 1)
        return List(rows) { row -> Line(listOf(Span(list[row] + " ")) + detail[row]) }
    }

    /**
     * The chosen field on its own, whole.
     *
     * Where it came from sits at the top, so that a reader who followed a reference into
     * this knows where they are. A value too long for the screen is wrapped and scrolled
     * rather than cut, this being the one place that shows all of it.
     */
    private fun opened(width: Int, rows: Int): List<Line> {
        val item = item
        val ends = if (item == null) null else endsOf(item, path)
        if (ends == null) return List(rows) { Line(fitted(emptyList(), width)) }
        val lines = when (ends) {
            is Ends.Value -> fieldLines(ends.field, ends.read, ends.item, chosenEntry)
            is Ends.Within -> withinLines(ends.item, chosenEntry)
            is Ends.Keys -> keyLines(ends.field, ends.held, chosenEntry)
        }
        val body = lines.flatMap { wrapped(it, width) }
        val at = body.indexOfFirst { row -> row.spans.any { Style.SELECTED in it.styles } }
        showing(at, rows, body.size)
        return List(rows) { row ->
            val spans = body.getOrNull(within + row)?.spans.orEmpty()
            val here = spans.firstOrNull()?.styles.orEmpty().intersect(setOf(Style.SELECTED))
            Line(fitted(spans, width, here))
        }
    }

    /**
     * Move the window as little as it takes to show the row at [at], and no further than the end.
     *
     * Nothing chosen leaves the window alone, which is what scrolling a single value does.
     */
    private fun showing(at: Int, rows: Int, of: Int) {
        if (at >= 0) {
            if (at < within) within = at
            if (at >= within + rows) within = at - rows + 1
        }
        within = within.coerceIn(0, (of - rows).coerceAtLeast(0))
    }

    /**
     * [line] as as many rows as it takes, each at most [width] wide.
     *
     * Broken between characters rather than between words. A value is not prose — an id, a
     * position, a gas mix — and breaking one where a space happens to fall would suggest
     * that the space meant something.
     */
    private fun wrapped(line: Line, width: Int): List<Line> {
        val text = flat(line.text)
        if (text.length <= width) return listOf(line)
        val styles = line.spans.firstOrNull()?.styles.orEmpty()
        return text.chunked(width).map { Line(listOf(Span(it, styles))) }
    }

    /** The tab bar: every type in order, the open one in brackets. */
    private fun tabs(): String =
        types.withIndex().joinToString("  ") { (at, type) ->
            if (at == tab) "[${type.name}]" else " ${type.name} "
        }

    /** The ids of the open type, the chosen one marked, as many as fit. */
    private fun ids(rows: Int, width: Int): List<String> {
        val ids = items.map { set.idOf(it) ?: "?" }
        return List(rows) { row ->
            val at = first[tab] + row
            val text = when {
                ids.isEmpty() && row == 0 -> "  (no ${type.name})"
                at >= ids.size -> ""
                at == chosen[tab] -> "> ${ids[at]}"
                else -> "  ${ids[at]}"
            }
            fit(flat(text), width)
        }
    }

    /**
     * The chosen item's fields, and the fields of anything inside them.
     *
     * A field with nothing in it is still listed, because what a type *can* hold is half of what
     * this interface is for. The chosen one is set apart across its own row, so that the cursor
     * reads as a bar rather than as another mark like the list's; only that row and none of the
     * rows under it, since a whole item reversed is a wall rather than a cursor.
     *
     * Values line up in one column however deep their names sit, which is what lets a reader run
     * an eye down them.
     */
    private fun detailed(rows: Int, width: Int): List<List<Span>> {
        val all = this.rows
        if (all.isEmpty()) return List(rows) { fitted(emptyList(), width) }
        val labelWidth = all.maxOf { it.indent * STEP + it.label.length }
        scrollFields(all, rows)
        return List(rows) { row ->
            val at = deep[tab] + row
            // Marked by where it sits rather than by which object it is: the rows are worked out
            // afresh each time they are asked for, so no two calls give back the same one.
            val here = if (at == chosenField[tab]) setOf(Style.SELECTED) else emptySet()
            val there = all.getOrNull(at)
            if (there == null) fitted(emptyList(), width)
            else fitted(spansOf(there, labelWidth, here), width, here)
        }
    }

    /** One row: its name where its depth puts it, and what it holds in the value column. */
    private fun spansOf(row: Row, labelWidth: Int, here: Set<Style>): List<Span> {
        val name = " ".repeat(row.indent * STEP) + row.label
        return listOf(Span(name.padEnd(labelWidth) + "  ", here)) +
            row.value.map { Span(it.text, it.styles + here) }
    }

    /** Move the column as little as it takes to show the chosen row. */
    private fun scrollFields(all: List<Row>, rows: Int) {
        val at = chosenField[tab]
        if (at >= 0) {
            if (at < deep[tab]) deep[tab] = at
            if (at >= deep[tab] + rows) deep[tab] = at - rows + 1
        }
        deep[tab] = deep[tab].coerceIn(0, (all.size - rows).coerceAtLeast(0))
    }

    /** Move the window as little as the chosen row allows, so the list stays where it was. */
    private fun scrollTo(row: Int, rows: Int) {
        if (row < first[tab]) first[tab] = row
        if (row >= first[tab] + rows) first[tab] = row - rows + 1
        first[tab] = first[tab].coerceIn(0, (items.size - rows).coerceAtLeast(0))
    }

    /**
     * [spans] cut and padded to exactly [width] characters, the padding carrying [fill].
     *
     * The padding is styled with the rest of its row so that a chosen field reads as a bar across
     * the screen rather than stopping where its value happens to end.
     */
    private fun fitted(spans: List<Span>, width: Int, fill: Set<Style> = emptySet()): List<Span> {
        val cut = ArrayList<Span>(spans.size + 1)
        var left = width
        for (span in spans) {
            if (left == 0) break
            cut.add(if (span.text.length <= left) span else Span(span.text.take(left), span.styles))
            left -= cut.last().text.length
        }
        if (left > 0) cut.add(Span(" ".repeat(left), fill))
        return cut
    }

    private fun fit(text: String, width: Int): String =
        if (text.length > width) text.take(width) else text.padEnd(width)

    companion object {

        /** Narrower than this and the list and the fields have nothing to stand in. */
        const val LEAST_WIDTH: Int = 20

        /** The rows that are not body: where the reader is and a rule, a rule and the keys. */
        private const val CHROME = 4

        /** Where the reader is, a rule, one row of body, a rule, and what the keys do. */
        const val LEAST_HEIGHT: Int = CHROME + 1

        /** What stands between one key and the next in the bar. */
        private const val BETWEEN = " | "

        /** Both directions in one, since a key that only goes forwards is half a key. */
        private const val FIELD = "[(shift)-tab] field"

        /** What a line break is shown as, which is how a file writes one. */
        private const val ESCAPED = "\\n"

        private const val LEAST_LIST_WIDTH = 12

        private const val MOST_LIST_WIDTH = 40
    }
}
