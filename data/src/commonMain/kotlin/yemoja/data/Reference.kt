package yemoja.data

/**
 * A field's way of naming another item.
 *
 * Two kinds, and the difference is identity rather than form. `@john` asserts that this is
 * a particular person: two of them name the same one whether or not that person is in the
 * logbook. A plain `john` asserts nothing of the sort, so two of them may be two people who
 * share a name.
 *
 * A reference to an item that is not in the logbook is still a reference. It names someone
 * who has not been entered yet, which is not a fault in the value. See [Referent] and
 * `DATA-66`.
 *
 * Immutable.
 */
sealed class Reference {

    /** As written in a file, `@` and all. */
    abstract val written: String

    /** `@john`. */
    data class Identified(val id: String) : Reference() {
        init {
            require(id.isNotEmpty()) { "an id should not be empty" }
            require(id.none { it == '*' || it.isWhitespace() }) {
                "an id should hold no spaces and no *, but was $id"
            }
        }

        override val written: String get() = "@$id"
    }

    /** `john`. A name where a reference could have gone, asserting no id. */
    data class OneOff(val name: String) : Reference() {
        init {
            require(name.isNotEmpty()) { "a name should not be empty" }
            require(!name.startsWith('@')) {
                "a name should not begin with @, which would make it an id: $name"
            }
        }

        override val written: String get() = name
    }

    companion object {

        /**
         * Reads a reference. Throws where the text is not one.
         *
         * [oneOff] says whether a plain name is allowed here. Where it is not, text without
         * a leading `@` is not a reference at all.
         */
        fun parse(text: String, oneOff: Boolean): Reference {
            val written = text.trim()
            if (written.startsWith('@')) {
                return try {
                    Identified(written.substring(1))
                } catch (impossible: IllegalArgumentException) {
                    throw ValueFormatException("$text: ${impossible.message}")
                }
            }
            if (!oneOff) throw ValueFormatException("$text is not a reference, which begins with @")
            return try {
                OneOff(written)
            } catch (impossible: IllegalArgumentException) {
                throw ValueFormatException("$text: ${impossible.message}")
            }
        }
    }
}

/**
 * One entry inside the item being read, written `*p1`.
 *
 * Not a [Reference]: it names a part of one item rather than an item, so what resolves it
 * is the collection the description points into, not the set of all items.
 *
 * Immutable.
 */
data class KeyReference(val key: String) {

    init {
        require(key.isNotEmpty()) { "a key should not be empty" }
        require(key.none { it == '*' || it.isWhitespace() }) {
            "a key should hold no spaces and no *, but was $key"
        }
    }

    val written: String get() = "*$key"

    override fun toString(): String = written

    companion object {

        fun parse(text: String): KeyReference {
            val written = text.trim()
            if (!written.startsWith('*')) {
                throw ValueFormatException("$text is not a key reference, which begins with *")
            }
            return try {
                KeyReference(written.substring(1))
            } catch (impossible: IllegalArgumentException) {
                throw ValueFormatException("$text: ${impossible.message}")
            }
        }
    }
}

/**
 * What a reference turned out to point at.
 *
 * Four answers rather than an item or nothing, because the ways of having no item differ
 * and an interface says different things about them. A dangling reference is a person you
 * have not entered yet; a one-off is a name you chose not to make anyone.
 *
 * Not immutable, alone among these: [Resolved] holds an [Item], and items change.
 */
sealed class Referent {

    /** The reference named an item, and it is here. */
    class Resolved(val item: Item) : Referent()

    /** The reference named an item that is not in the set. Still an identity. */
    class Dangling(val id: String) : Referent()

    /** A name was written where a reference could have gone. There is nothing to find. */
    class OneOff(val name: String) : Referent()

    /** No reference to follow: the field was empty, or held something unreadable. */
    object None : Referent()
}
