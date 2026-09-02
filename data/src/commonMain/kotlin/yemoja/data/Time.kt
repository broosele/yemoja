package yemoja.data

/**
 * A time of day, with no date and no zone.
 *
 * Always written `09:15:00`, seconds included. No `units` setting affects it.
 *
 * Seconds run 0 to 59. A leap second is refused because [Moment] takes every day to be 86,400
 * seconds.
 */
data class Time(val hour: Int, val minute: Int, val second: Int) : Comparable<Time> {

    init {
        require(hour in 0..<HOURS_IN_DAY) {
            "hour should be 0 to ${HOURS_IN_DAY - 1}, but was $hour"
        }
        require(minute in 0..<MINUTES_IN_HOUR) {
            "minute should be 0 to ${MINUTES_IN_HOUR - 1}, but was $minute"
        }
        require(second in 0..<SECONDS_IN_MINUTE) {
            "second should be 0 to ${SECONDS_IN_MINUTE - 1}, but was $second"
        }
    }

    val secondOfDay: Int get() =
        hour * SECONDS_IN_HOUR + minute * SECONDS_IN_MINUTE + second

    override fun compareTo(other: Time): Int = secondOfDay.compareTo(other.secondOfDay)

    /** Always `09:15:00`, zero-padded. */
    override fun toString(): String =
        "${pad(hour)}:${pad(minute)}:${pad(second)}"

    companion object {

        const val SECONDS_IN_MINUTE = 60
        const val MINUTES_IN_HOUR = 60
        const val HOURS_IN_DAY = 24

        const val SECONDS_IN_HOUR = SECONDS_IN_MINUTE * MINUTES_IN_HOUR
        const val SECONDS_IN_DAY = SECONDS_IN_HOUR * HOURS_IN_DAY

        private val WRITTEN = Regex("""(\d{2}):(\d{2}):(\d{2})""")

        fun ofSecondOfDay(second: Int): Time {
            require(second in 0..<SECONDS_IN_DAY) {
                "a second of the day should be 0 to ${SECONDS_IN_DAY - 1}, but was $second"
            }
            return Time(
                second / SECONDS_IN_HOUR,
                second % SECONDS_IN_HOUR / SECONDS_IN_MINUTE,
                second % SECONDS_IN_MINUTE,
            )
        }

        /** Reads a time. Throws where the text is not one. One written form only. */
        fun parse(text: String): Time {
            val match = WRITTEN.matchEntire(text.trim())
                ?: throw ValueFormatException("$text is not a time, which is written 09:15:00")
            val (hour, minute, second) = match.destructured
            return try {
                Time(hour.toInt(), minute.toInt(), second.toInt())
            } catch (impossible: IllegalArgumentException) {
                throw ValueFormatException("$text: ${impossible.message}")
            }
        }

        private fun pad(value: Int) = value.toString().padStart(2, '0')
    }
}
