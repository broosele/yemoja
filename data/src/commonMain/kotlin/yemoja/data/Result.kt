package yemoja.data

/**
 * What reading a field gave: a value, nothing, or something that cannot be believed.
 *
 * Three states rather than a nullable, because absent and unusable are the two a diver most
 * needs told apart — `DATA-50`. Every reader handles all three.
 */
sealed class Result<out T> {

    /**
     * Applies [transform] to a usable value and carries the other two states through
     * untouched, so a derivation states its arithmetic and nothing else — `DATA-50`.
     *
     * Several inputs are the reader's business: it resolves them, stops at the first that
     * is not usable, and calls the arithmetic only when all of them are.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Usable -> Usable(transform(value))
        is Absent -> this
        is Unusable -> this
    }

    /** A value, and it can be used. */
    class Usable<out T>(val value: T) : Result<T>()

    /** Nothing was recorded and nothing could be worked out. One absent state, not several. */
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
