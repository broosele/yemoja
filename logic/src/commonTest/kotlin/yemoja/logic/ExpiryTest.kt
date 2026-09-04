package yemoja.logic

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * The four fields counted against today rather than against the logbook.
 *
 * **Stated against `today()` rather than against a written date.** These are the only fields
 * whose answer depends on when they are asked, so a test naming a day would pass until that day
 * arrived and then fail with no commit to blame. What is worth pinning is the arithmetic and the
 * two boundaries, and both are sayable as an offset from whatever day it is.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** The day [days] from today, which is how every date here is stated. */
private fun away(days: Int): Date = Date.ofEpochDay(today().epochDay + days)

private fun person(cover: String): Item {
    val set: ItemSet = LogbookReader.read(
        MemoryFileStore(mapOf("person.json" to """{"anna": {"insurance": $cover}}""")),
        Types.ALL,
    )
    return (set["anna"]!!.single<OwnedItem>("insurance") as Result.Usable).value
}

private fun cover(ends: Date?): Item {
    val until = ends?.let { ""","end_date": "$it"""" } ?: ""
    return person("""{"name": "Cover"$until}""")
}

class InsuranceExpiryTest {

    @Test
    fun `days left counts from today`() {
        val read = assertIs<Result.Usable<*>>(cover(away(27)).read("days_left"))
        assertEquals(27, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
        assertEquals(false, (cover(away(27)).read("expired") as Result.Usable).value)
    }

    @Test
    fun `the last day is not yet over`() {
        val today = cover(away(0))
        assertEquals(0, (today.read("days_left") as Result.Usable).value)
        assertEquals(false, (today.read("expired") as Result.Usable).value)
    }

    @Test
    fun `the day after is expired, and the count runs negative`() {
        val yesterday = cover(away(-1))
        assertEquals(-1, (yesterday.read("days_left") as Result.Usable).value)
        assertEquals(true, (yesterday.read("expired") as Result.Usable).value)
    }

    @Test
    fun `cover long run out is expired by however long`() {
        assertEquals(-400, (cover(away(-400)).read("days_left") as Result.Usable).value)
        assertEquals(true, (cover(away(-400)).read("expired") as Result.Usable).value)
    }

    @Test
    fun `cover with no end date says nothing either way`() {
        assertEquals(Result.Absent, cover(null).read("days_left"))
        assertEquals(Result.Absent, cover(null).read("expired"))
    }
}

class MaintenanceExpiryTest {

    private fun work(due: String): Item {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"steel_12": {"name": "Steel 12",
                        "maintenances": {"k1": {"type": "visual inspection"$due}}}}""",
                ),
            ),
            Types.ALL,
        )
        val held = assertIs<Result.Usable<*>>(set["steel_12"]!!.read("maintenances")).value
        @Suppress("UNCHECKED_CAST")
        val entries = held as Map<String, Element<Any>>
        return (entries["k1"] as Element.Usable).value as Item
    }

    @Test
    fun `it counts against valid_until rather than the date the work was done`() {
        val done = work(""","date": "2020-11-05", "valid_until": "${away(63)}"""")
        assertEquals(63, (done.read("days_left") as Result.Usable).value)
        assertEquals(false, (done.read("expired") as Result.Usable).value)
    }

    @Test
    fun `work that owes nothing has no clock, a cleaning being worth recording`() {
        val done = work(""","date": "2020-11-05"""")
        assertEquals(Result.Absent, done.read("days_left"))
        assertEquals(Result.Absent, done.read("expired"))
    }

    @Test
    fun `an inspection that fell due is expired`() {
        assertEquals(true, (work(""","valid_until": "${away(-2)}"""").read("expired") as
            Result.Usable).value)
    }
}
