package yemoja.ui.tui

import yemoja.data.Cardinality
import yemoja.data.FieldDescription
import yemoja.data.Item
import yemoja.data.ItemDescription
import yemoja.data.ItemSet
import yemoja.data.ReferenceableItem
import yemoja.data.Result
import yemoja.data.Units

/** Key is a keystroke this interface answers to. Everything else is ignored. */
enum class Key { LEFT, RIGHT, UP, DOWN, QUIT }

/**
 * Screen is the whole terminal interface: which tab is open, which item is chosen, and what that
 * looks like as lines of text.
 *
 * **Deliberately not immutable.** Where the user is in the interface is presentation state, which
 * `ui/doc.md` allows a front end to hold, and it changes on every keystroke.
 *
 * It holds no terminal. [paint] answers with lines and [press] takes a key, so what reads the
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

    // One selection and one scroll offset per tab, so that leaving a tab and coming back
    // returns to where the user was rather than to the top.
    private val chosen = IntArray(types.size)

    private val first = IntArray(types.size)

    /** The type whose tab is open. */
    val type: ItemDescription get() = types[tab]

    /** Every item of the open type, in the order the set holds them. */
    val items: List<ReferenceableItem> get() = set.allOf(type)

    /** The chosen item, or absent where the open type has none. */
    val item: ReferenceableItem? get() = items.getOrNull(chosen[tab])

    /**
     * Answer [key], and say whether the interface is still running.
     *
     * Left and right change tab and wrap round, so three tabs are reached in two presses from
     * either end. Up and down move within the list and stop at its ends, because a list that
     * wraps loses the user's place on a long one.
     */
    fun press(key: Key): Boolean {
        when (key) {
            Key.LEFT -> tab = (tab + types.size - 1) % types.size
            Key.RIGHT -> tab = (tab + 1) % types.size
            Key.UP -> chosen[tab] = (chosen[tab] - 1).coerceAtLeast(0)
            Key.DOWN -> chosen[tab] = (chosen[tab] + 1).coerceAtMost(items.size - 1)
            Key.QUIT -> running = false
        }
        // An emptied tab leaves the chosen row past the end, and nothing else corrects it.
        chosen[tab] = chosen[tab].coerceIn(0, (items.size - 1).coerceAtLeast(0))
        return running
    }

    /**
     * The screen as [height] lines, each exactly [width] characters.
     *
     * A tab bar, a rule under it, and then the list on the left against the chosen item's fields
     * on the right. Every line is padded and cut to [width], so what draws this puts down a
     * rectangle and never has to measure anything.
     */
    fun paint(width: Int, height: Int): List<String> {
        require(width >= LEAST_WIDTH) {
            "a screen should be $LEAST_WIDTH wide at least, was $width"
        }
        require(height >= LEAST_HEIGHT) {
            "a screen should be $LEAST_HEIGHT high at least, was $height"
        }
        val rows = height - 2
        val listWidth = (width / 3).coerceIn(LEAST_LIST_WIDTH, MOST_LIST_WIDTH)
        scrollTo(chosen[tab], rows)
        val list = listed(rows, listWidth)
        val detail = detailed(rows, width - listWidth - 1)
        val lines = ArrayList<String>(height)
        lines.add(fitted(tabs(), width))
        lines.add("-".repeat(width))
        for (row in 0..<rows) lines.add(fitted("${list[row]} ${detail[row]}", width))
        return lines
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
            fitted(text, width)
        }
    }

    /**
     * Every single-valued field of the chosen item, as a label and what it holds.
     *
     * Fields holding more than one value are left out until it is settled how they are shown.
     * A field with nothing in it is still listed, because what a type *can* hold is half of what
     * this interface is for.
     */
    private fun detailed(rows: Int, width: Int): List<String> {
        val item = item ?: return List(rows) { fitted("", width) }
        val fields = type.fields.filter { it.cardinality == Cardinality.SINGLE }
        val labelWidth = fields.maxOf { it.label.length }
        val lines = fields.map { "${it.label.padEnd(labelWidth)}  ${shown(item, it)}" }
        return List(rows) { fitted(lines.getOrElse(it) { "" }, width) }
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

    /** Move the window as little as the chosen row allows, so the list stays where it was. */
    private fun scrollTo(row: Int, rows: Int) {
        if (row < first[tab]) first[tab] = row
        if (row >= first[tab] + rows) first[tab] = row - rows + 1
        first[tab] = first[tab].coerceIn(0, (items.size - rows).coerceAtLeast(0))
    }

    private fun fitted(text: String, width: Int): String =
        if (text.length > width) text.take(width) else text.padEnd(width)

    companion object {

        /** Narrower than this and the list and the fields have nothing to stand in. */
        const val LEAST_WIDTH: Int = 20

        /** The tab bar, its rule, and one row of list. */
        const val LEAST_HEIGHT: Int = 3

        private const val LEAST_LIST_WIDTH = 12

        private const val MOST_LIST_WIDTH = 40
    }
}
