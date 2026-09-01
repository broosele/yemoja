package yemoja.data

/**
 * What reading a field gave: a value, nothing, or something that cannot be believed.
 *
 * Three states rather than a nullable, because absent and unusable are the two a diver most
 * needs told apart — `DATA-50`. Every reader handles all three.
 *
 * The value is non-null by the bound on [T]: a usable nothing would be the collapse the
 * three states exist to prevent, smuggled back through the type parameter.
 */
sealed class Result<out T : Any> {

    /**
     * Applies [transform] to a usable value and carries the other two states through
     * untouched.
     *
     * Offered, not imposed. A derivation decides for itself whether it can manage without
     * an input — `deco` falls back from one field to another when the first is absent — so
     * propagation is the common case rather than the rule. `DATA-26`.
     */
    inline fun <R : Any> map(transform: (T) -> R): Result<R> = when (this) {
        is Usable -> Usable(transform(value))
        is Absent -> this
        is Unusable -> this
    }

    /** A value, and it can be used. */
    class Usable<out T : Any>(val value: T) : Result<T>()

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
