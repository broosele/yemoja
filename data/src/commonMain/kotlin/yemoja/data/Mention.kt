package yemoja.data

/**
 * Mention is a place in free text where an id appears to have been written.
 *
 * Immutable.
 *
 * **A candidate, not a reference.** `JSON-23` makes a mention a convention that binds nobody: the
 * text is the value, nothing resolves on load, and one naming an item that is not there is text
 * that happens to look like a mention. So this says where a candidate sits and what it would
 * name, and whoever asked decides whether anything is there.
 *
 * [at] is where it sits in the text, the `@` included, so an interface can mark it in place.
 */
data class Mention(val at: IntRange, val id: String)

/**
 * Every candidate in [text], in the order they are written.
 *
 * After an `@`, the longest run of ASCII letters, digits, `_`, `-`, `.` and `#` — the characters
 * `DATA-84` allows in an id. A trailing `_`, `-`, `.` or `#` comes off, no id ending in one, so a
 * full stop closing a sentence stays a full stop. An empty run is no candidate. What is left is
 * folded to lowercase, an id being lowercase ASCII exactly so that `Anna` and `anna` cannot
 * be two of them, so `@Willy` opening a sentence finds *willy*.
 *
 * **Nothing says where a mention may begin.** An `@` may be an address, a handle from another
 * system, or the word *at* — `max depth @ 22m` — and no rule separates those from a name,
 * because what separates them is intent. What makes a candidate safe is resolution:
 * `@example.invalid` inside an address is looked up, found to be nothing, and goes on being
 * the text it was.
 *
 * Examples: `We met @willy, who was on his @padi_wreck course.` gives *willy* and *padi_wreck*.
 * `Mail him on tom@example.invalid` gives *example.invalid*, which is nobody.
 */
fun mentionsIn(text: String): List<Mention> {
    val found = ArrayList<Mention>()
    var at = text.indexOf(MARKER)
    while (at >= 0) {
        var end = at + 1
        while (end < text.length && text[end].isIdCharacter()) end++
        while (end > at + 1 && text[end - 1] in TRAILING) end--
        if (end > at + 1) {
            found.add(Mention(at..<end, text.substring(at + 1, end).lowercase()))
        }
        at = text.indexOf(MARKER, (end).coerceAtLeast(at + 1))
    }
    return found
}

/** What an id may hold, either case, since a candidate is folded before it is looked up. */
private fun Char.isIdCharacter(): Boolean =
    this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in "_-.#"

/** What no id ends with, so a trailing one is punctuation rather than part of the name. */
private const val TRAILING = "_-.#"

/** What marks a mention, the same character a reference field carries. `JSON-19`. */
private const val MARKER = '@'
