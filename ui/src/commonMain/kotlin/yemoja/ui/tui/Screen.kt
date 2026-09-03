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

    /** Whether the chosen field is open on its own, rather than the list being shown. */
    var opened: Boolean = false
        private set

    // How far down the open field is scrolled, and which of its values is chosen. One of each,
    // since only one field is ever open.
    private var within = 0

    private var chosenEntry = 0

    /** The type whose tab is open. */
    val type: ItemDescription get() = types[tab]

    /** Every item of the open type, in the order the set holds them. */
    val items: List<ReferenceableItem> get() = set.allOf(type)

    /** The chosen item, or absent where the open type has none. */
    val item: ReferenceableItem? get() = items.getOrNull(chosen[tab])

    /**
     * The fields shown, which are those holding one value and those holding a list of them.
     *
     * A keyed collection and a series are left out until it is settled how each is shown, so
     * nothing here has to guess at one.
     */
    val fields: List<FieldDescription>
        get() = type.fields.filter { it.cardinality in SHOWN }

    /** The chosen field, or absent where the open type has none. */
    val field: FieldDescription? get() = fields.getOrNull(chosenField[tab])

    /**
     * How many values the chosen field holds, or absent where it does not hold a list of them.
     *
     * What decides whether there is anything to move between inside an open field. A single
     * value is one thing however many rows it wraps over.
     *
     * A function rather than a property, because inside a property's own accessor `field` is
     * Kotlin's word for the backing field rather than this class's.
     */
    private fun entries(): Int? {
        val naming = field ?: return null
        if (naming.cardinality != Cardinality.LIST) return null
        val read = item?.read(naming.name) as? Result.Usable ?: return null
        return (read.value as? List<*>)?.size
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
            Key.OPEN -> opened = opened || (item != null && field != null)
            // One key closes what is open and leaves where nothing is, so what it shuts is
            // always whatever is in front of the user.
            Key.CLOSE -> if (opened) opened = false else running = false
            Key.QUIT -> running = false
        }
        if (!opened) {
            within = 0
            chosenEntry = 0
        }
        // An emptied tab leaves the chosen row past the end, and nothing else corrects it.
        chosen[tab] = chosen[tab].coerceIn(0, (items.size - 1).coerceAtLeast(0))
        chosenField[tab] = chosenField[tab].coerceIn(0, (fields.size - 1).coerceAtLeast(0))
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

    /** Move [by] fields, round the end. */
    private fun step(by: Int) {
        if (fields.isEmpty()) return
        chosenField[tab] = (chosenField[tab] + by + fields.size) % fields.size
    }

    /**
     * Open the item the chosen field names, where it names one that is there.
     *
     * **The first one that can be opened**, which for a field naming several is the one the row
     * is showing. A field that is not a reference, a reference to nothing, a plain name asserting
     * no id, and a reference to a type this screen has no tab for are all the same answer: stay
     * where we are.
     *
     * Following is one way — nothing records where the user came from, so there is no going back
     * yet. Which of several to follow, where a reader wants one further down the row, wants a
     * cursor inside the field and is not settled.
     */
    private fun follow() {
        val naming = field as? ReferenceDescription ?: return
        val read = item?.read(naming.name) as? Result.Usable ?: return
        // Open, the one the user chose. On the row, the first that can be opened, which is the
        // one the row is showing.
        val order: Iterable<Int> = if (opened) listOf(chosenEntry) else 0..<howMany(read.value)
        for (at in order) {
            val named = namedAt(read.value, at) ?: continue
            val target = set[named.id] ?: continue
            val to = types.indexOf(target.description)
            if (to < 0) continue
            // Where the user has arrived is a different field, so the one they opened is shut.
            opened = false
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
        val rows = height - 2
        if (opened) return opened(width, rows)
        val listWidth = (width / 3).coerceIn(LEAST_LIST_WIDTH, MOST_LIST_WIDTH)
        scrollTo(chosen[tab], rows)
        val list = listed(rows, listWidth)
        val detail = detailed(rows, width - listWidth - 1)
        val lines = ArrayList<Line>(height)
        lines.add(Line(fitted(listOf(Span(tabs())), width)))
        lines.add(Line(listOf(Span("-".repeat(width)))))
        for (row in 0..<rows) lines.add(Line(listOf(Span(list[row] + " ")) + detail[row]))
        return lines
    }

    /**
     * The chosen field on its own, whole.
     *
     * Where it came from sits at the top, so that a reader who followed a reference into
     * this knows where they are. A value too long for the screen is wrapped and scrolled
     * rather than cut, this being the one place that shows all of it.
     */
    private fun opened(width: Int, rows: Int): List<Line> {
        val item = item ?: return emptyList()
        val field = field ?: return emptyList()
        val where = "${type.name} / ${set.idOf(item)} / ${field.name}"
        val body = fieldLines(item, field, chosenEntry).flatMap { wrapped(it, width) }
        val at = body.indexOfFirst { row -> row.spans.any { Style.SELECTED in it.styles } }
        showing(at, rows, body.size)
        val lines = ArrayList<Line>(rows + 2)
        lines.add(Line(fitted(listOf(Span(where)), width)))
        lines.add(Line(listOf(Span("-".repeat(width)))))
        for (row in 0..<rows) {
            val spans = body.getOrNull(within + row)?.spans.orEmpty()
            val here = spans.firstOrNull()?.styles.orEmpty().intersect(setOf(Style.SELECTED))
            lines.add(Line(fitted(spans, width, here)))
        }
        return lines
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
    private fun listed(rows: Int, width: Int): List<String> {
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
     * Every shown field of the chosen item, as a label and what it holds.
     *
     * A field with nothing in it is still listed, because what a type *can* hold is half of what
     * this interface is for. The chosen one is set apart whole, label and value together, so that
     * the cursor reads as a bar rather than as another mark like the list's.
     */
    private fun detailed(rows: Int, width: Int): List<List<Span>> {
        val item = item ?: return List(rows) { fitted(emptyList(), width) }
        val labelWidth = fields.maxOf { it.label.length }
        val lines = fields.mapIndexed { at, field ->
            val here = if (at == chosenField[tab]) setOf(Style.SELECTED) else emptySet()
            here to listOf(
                Span(field.label.padEnd(labelWidth) + "  ", here),
                Span(cut(flat(shown(item, field))), styles(item, field) + here),
            )
        }
        return List(rows) { row ->
            val line = lines.getOrNull(row)
            fitted(line?.second.orEmpty(), width, line?.first.orEmpty())
        }
    }

    /**
     * What one field holds, as a user should see it.
     *
     * The written form, which is the form the file holds. A raw interface showing what is on disk
     * is the point of this one, and display preference is a settled question nobody has answered.
     * `UI-2`.
     *
     * A value that could not be read shows why instead of showing nothing, since a blank is what
     * an absent field looks like and the two are not the same thing.
     */
    private fun shown(item: Item, field: FieldDescription): String =
        when (val read = item.read(field.name)) {
            is Result.Usable -> shortened(entriesOf(field, read.value).map { flat(it.text) })
            is Result.Unusable -> cut(flat("! ${read.reason}"))
            Result.Absent -> ""
        }

    /**
     * Several values on one row: as many as fit, and how many did not.
     *
     * Saying how many were left out is what a plain cut cannot: three dots at the end of a list
     * of regions could be one more or forty, and the difference is what decides whether opening
     * the field is worth it.
     */
    private fun shortened(entries: List<String>): String {
        if (entries.isEmpty()) return EMPTY
        val whole = entries.joinToString(SEPARATOR)
        if (entries.size <= 1 || whole.length <= VALUE_WIDTH) return cut(whole)
        for (count in entries.size - 1 downTo 1) {
            val rest = entries.size - count
            val said = entries.take(count).joinToString(SEPARATOR) +
                " $MORE ($rest other${if (rest == 1) "" else "s"})"
            if (said.length <= VALUE_WIDTH) return said
        }
        // Not even the first entry fits beside the count, so only the count is worth saying.
        return cut("(${entries.size} entries)")
    }

    /**
     * How a field's value is set apart, which says what kind of value it is.
     *
     * Underlined names another item, so a reader knows what can be followed before trying it.
     * Bold is a value written over one that would have been worked out, and italic one that was
     * worked out. Plain is a value simply written, which is most of them.
     */
    private fun styles(item: Item, field: FieldDescription): Set<Style> {
        val styles = mutableSetOf<Style>()
        if (field is ReferenceDescription) styles.add(Style.UNDERLINED)
        when ((item.read(field.name) as? Result.Usable)?.origin) {
            Result.Origin.OVERRIDDEN -> styles.add(Style.BOLD)
            Result.Origin.DERIVED -> styles.add(Style.ITALIC)
            else -> Unit
        }
        return styles
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

    /**
     * [text] short enough to sit in the list beside its neighbours.
     *
     * A column wide enough for the longest remark anybody writes would be a column of
     * mostly nothing, so a value is cut here and shown whole where it is opened. The mark
     * says which values have more to them, which a value simply ending does not.
     */
    private fun cut(text: String): String =
        if (text.length <= VALUE_WIDTH) text
        else text.take(VALUE_WIDTH - MORE.length) + MORE

    /**
     * [text] with no line break left in it.
     *
     * `remarks` is multiline and a terminal row is not, so a break is shown as the escape a file
     * writes it with rather than taken: a value holding one would otherwise occupy three rows
     * while measuring as one, and in raw mode leave the cursor wherever the last row ended.
     */
    private fun flat(text: String): String =
        text.replace("\r\n", ESCAPED).replace("\n", ESCAPED).replace("\r", ESCAPED)

    companion object {

        /** Narrower than this and the list and the fields have nothing to stand in. */
        const val LEAST_WIDTH: Int = 20

        /** The tab bar, its rule, and one row of list. */
        const val LEAST_HEIGHT: Int = 3

        /** What a line break is shown as, which is how a file writes one. */
        private const val ESCAPED = "\\n"

        /** How much of a value the list shows. Past this it is cut and marked. */
        const val VALUE_WIDTH: Int = 32

        /**
         * That a value goes on past where the list stopped showing it.
         *
         * Three dots rather than the one character that means them. A Windows
         * console on a code page that is not UTF-8 shows that character as a
         * question mark, which reads as a value nobody could make sense of
         * rather than as a value that was cut.
         */
        private const val MORE = "..."

        /** What sits between two values of one field on a row. */
        private const val SEPARATOR = ", "

        /** A list somebody wrote with nothing in it, which is not a field nobody wrote. */
        private const val EMPTY = "(empty)"

        /** The cardinalities this interface knows how to show. */
        private val SHOWN = setOf(Cardinality.SINGLE, Cardinality.LIST)

        private const val LEAST_LIST_WIDTH = 12

        private const val MOST_LIST_WIDTH = 40
    }
}
