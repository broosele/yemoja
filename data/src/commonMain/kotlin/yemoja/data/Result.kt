package yemoja.data

/**
 * Result is what reading a field gave: a value, nothing, or something that cannot be used.
 *
 * Three states, not a nullable. Absent and unusable are the two a user most needs told apart.
 * `DATA-50`.
 *
 * The value is non-null by the bound on [T]: a usable nothing should be an absent.
 *
 * Immutable. What a [Usable] carries is immutable only where the value itself is.
 */
sealed class Result<out T : Any> {

    /**
     * Origin is where a usable value came from.
     *
     * Not *source*, which is the store it was read from. `DATA-64`.
     *
     * One of three, not a flag each: they exclude one another, and a description settles which
     * are possible before a file is read.
     */
    enum class Origin {

        /** Written in the file, on a field that records what it is given. */
        STORED,

        /** Worked out, and nothing was stored to work out from. A default is this. */
        DERIVED,

        /** Written in the file, on a field that would otherwise have worked it out. */
        OVERRIDDEN,
    }

    /**
     * Usable is a value that read correctly, and where it came from.
     *
     * [origin] saves every caller working that out from the description and the raw mapping: an
     * interface greys a value nobody typed. It changes nothing about what is written back.
     *
     * Always given. Whatever builds one knows where the value came from, so a default could only
     * stand in for a forgotten argument, and a wrong origin is a correct-looking answer.
     */
    data class Usable<out T : Any>(val value: T, val origin: Origin) : Result<T>()

    /** Absent is nothing recorded and nothing worked out. One absent state, not several. */
    object Absent : Result<Nothing>()

    /**
     * Unusable is something that is there and cannot be used: a word where a depth belongs, a
     * value outside a fixed set, a reference to the wrong kind of item.
     *
     * [raw] is what was stored, kept exactly as written. An interface can then say what it found
     * instead of showing a blank. `DATA-24`.
     */
    data class Unusable(val raw: Any?, val reason: String) : Result<Nothing>()
}

/**
 * Element is one member of a collection: a value, or something that is not one.
 *
 * Two states where [Result] has three. A member of a list is there, so it cannot be absent, and a
 * type carrying a state that never arises is a branch every caller writes and none reaches.
 *
 * No origin either. Where a value came from is a property of the field, so every member would carry
 * the same answer, and on a profile that is one of them per sample.
 *
 * A field holding a collection keeps its own [Result]: absent, or usable with an origin, or
 * unusable where what was stored is not a collection at all.
 *
 * Immutable.
 */
sealed class Element<out T : Any> {

    /** Usable is a value that read correctly. */
    data class Usable<out T : Any>(val value: T) : Element<T>()

    /**
     * Unusable is something that is there and cannot be used.
     *
     * [raw] is kept as written, so one unreadable member is shown in place rather than dropped, and
     * the rest of the collection is unaffected. `DATA-24`.
     */
    data class Unusable(val raw: Any?, val reason: String) : Element<Nothing>()
}

/**
 * ValueFormatException is thrown for text that does not say what it claims to: `EAN200`, or a
 * date that is not a date.
 *
 * A bad value, not a bad program. A parser this project owns throws this, and nothing else does, so
 * catching it catches only that. An `IllegalArgumentException` from somewhere unrelated stays a
 * fault instead of reaching a user as a misspelt gas.
 */
class ValueFormatException(message: String) : RuntimeException(message)
