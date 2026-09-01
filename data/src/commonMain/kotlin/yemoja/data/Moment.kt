package yemoja.data

/**
 * A date and a time together, so that arithmetic across midnight carries the day.
 *
 * **Never stored.** The format keeps the two apart so a diver can correct one by hand; this is what
 * they become while something is worked out from them, and it is split back before anything is
 * written. See `DATA-75`.
 */
data class Moment(val date: Date, val time: Time) : Comparable<Moment> {

    /** Seconds since 1970-01-01 00:00:00, so that two moments can be subtracted. */
    val epochSecond: Long get() = date.epochDay * Time.SECONDS_IN_DAY + time.secondOfDay

    /** Negative where [other] is earlier. */
    fun secondsUntil(other: Moment): Long = other.epochSecond - epochSecond

    /** [seconds] later, carrying the date as far as it has to go. Negative moves back. */
    fun plusSeconds(seconds: Long): Moment = of(epochSecond + seconds)

    override fun compareTo(other: Moment): Int = epochSecond.compareTo(other.epochSecond)

    /** `2026-02-23 09:15:00`, the two written forms with a space between. */
    override fun toString(): String = "$date $time"

    companion object {

        fun of(epochSecond: Long): Moment {
            // Floor, not truncate: -1 is the last second of the day before the epoch.
            // Kotlin has no floorDiv in common code, hence the correction.
            var day = epochSecond / Time.SECONDS_IN_DAY
            var second = epochSecond % Time.SECONDS_IN_DAY
            if (second < 0) {
                day -= 1
                second += Time.SECONDS_IN_DAY
            }
            return Moment(Date.ofEpochDay(day), Time.ofSecondOfDay(second.toInt()))
        }
    }
}
