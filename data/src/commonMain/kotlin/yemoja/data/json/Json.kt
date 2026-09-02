package yemoja.data.json

import yemoja.data.Stored

/**
 * JsonFormatException is thrown when a text is not JSON at all.
 *
 * A file rather than a value. `ValueFormatException` says a value does not say what it claims to;
 * this says the text could not be read far enough to find values in it.
 *
 * It carries a line and a column, because the logbook is meant to be edited by hand and a reader
 * who is told only *that* it failed has to hunt.
 */
class JsonFormatException(message: String) : RuntimeException(message)

/**
 * Json reads a JSON text into a [Stored] tree.
 *
 * Read but not judged: nothing here knows what a field is, so `"max_depth": "deep"` reads happily
 * as text and is refused later by the description. That division is `DATA-64`.
 *
 * It builds the data layer's own tree rather than one of its own, so the walk from a description
 * to an item's fields belongs to the layer that owns the rules and not to this source.
 */
object Json {

    /**
     * Reads [text] as JSON. Throws [JsonFormatException] where it is not.
     *
     * Two forgivenesses, in the reading only, and a writer offers neither. A **trailing comma**
     * is allowed after the last member or element, because a hand-editor who deletes a line
     * should not have to fix the line above it. And a **byte order mark** at the very start is
     * skipped: editors on Windows leave one, it cannot be seen, and refusing it would point at a
     * brace the reader can find nothing wrong with.
     */
    fun parse(text: String): Stored = Reader(text).read()
}

/**
 * How deep the reader will go before it refuses.
 *
 * The format nests four or five levels. A recursive reader on a corrupt file of nothing but `[`
 * would otherwise run out of stack, which is not a failure anyone can act on.
 */
private const val DEEPEST = 64

/**
 * What an editor on Windows may leave at the start of a file.
 *
 * Invisible, and not a value. Only the one at the very start means this; anywhere else it
 * is a character like any other, and the text rules refuse it.
 */
private const val MARK = '\uFEFF'

/** Said wherever a value was expected and something else was there. */
private const val BEGINS = "a value should begin with {, [, a quote, a digit, true, false or null"

private class Reader(private val text: String) {

    private var at = 0
    private var depth = 0

    fun read(): Stored {
        if (text.startsWith(MARK)) at += 1
        skipSpace()
        val value = readValue()
        skipSpace()
        if (at < text.length) fail("nothing should follow the value")
        return value
    }

    private fun readValue(): Stored {
        if (at >= text.length) fail("a value should be here, but the text ended")
        if (depth >= DEEPEST) fail("a value should not be nested more than $DEEPEST deep")
        return when (val start = text[at]) {
            '{' -> readMembers()
            '[' -> readElements()
            '"' -> Stored.Leaf(readText())
            't', 'f', 'n' -> readWord()
            else ->
                if (start == '-' || start in '0'..'9') readNumber()
                else fail(BEGINS)
        }
    }

    private fun readMembers(): Stored {
        val members = LinkedHashMap<String, Stored>()
        depth += 1
        at += 1
        skipSpace()
        if (peek() == '}') {
            at += 1
            depth -= 1
            return Stored.Members(members)
        }
        while (true) {
            skipSpace()
            if (peek() != '"') fail("a name should be in quotes")
            val name = readText()
            if (name in members) fail("$name should name one member, but names two")
            skipSpace()
            if (peek() != ':') fail("a name should be followed by a colon")
            at += 1
            skipSpace()
            members[name] = readValue()
            skipSpace()
            when (peek()) {
                ',' -> {
                    at += 1
                    skipSpace()
                    if (peek() == '}') break      // the trailing comma
                }

                '}' -> break
                else -> fail("a member should be followed by a comma or a closing brace")
            }
        }
        at += 1
        depth -= 1
        return Stored.Members(members)
    }

    private fun readElements(): Stored {
        val elements = ArrayList<Stored>()
        depth += 1
        at += 1
        skipSpace()
        if (peek() == ']') {
            at += 1
            depth -= 1
            return Stored.Elements(elements)
        }
        while (true) {
            skipSpace()
            elements.add(readValue())
            skipSpace()
            when (peek()) {
                ',' -> {
                    at += 1
                    skipSpace()
                    if (peek() == ']') break      // the trailing comma
                }

                ']' -> break
                else -> fail("an element should be followed by a comma or a closing bracket")
            }
        }
        at += 1
        depth -= 1
        return Stored.Elements(elements)
    }

