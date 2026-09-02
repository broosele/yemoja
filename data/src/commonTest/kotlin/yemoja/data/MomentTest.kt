package yemoja.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DateTest {

    @Test
    fun `a date is written the one way it is ever written`() {
        assertEquals("2026-02-23", Date(2026, 2, 23).toString())
        assertEquals("0001-01-01", Date(1, 1, 1).toString())
        assertEquals("-0044-03-15", Date(-44, 3, 15).toString())
    }

    @Test
    fun `a date reads back from the way it is written`() {
        assertEquals(Date(2026, 2, 23), Date.parse("2026-02-23"))
        assertEquals(Date(-44, 3, 15), Date.parse("-0044-03-15"))
        assertEquals(Date(2026, 2, 23), Date.parse("  2026-02-23  "))
    }

    @Test
    fun `an ambiguous date is refused rather than guessed at`() {
        // 03/04/2026 is the third of April to some readers and the fourth of March to others.
        assertFailsWith<ValueFormatException> { Date.parse("03/04/2026") }
        assertFailsWith<ValueFormatException> { Date.parse("23 February 2026") }
        assertFailsWith<ValueFormatException> { Date.parse("2026-2-23") }
        assertFailsWith<ValueFormatException> { Date.parse("") }
    }

    @Test
    fun `a day that the month does not have is refused`() {
        assertFailsWith<ValueFormatException> { Date.parse("2023-02-29") }
        assertFailsWith<ValueFormatException> { Date.parse("2026-13-01") }
        assertFailsWith<ValueFormatException> { Date.parse("2026-04-31") }
        assertFailsWith<IllegalArgumentException> { Date(2026, 0, 1) }
        assertFailsWith<IllegalArgumentException> { Date(2026, 1, 0) }
    }

    @Test
    fun `a leap day exists only in a leap year`() {
        Date(2024, 2, 29)
        assertFailsWith<IllegalArgumentException> { Date(2023, 2, 29) }
        Date(2000, 2, 29)
        assertFailsWith<IllegalArgumentException> { Date(1900, 2, 29) }
    }

    @Test
    fun `the centuries that catch people out`() {
        assertTrue(Date.isLeapYear(2000))
        assertFalse(Date.isLeapYear(1900))
        assertTrue(Date.isLeapYear(2024))
        assertFalse(Date.isLeapYear(2023))
        assertEquals(29, Date.lengthOfMonth(2024, 2))
        assertEquals(28, Date.lengthOfMonth(2023, 2))
        assertEquals(30, Date.lengthOfMonth(2026, 4))
        assertEquals(31, Date.lengthOfMonth(2026, 1))
    }

    @Test
    fun `the day count is zero at the epoch and runs both ways from it`() {
        assertEquals(0L, Date(1970, 1, 1).epochDay)
        assertEquals(1L, Date(1970, 1, 2).epochDay)
        assertEquals(-1L, Date(1969, 12, 31).epochDay)
        assertEquals(31L + 28, Date(1970, 3, 1).epochDay)
        assertEquals(365L, Date(1971, 1, 1).epochDay)
        assertEquals(366L, Date(1973, 1, 1).epochDay - Date(1972, 1, 1).epochDay)
    }

    @Test
    fun `the day count runs backwards as well as forwards`() {
        // Every day of four centuries, through year zero and the leap rule's exceptions.
        var date = Date(1800, 1, 1)
        var day = date.epochDay
        while (date < Date(2200, 1, 1)) {
            assertEquals(date, Date.ofEpochDay(day), "$date")
            assertEquals(day, date.epochDay, "$date")
            day += 1
            date = Date.ofEpochDay(day)
        }
    }

    @Test
    fun `the count is continuous through the year before year one`() {
        val lastOfYearZero = Date(0, 12, 31)
        val firstOfYearOne = Date(1, 1, 1)
        assertEquals(1L, lastOfYearZero.daysUntil(firstOfYearOne))
        assertEquals(-1L, firstOfYearOne.daysUntil(lastOfYearZero))
        assertTrue(Date.isLeapYear(0))
        assertEquals(29, Date.lengthOfMonth(0, 2))
    }

    @Test
    fun `two dates subtract to the days between them`() {
        assertEquals(365L, Date(2026, 1, 1).daysUntil(Date(2027, 1, 1)))
        assertEquals(-365L, Date(2027, 1, 1).daysUntil(Date(2026, 1, 1)))
        assertEquals(0L, Date(2026, 1, 1).daysUntil(Date(2026, 1, 1)))
    }

    @Test
    fun `dates order by when they were`() {
        assertTrue(Date(2026, 2, 23) < Date(2026, 2, 24))
        assertTrue(Date(2026, 2, 23) < Date(2026, 3, 1))
        assertTrue(Date(-44, 3, 15) < Date(1, 1, 1))
        assertEquals(Date(2026, 2, 23), Date(2026, 2, 23))
    }
}

class TimeTest {

    @Test
    fun `a time is written with its seconds, always`() {
        assertEquals("09:15:00", Time(9, 15, 0).toString())
        assertEquals("00:00:00", Time(0, 0, 0).toString())
        assertEquals("23:59:59", Time(23, 59, 59).toString())
    }

    @Test
    fun `a time reads back from the way it is written`() {
        assertEquals(Time(9, 15, 0), Time.parse("09:15:00"))
        assertEquals(Time(23, 59, 59), Time.parse(" 23:59:59 "))
    }

