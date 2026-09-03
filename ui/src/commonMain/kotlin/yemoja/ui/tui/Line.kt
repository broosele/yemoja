package yemoja.ui.tui

/**
 * Style is how a piece of text is set apart from the text around it.
 *
 * What each means is a rule about the value underneath, not a decoration: a reader can tell a
 * worked-out value from a written one without selecting it, which is the whole reason a raw
 * interface bothers with any of this.
 */
enum class Style {

    /** A value the file holds on a field that would otherwise work one out. */
    BOLD,

    /** A value nothing wrote, worked out from the others. */
    ITALIC,

    /** A value naming another item, which can be followed. */
    UNDERLINED,

    /** Where the user is. Not a property of the value, and the only one of these that moves. */
    SELECTED,
}

/**
 * Span is a run of text on one line, and how it is shown.
 *
 * Immutable.
 */
class Span(val text: String, val styles: Set<Style> = emptySet()) {

    override fun toString(): String = if (styles.isEmpty()) text else "$text$styles"
}

/**
 * Line is one row of the screen, as the runs of text it is made of.
 *
 * Immutable.
 *
 * Spans rather than text with escape codes in it, so that nothing here knows what a terminal
 * understands and the width of a row is the number of characters a reader sees. What turns a
 * style into something a terminal does is the one file that talks to one.
 */
class Line(spans: List<Span>) {

    val spans: List<Span> = spans.toList()

    /** Everything on the row, without any of it being set apart. */
    val text: String get() = spans.joinToString("") { it.text }

    /** How many characters wide the row is. */
    val width: Int get() = spans.sumOf { it.text.length }

    override fun toString(): String = text
}
