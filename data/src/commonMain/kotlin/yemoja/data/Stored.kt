package yemoja.data

/**
 * Stored is what a source holds, before anything has judged it.
 *
 * Three shapes and no more: a leaf, members under names, or elements in order. Every source can
 * produce them — a file, a database row, a fixture assembled in memory — which is what lets the
 * walk from a description to an item's fields be written once instead of once per source.
 *
 * **A leaf holds anything, on purpose.** Sources differ in how much they have already done: JSON
 * has no date, so it hands over `"2026-02-23"`, while a database column hands over a date already
 * made. No closed set of leaf types could cover both, and no interface phrased as *give me a date*
 * could either, since that forces every source to parse. Reading a leaf against a kind takes
 * whichever it finds. `DATA-64`.
 *
 * Immutable, and it copies what it is given.
 */
sealed class Stored {

    /**
     * Leaf is a single thing: text, a number, a boolean, nothing, or a value a source had already
     * made.
     *
     * A whole number and a fraction tell themselves apart without help, because a [Long] and a
     * [Double] are different types. That distinction is load-bearing: a whole number field refuses
     * `7.0` while a number field accepts `7`.
     */
    data class Leaf(val value: Any?) : Stored()

    /** Members is a group under names, in the order the source holds them. */
    class Members(members: Map<String, Stored>) : Stored() {

        // Copied. A Map is read-only, not immutable.
        val members: Map<String, Stored> = members.toMap()

        override fun equals(other: Any?): Boolean = other is Members && members == other.members

        override fun hashCode(): Int = members.hashCode()

        override fun toString(): String = "Members$members"
    }

    /** Elements is a run of them in order. */
    class Elements(elements: List<Stored>) : Stored() {

        // Copied. A List is read-only, not immutable.
        val elements: List<Stored> = elements.toList()

        override fun equals(other: Any?): Boolean = other is Elements && elements == other.elements

        override fun hashCode(): Int = elements.hashCode()

        override fun toString(): String = "Elements$elements"
    }
}