    @Test
    fun `a time written any other way is refused`() {
        assertFailsWith<ValueFormatException> { Time.parse("9:15:00") }
        assertFailsWith<ValueFormatException> { Time.parse("09:15") }
        assertFailsWith<ValueFormatException> { Time.parse("9.15am") }
        assertFailsWith<ValueFormatException> { Time.parse("") }
    }

    @Test
    fun `an hour outside the day is refused`() {
        assertFailsWith<IllegalArgumentException> { Time(24, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Time(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { Time(0, 60, 0) }
        assertFailsWith<IllegalArgumentException> { Time(0, 0, 60) }
        assertFailsWith<ValueFormatException> { Time.parse("24:00:00") }
    }

    @Test
    fun `a leap second is refused, because every day is the same length here`() {
        assertFailsWith<IllegalArgumentException> { Time(23, 59, 60) }
    }

    @Test
    fun `the second of the day counts from midnight`() {
        assertEquals(0, Time(0, 0, 0).secondOfDay)
        assertEquals(33300, Time(9, 15, 0).secondOfDay)
        assertEquals(Time.SECONDS_IN_DAY - 1, Time(23, 59, 59).secondOfDay)
    }

    @Test
    fun `every second of the day survives being counted and read back`() {
        for (second in 0 until Time.SECONDS_IN_DAY step 7) {
            assertEquals(second, Time.ofSecondOfDay(second).secondOfDay)
        }
        assertFailsWith<IllegalArgumentException> { Time.ofSecondOfDay(Time.SECONDS_IN_DAY) }
        assertFailsWith<IllegalArgumentException> { Time.ofSecondOfDay(-1) }
    }

    @Test
    fun `the constants agree with each other`() {
        assertEquals(3600, Time.SECONDS_IN_HOUR)
        assertEquals(86400, Time.SECONDS_IN_DAY)
        assertEquals(Time.SECONDS_IN_MINUTE * Time.MINUTES_IN_HOUR, Time.SECONDS_IN_HOUR)
        assertEquals(Time.SECONDS_IN_HOUR * Time.HOURS_IN_DAY, Time.SECONDS_IN_DAY)
    }

    @Test
    fun `times order by when they were`() {
        assertTrue(Time(9, 15, 0) < Time(9, 15, 1))
        assertTrue(Time(9, 15, 0) < Time(10, 0, 0))
        assertEquals(Time(9, 15, 0), Time(9, 15, 0))
    }
}

class MomentTest {

    private val moment = Moment(Date(2026, 2, 23), Time(9, 15, 0))

    @Test
    fun `a moment is the two written forms with a space between`() {
        assertEquals("2026-02-23 09:15:00", moment.toString())
    }

    @Test
    fun `the second count is zero at the epoch and runs both ways from it`() {
        assertEquals(0L, Moment(Date(1970, 1, 1), Time(0, 0, 0)).epochSecond)
        assertEquals(1L, Moment(Date(1970, 1, 1), Time(0, 0, 1)).epochSecond)
        assertEquals(-1L, Moment(Date(1969, 12, 31), Time(23, 59, 59)).epochSecond)
    }

    @Test
    fun `a second before the epoch is the last second of the day before it`() {
        // Flooring, not truncating: the whole reason this type exists.
        assertEquals(Moment(Date(1969, 12, 31), Time(23, 59, 59)), Moment.of(-1))
        assertEquals(Moment(Date(1969, 12, 31), Time(0, 0, 0)), Moment.of(-Time.SECONDS_IN_DAY.toLong()))
    }

    @Test
    fun `adding seconds carries the date as far as it has to go`() {
        val lateOn = Moment(Date(2026, 2, 23), Time(23, 30, 0))
        assertEquals(Moment(Date(2026, 2, 24), Time(0, 30, 0)), lateOn.plusSeconds(3600))
        assertEquals(Moment(Date(2026, 3, 25), Time(23, 30, 0)), lateOn.plusSeconds(30L * Time.SECONDS_IN_DAY))
    }

    @Test
    fun `taking seconds away carries the date backwards`() {
        val earlyOn = Moment(Date(2026, 3, 1), Time(0, 30, 0))
        assertEquals(Moment(Date(2026, 2, 28), Time(23, 30, 0)), earlyOn.plusSeconds(-3600))
    }

    @Test
    fun `two moments subtract to the seconds between them, across midnight`() {
        val before = Moment(Date(2026, 2, 23), Time(23, 0, 0))
        val after = Moment(Date(2026, 2, 24), Time(1, 0, 0))
        assertEquals(7200L, before.secondsUntil(after))
        assertEquals(-7200L, after.secondsUntil(before))
    }

    @Test
    fun `every moment survives being counted and read back`() {
        var second = -2L * Time.SECONDS_IN_DAY
        while (second < 2L * Time.SECONDS_IN_DAY) {
            assertEquals(second, Moment.of(second).epochSecond, "$second")
            second += 997
        }
    }

    @Test
    fun `moments order by when they were`() {
        assertTrue(moment < Moment(Date(2026, 2, 23), Time(9, 15, 1)))
        assertTrue(moment < Moment(Date(2026, 2, 24), Time(0, 0, 0)))
        assertEquals(moment, Moment(Date(2026, 2, 23), Time(9, 15, 0)))
    }
}
