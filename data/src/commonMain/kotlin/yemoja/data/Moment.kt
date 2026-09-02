package yemoja.data

/* When something happened: a day, a time of day, and the two together. */

/**
 * A day in the proleptic Gregorian calendar, with no time of day and no time zone.
 *
 * Always written `2026-02-23`. No `units` setting affects it. Written here rather than taken from a
 * library. `DATA-74`.
 */
data class Date(val year: Int, val month: Int, val day: Int) : Comparable<Date> {

    init {
        require(month in 1..MONTHS_IN_YEAR) {
            "month should be 1 to $MONTHS_IN_YEAR, but was $month"
        }
        require(day in 1..lengthOfMonth(year, month)) {
            "day should be 1 to ${lengthOfMonth(year, month)}" +
                " in month $month of $year, but was $day"
        }
    }

    /**
     * Days since 1970-01-01, so that two dates can be subtracted.
     *
     * The year is shifted to begin in March, which puts the leap day at the end and makes the month
     * lengths repeat, so no table is needed.
     */
    val epochDay: Long
        get() {
            val shiftedYear = if (month <= 2) year - 1 else year
            val era = (if (shiftedYear >= 0) shiftedYear else shiftedYear - 399) / 400
            val yearOfEra = shiftedYear - era * 400
            val shiftedMonth = if (month > 2) month - 3 else month + 9
            val dayOfYear = (153 * shiftedMonth + 2) / 5 + day - 1
            val dayOfEra = yearOfEra * 365L + yearOfEra / 4 - yearOfEra / 100 + dayOfYear
            return era * 146097L + dayOfEra - 719468L
        }

    /** Negative where [other] is earlier. */
    fun daysUntil(other: Date): Long = other.epochDay - epochDay

    override fun compareTo(other: Date): Int = epochDay.compareTo(other.epochDay)

    /** Always `2026-02-23`, zero-padded, independent of the file's unit format. */
    override fun toString(): String =
        "${pad(year, 4)}-${pad(month, 2)}-${pad(day, 2)}"

    companion object {

        const val MONTHS_IN_YEAR = 12

        private val WRITTEN = Regex("""(-?\d{4,})-(\d{2})-(\d{2})""")

        /**
         * Reads a date.
         *
         * Throws where the text is not one, as [Gas.parse] does.
         *
         * Not forgiving. `03/04/2026` is the third of April to some readers and the fourth of March
         * to others. Guessing would put dives on the wrong days.
         */
        fun parse(text: String): Date {
            val match = WRITTEN.matchEntire(text.trim())
                ?: throw ValueFormatException("$text is not a date, which is written 2026-02-23")
            val (year, month, day) = match.destructured
            return try {
                Date(year.toInt(), month.toInt(), day.toInt())
            } catch (impossible: IllegalArgumentException) {
                throw ValueFormatException("$text: ${impossible.message}")
            }
        }

        /** The inverse of [epochDay]: the same arithmetic run backwards. */
        fun ofEpochDay(epochDay: Long): Date {
            val shifted = epochDay + 719468L
            val era = (if (shifted >= 0) shifted else shifted - 146096) / 146097
            val dayOfEra = shifted - era * 146097
            val yearOfEra =
                (dayOfEra - dayOfEra / 1460 + dayOfEra / 36524 - dayOfEra / 146096) / 365
            val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
            val shiftedMonth = (5 * dayOfYear + 2) / 153
            val day = dayOfYear - (153 * shiftedMonth + 2) / 5 + 1
            val month = shiftedMonth + if (shiftedMonth < 10) 3 else -9
            val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
            return Date(year.toInt(), month.toInt(), day.toInt())
        }

        /** Divisible by four, except centuries that are not divisible by four hundred. */
        fun isLeapYear(year: Int): Boolean =
            year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

        fun lengthOfMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeapYear(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }

        private fun pad(value: Int, width: Int): String {
            val digits = value.toString().removePrefix("-").padStart(width, '0')
            return if (value < 0) "-$digits" else digits
        }
    }
}

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

/**
 * A date and a time together, so that arithmetic across midnight carries the day.
 *
 * **Never stored.** The format keeps the two apart so a user can correct one by hand; this is what
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
