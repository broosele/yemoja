package yemoja.data

/**
 * The result of reading a field: a value, nothing, or something that cannot be interpreted
 * correctly.
 *
 * Three states, not a nullable. Absent and unusable are the two a user most needs told apart.
 * `DATA-50`. Every reader handles all three.
 *
 * The value is non-null by the bound on [T]: a usable nothing should be an absent.
 *
 * Immutable. What a [Usable] carries is immutable only where the value itself is.
 */
sealed class Result<out T : Any> {

    /**
     * Where a usable value came from.
     *
     * Not *source*, which is the store it was read from. `DATA-64`.
     *
     * One of three, not a flag each. They exclude one another, and a description settles which are
     * possible before a file is read.
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
     * A usable value, and where it came from.
     *
     * [origin] saves every caller working that out from the description and the raw mapping. An
     * interface greys a value nobody typed. An editor decides whether clearing a field does
     * anything. It changes nothing about what is written back.
     *
     * Always given. Whatever builds one of these knows where the value came from, so a default
     * could only stand in for a forgotten argument. Nothing would catch that: the wrong origin is a
     * correct-looking answer.
     */
    class Usable<out T : Any>(val value: T, val origin: Origin) : Result<T>()

    /** Nothing recorded and nothing worked out. One absent state, not several. */
    object Absent : Result<Nothing>()

    /**
     * Something is there and cannot be used: a word where a depth belongs, a value outside a fixed
     * set, a reference to the wrong kind of item.
     *
     * [raw] is what was stored, kept exactly as written. An interface can then say what it found
     * instead of showing a blank. `DATA-24`.
     */
    class Unusable(val raw: Any?, val reason: String) : Result<Nothing>()
}
