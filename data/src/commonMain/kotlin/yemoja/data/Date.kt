package yemoja.data

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
