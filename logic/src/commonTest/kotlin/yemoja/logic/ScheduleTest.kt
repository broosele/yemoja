package yemoja.logic

import yemoja.data.Gas
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * What the model asks a diver to hold, against what published air tables ask. See
 * ../../../../../doc.md — `LOGIC-37`.
 *
 * Sea water at sea level, because that is what a table assumes, and bottom time counted from
 * leaving the surface, because that is how a table counts it. The no-decompression limits were
 * checked against published figures from the start and the schedules never were, which is how a
 * gradient factor read at the wrong depth went unseen.
 */

/**
 * One air dive to [metres], leaving the surface at nought and the bottom at [minutes].
 *
 * The descent is rounded up, which is how the form that writes a plan times it, so these are runs
 * a user can ask for rather than ones only a test can build. A second either way is not a
 * decompression difference, but it moves a stop that sits near a whole minute.
 */
private fun airRun(metres: Double, minutes: Int, low: Double, high: Double): Run {
    val descent = ceil(metres / DESCENT_METRES_A_MINUTE * 60).toInt()
    return Run(
        depth = listOf(0 to 0.0, descent to metres, minutes * 60 to metres),
        sources = mapOf("g1" to Source(Gas.AIR)),
        gradientFactorLow = low,
        gradientFactorHigh = high,
        switches = listOf(0 to "g1"),
    )
}

private fun ascentOf(metres: Double, minutes: Int, low: Double, high: Double): Ascended.Done =
    assertIs<Ascended.Done>(
        completeAscent(airRun(metres, minutes, low, high), ASCENT_METRES_A_MINUTE, 3.0),
    )

/** The minutes spent holding a depth on the way up, which is what a table's column adds to. */
private fun stopMinutesOf(ascent: Ascended.Done): Double =
    ascent.depth.zipWithNext()
        .filter { (from, to) -> from.second > 0 && from.second == to.second }
        .sumOf { (from, to) -> to.first - from.first } / 60.0

private fun deepestStopOf(ascent: Ascended.Done): Double =
    ascent.depth.filter { it.second > 0 }.maxOf { it.second }

private const val DESCENT_METRES_A_MINUTE = 18.0
private const val ASCENT_METRES_A_MINUTE = 9.0

/**
 * Four air dives a table has a column for, and the stop minutes each should fall between.
 *
 * The bands are wide on purpose. A Bühlmann model with gradient factors is not the model the air
 * tables were cut from, and the two are not meant to agree to the minute. A schedule outside the
 * band is wrong by more than the models differ.
 */
private val TABLE_DIVES = listOf(
    Row(30.0, 40, 7.0..28.0),
    Row(40.0, 25, 7.0..26.0),
    Row(45.0, 20, 6.0..22.0),
    Row(50.0, 15, 5.0..18.0),
)

private class Row(val metres: Double, val minutes: Int, val expected: ClosedRange<Double>)

class ScheduleTest {

    @Test
    fun `an air dive at the full factors is held about as long as a table holds it`() {
        for (row in TABLE_DIVES) {
            val stops = stopMinutesOf(ascentOf(row.metres, row.minutes, 1.0, 1.0))

            assertTrue(
                stops in row.expected,
                "${row.metres} m for ${row.minutes} min asks for $stops min of stops," +
                    " outside ${row.expected}",
            )
        }
    }

    @Test
    fun `a sliding pair holds longer than the full factors, and not without limit`() {
        for (row in TABLE_DIVES) {
            val full = stopMinutesOf(ascentOf(row.metres, row.minutes, 1.0, 1.0))
            val sliding = stopMinutesOf(ascentOf(row.metres, row.minutes, 0.3, 0.7))

            assertTrue(
                sliding > full,
                "${row.metres} m for ${row.minutes} min: 30/70 asks $sliding min against" +
                    " 100/100's $full, and conservatism should cost something",
            )
            // Reading the factor at the depth held rather than the depth ascended to put this
            // ratio near four and a half, where a sliding pair costs about three.
            assertTrue(
                sliding < full * 4,
                "$sliding min against $full is more than a gradient factor accounts for",
            )
        }
    }

    @Test
    fun `a lower low factor takes the first stop deeper without shortening the way up`() {
        val full = ascentOf(40.0, 25, 1.0, 1.0)
        val sliding = ascentOf(40.0, 25, 0.3, 1.0)

        assertTrue(
            deepestStopOf(sliding) > deepestStopOf(full),
            "the first stop should be deeper: ${deepestStopOf(sliding)} against" +
                " ${deepestStopOf(full)}",
        )
        assertTrue(
            stopMinutesOf(sliding) >= stopMinutesOf(full),
            "stopping deeper does not let anybody up sooner",
        )
    }

    @Test
    fun `longer on the bottom owes more than shorter, at every depth a table lists`() {
        for (row in TABLE_DIVES) {
            val shorter = stopMinutesOf(ascentOf(row.metres, row.minutes, 0.3, 0.7))
            val longer = stopMinutesOf(ascentOf(row.metres, row.minutes + 10, 0.3, 0.7))

            assertTrue(
                longer > shorter,
                "${row.metres} m: ${row.minutes + 10} min owes $longer against" +
                    " ${row.minutes} min's $shorter",
            )
        }
    }
}
