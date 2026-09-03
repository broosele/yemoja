package yemoja.data

/**
 * Reference is a field's way of naming another item.
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
    abstract val asWritten: String

    // Final, so that the two below stay out of the business of saying what they are. A data class
    // writes its own unless a superclass has settled it, and `Identified(id=tuesday)` is not a
    // reference: DATA-76 has a writer read back what format wrote, and that would not survive it.
    final override fun toString(): String = asWritten

    /** Identified is a reference to a particular item, written `@john`. */
    data class Identified(val id: String) : Reference() {
        init {
            require(id.isNotEmpty()) { "an id should not be empty" }
            require(id.none { it == '*' || it.isWhitespace() }) {
                "an id should hold no spaces and no *, but was $id"
            }
        }

        override val asWritten: String get() = "@$id"
    }

    /** OneOff is a name where a reference could have gone, written `john`, asserting no id. */
    data class OneOff(val name: String) : Reference() {
        init {
            require(name.isNotEmpty()) { "a name should not be empty" }
            require(!name.startsWith('@')) {
                "a name should not begin with @, which would make it an id: $name"
            }
        }

        override val asWritten: String get() = name
    }

    companion object {

        /**
         * Reads a reference. Throws where the text is not one.
         *
         * [oneOffAllowed] says whether a plain name may stand in here. Where it may not, text
         * a leading `@` is not a reference at all.
         */
        fun parse(text: String, oneOffAllowed: Boolean): Reference {
            val written = text.trim()
            if (written.startsWith('@')) {
                return try {
                    Identified(written.substring(1))
                } catch (impossible: IllegalArgumentException) {
                    throw ValueFormatException("$text: ${impossible.message}")
                }
            }
            if (!oneOffAllowed) {
                throw ValueFormatException("$text is not a reference, which begins with @")
            }
            return try {
                OneOff(written)
            } catch (impossible: IllegalArgumentException) {
                throw ValueFormatException("$text: ${impossible.message}")
            }
        }
    }
}

/**
 * KeyReference names one entry inside the item being read.
 *
 * Not a [Reference]: an id names an item anywhere in the logbook, while a key names an owned item
 * relative to its owner, so what resolves it is the collection the description points into rather
 * than the set of all items.
 *
 * Immutable.
 *
 * An entry keyed `p1` is written `*p1`.
 */
data class KeyReference(val key: String) {

    init {
        require(key.isNotEmpty()) { "a key should not be empty" }
        require(key.none { it == '*' || it.isWhitespace() }) {
            "a key should hold no spaces and no *, but was $key"
        }
    }

    val asWritten: String get() = "*$key"

    override fun toString(): String = asWritten

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
 * Referent is what a reference turned out to point at.
 *
 * Four answers rather than an item or nothing, because the ways of having no item differ
 * and an interface says different things about them. A dangling reference is a person you
 * have not entered yet; a one-off is a name you chose not to make anyone.
 *
 * Not immutable: [Resolved] holds an item, and items change.
 */
sealed class Referent {

    /** Resolved is a reference that named an item, and the item is here. */
    class Resolved(val item: ReferenceableItem) : Referent()

    /** Dangling is a reference that named an item not in the set. Still an identity. */
    class Dangling(val id: String) : Referent()

    /** OneOff is a name written where a reference could have gone. There is nothing to find. */
    class OneOff(val name: String) : Referent()

    /** None is no reference to follow: the field was empty, or held something unreadable. */
    object None : Referent()
}
