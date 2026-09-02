package yemoja.data

/**
 * Values against time, in seconds.
 *
 * One value rather than a collection of them. A series is a curve or a run of events, and its
 * samples are how that gets written down, so a field holding one is usable or not as a whole. What
 * sits on the value axis is whatever a value may be: a depth is a number, an alarm is closed text,
 * a gas switch is a key reference. `DATA-69`.
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

    // Equality is absent, as on Result. Making this a data class would not supply it: generated
    // equality over an IntArray compares identity, so two identical series would differ.

    /** For test failures. The samples themselves are too many to print. */
    override fun toString(): String =
        if (size == 0) "an empty series"
        else "$size samples, ${secondAt(0)} to ${secondAt(size - 1)} seconds"
}

/**
 * One reading of a [Series]: when it was taken, and what was recorded.
 *
 * A view of two halves held apart, not how they are stored.
 *
 * Immutable.
 */
data class Sample(val second: Int, val value: Element<Any>)
