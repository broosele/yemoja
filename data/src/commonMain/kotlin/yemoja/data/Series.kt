package yemoja.data

/**
 * Series holds values against time, in seconds.
 *
 * One value rather than a collection of them, so a field holding one carries a single [Result]
 * and not a result per sample. Each sample keeps its own fate all the same. `DATA-77`.
 *
 * What sits on the value axis is whatever a value may be: a depth is a number, an alarm is closed
 * text, a gas switch is a key reference. `DATA-69`.
 *
 * Times and values are held apart rather than as pairs. A profile is the largest thing in the
 * application, and a thousand dives of a few thousand samples is where that is felt.
 *
 * A gap says nothing. A series cannot tell data that is missing from data that is merely sparse,
 * and nothing is added to let it. `DATA-58`.
 *
 * Immutable.
 */
class Series(seconds: IntArray, values: List<Element<Any>>) {

    // Both copied. An IntArray is mutable, and a List is read-only rather than immutable.
    private val seconds: IntArray = seconds.copyOf()
    private val values: List<Element<Any>> = values.toList()

    init {
        require(seconds.size == values.size) {
            "a series should hold one value per time, but held ${seconds.size} times" +
                " and ${values.size} values"
        }
        for (index in 1..<seconds.size) {
            require(seconds[index] > seconds[index - 1]) {
                "a series should run forwards, but ${seconds[index]} follows ${seconds[index - 1]}"
            }
        }
    }

    val size: Int get() = seconds.size

    /** When the sample at [index] was taken, in seconds. */
    fun secondAt(index: Int): Int = seconds[index]

    /** What was recorded at [index]. */
    fun valueAt(index: Int): Element<Any> = values[index]

    /** The two halves together. Built on asking, so a scan that does not need it pays nothing. */
    operator fun get(index: Int): Sample = Sample(seconds[index], values[index])

    // Written out rather than generated. A data class compares an IntArray by identity, so two
    // series holding the same samples would differ.
    override fun equals(other: Any?): Boolean =
        other is Series && seconds.contentEquals(other.seconds) && values == other.values

    override fun hashCode(): Int = 31 * seconds.contentHashCode() + values.hashCode()

    /** For test failures. The samples themselves are too many to print. */
    override fun toString(): String =
        if (size == 0) "an empty series"
        else "$size samples, ${secondAt(0)} to ${secondAt(size - 1)} seconds"
}

/**
 * Sample is one reading of a [Series]: when it was taken, and what was recorded.
 *
 * Immutable.
 */
data class Sample(val second: Int, val value: Element<Any>)
