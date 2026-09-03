package yemoja.ui.tui

import yemoja.data.Cardinality
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

    // How far down the open field is scrolled. One offset, since only one is ever open.
    private var within = 0

    /** The type whose tab is open. */
    val type: ItemDescription get() = types[tab]

    /** Every item of the open type, in the order the set holds them. */
    val items: List<ReferenceableItem> get() = set.allOf(type)

    /** The chosen item, or absent where the open type has none. */
    val item: ReferenceableItem? get() = items.getOrNull(chosen[tab])

    /**
     * The fields shown, which are those holding one value.
     *
     * The rest are left out until it is settled how a list, a keyed collection and a series are
     * each shown, so nothing here has to guess at one.
     */
    val fields: List<FieldDescription>
        get() = type.fields.filter { it.cardinality == Cardinality.SINGLE }

    /** The chosen field, or absent where the open type has none. */
    val field: FieldDescription? get() = fields.getOrNull(chosenField[tab])

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
            // Up and down move in whatever is in front of the user: the list, or an open
            // field too long to see at once.
            Key.UP -> if (opened) within = (within - 1).coerceAtLeast(0)
            else chosen[tab] = (chosen[tab] - 1).coerceAtLeast(0)

            Key.DOWN -> if (opened) within += 1
            else chosen[tab] = (chosen[tab] + 1).coerceAtMost(items.size - 1)
            Key.NEXT_FIELD -> step(1)
            Key.PREVIOUS_FIELD -> step(-1)
            Key.FOLLOW -> follow()
            Key.OPEN -> opened = opened || (item != null && field != null)
            // One key closes what is open and leaves where nothing is, so what it shuts is
            // always whatever is in front of the user.
            Key.CLOSE -> if (opened) opened = false else running = false
            Key.QUIT -> running = false
        }
        if (!opened) within = 0
        // An emptied tab leaves the chosen row past the end, and nothing else corrects it.
        chosen[tab] = chosen[tab].coerceIn(0, (items.size - 1).coerceAtLeast(0))
        chosenField[tab] = chosenField[tab].coerceIn(0, (fields.size - 1).coerceAtLeast(0))
        return running
    }

    /** Move [by] fields, round the end. */
    private fun step(by: Int) {
        if (fields.isEmpty()) return
        chosenField[tab] = (chosenField[tab] + by + fields.size) % fields.size
    }

    /**
     * Open the item the chosen field names, where it names one that is there.
     *
     * A field that is not a reference, a reference to nothing, and a reference to an item of a
     * type this screen has no tab for are all the same answer: stay where we are. Following is
     * one way — nothing records where the user came from, so there is no going back yet.
     */
    private fun follow() {
        if (opened) return
        val naming = field as? ReferenceDescription ?: return
        val read = item?.read(naming.name) as? Result.Usable ?: return
        val named = read.value as? Reference.Identified ?: return
        val target = set[named.id] ?: return
        val at = types.indexOf(target.description)
        if (at < 0) return
        tab = at
        chosen[tab] = set.allOf(target.description).indexOf(target)
        chosenField[tab] = 0
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
        val body = fieldLines(item, field).flatMap { wrapped(it, width) }
        within = within.coerceIn(0, (body.size - rows).coerceAtLeast(0))
        val lines = ArrayList<Line>(rows + 2)
        lines.add(Line(fitted(listOf(Span(where)), width)))
        lines.add(Line(listOf(Span("-".repeat(width)))))
        for (row in 0..<rows) {
            lines.add(Line(fitted(body.getOrNull(within + row)?.spans.orEmpty(), width)))
        }
        return lines
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
            is Result.Usable -> field.format(read.value, Units.DEFAULT)
            is Result.Unusable -> "! ${read.reason}"
            Result.Absent -> ""
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
        if (text.length <= VALUE_WIDTH) text else text.take(VALUE_WIDTH - 1) + MORE

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

        /** That a value goes on past where the list stopped showing it. */
        private const val MORE = "\u2026"

        private const val LEAST_LIST_WIDTH = 12

        private const val MOST_LIST_WIDTH = 40
    }
}