    private fun readText(): String {
        at += 1
        val read = StringBuilder()
        while (true) {
            if (at >= text.length) fail("a string should be closed with a quote")
            when (val character = text[at]) {
                '"' -> {
                    at += 1
                    return read.toString()
                }

                '\\' -> {
                    at += 1
                    read.append(readEscape())
                }

                else -> {
                    if (character.code < 0x20) {
                        fail("a string should not hold a control character")
                    }
                    read.append(character)
                    at += 1
                }
            }
        }
    }

    /**
     * One escape, the backslash already read.
     *
     * A surrogate pair needs no joining. A Kotlin string is UTF-16, so appending the two
     * escaped halves in turn gives the character they stand for.
     */
    private fun readEscape(): Char {
        if (at >= text.length) fail("an escape should be followed by what it escapes")
        val marker = text[at]
        at += 1
        return when (marker) {
            '"' -> '"'
            '\\' -> '\\'
            '/' -> '/'
            'b' -> '\u0008'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> readHex()
            else -> fail("$marker should not follow a backslash")
        }
    }

    private fun readHex(): Char {
        if (at + 4 > text.length) fail("an escaped character should have four hexadecimal digits")
        var code = 0
        repeat(4) {
            val digit = hexValue(text[at])
                ?: fail("an escaped character should have four hexadecimal digits")
            code = code * 16 + digit
            at += 1
        }
        return code.toChar()
    }

    /**
     * What [character] is worth as a hexadecimal digit, or nothing where it is not one.
     *
     * Written out rather than taken from `digitToIntOrNull`, which knows every digit Unicode has.
     * The grammar says four hexadecimal digits and means the sixteen ASCII ones, so an
     * Arabic-Indic numeral is not one of them.
     */
    private fun hexValue(character: Char): Int? = when (character) {
        in '0'..'9' -> character - '0'
        in 'a'..'f' -> character - 'a' + 10
        in 'A'..'F' -> character - 'A' + 10
        else -> null
    }

    /** One of the three values written as a word. */
    private fun readWord(): Stored = when {
        text.startsWith("true", at) -> {
            at += 4
            Stored.Leaf(true)
        }

        text.startsWith("false", at) -> {
            at += 5
            Stored.Leaf(false)
        }

        text.startsWith("null", at) -> {
            at += 4
            Stored.Leaf(null)
        }

        else -> fail(BEGINS)
    }

    private fun readNumber(): Stored {
        val start = at
        if (peek() == '-') at += 1
        readDigits(atLeastOne = true)
        if (text.startsWith("0", start) || text.startsWith("-0", start)) {
            val afterZero = if (text[start] == '-') start + 2 else start + 1
            if (afterZero < at) fail("a number should not begin with a zero")
        }
        var fractional = false
        if (at < text.length && text[at] == '.') {
            fractional = true
            at += 1
            readDigits(atLeastOne = true)
        }
        if (at < text.length && (text[at] == 'e' || text[at] == 'E')) {
            fractional = true
            at += 1
            if (at < text.length && (text[at] == '+' || text[at] == '-')) at += 1
            readDigits(atLeastOne = true)
        }
        val written = text.substring(start, at)
        if (fractional) {
            val value = written.toDoubleOrNull()
                ?: fail("$written should be a number this machine can hold")
            if (value.isInfinite()) fail("$written should be a number this machine can hold")
            return Stored.Leaf(value)
        }
        val value = written.toLongOrNull()
            ?: fail("$written should be a whole number this machine can hold")
        return Stored.Leaf(value)
    }

    private fun readDigits(atLeastOne: Boolean) {
        val start = at
        while (at < text.length && text[at] in '0'..'9') at += 1
        if (atLeastOne && at == start) fail("a number should have a digit here")
    }

    private fun skipSpace() {
        while (at < text.length && (text[at] == ' ' || text[at] == '\t' ||
                text[at] == '\n' || text[at] == '\r')
        ) {
            at += 1
        }
    }

    private fun peek(): Char {
        if (at >= text.length) fail("the text ended before the value did")
        return text[at]
    }

    private fun fail(what: String): Nothing {
        var line = 1
        var column = 1
        for (index in 0 until minOf(at, text.length)) {
            if (text[index] == '\n') {
                line += 1
                column = 1
            } else {
                column += 1
            }
        }
        throw JsonFormatException("$what, at line $line column $column")
    }
}
