package yemoja.data

/**
 * The result of reading a field: a value, nothing, or something that cannot be interpreted correctly.
 *
 * Three states rather than a nullable, because absent and unusable are the two a diver most
 * needs told apart — `DATA-50`. Every reader handles all three.
 *
 * The value is non-null by the bound on [T]: a usable nothing should be an absent.
 */
sealed class Result<out T : Any> {

    /**
     * Where a usable value came from. Not *source*, which is the store it was read from —
     * `DATA-64`.
     *
     * One of three rather than a flag each, because they exclude one another, and a
     * description settles which are possible before a file is read.
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
     * [origin] saves every caller working that out from the description and the raw mapping
     * for itself — an interface greying a value nobody typed, an editor deciding whether
     * clearing a field does anything. It changes nothing about what is written back.
     *
     * Always given: whatever builds one of these knows where the value came from, and a
     * default here could only stand in for a forgotten argument — which nothing would
     * catch, since the wrong origin is a correct-looking answer.
     */
    class Usable<out T : Any>(val value: T, val origin: Origin) : Result<T>()

    /** Nothing recorded and nothing worked out. One absent state, not several. */
    object Absent : Result<Nothing>()

    /**
     * Something is there and cannot be used — a word where a depth belongs, a value outside
     * a fixed set, a reference to the wrong kind of item.
     *
     * [raw] is what was stored, kept exactly as written so an interface can say what it
     * found rather than showing a blank — `DATA-24`.
     */
    class Unusable(val raw: Any?, val reason: String) : Result<Nothing>()
}
