package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * The four fields worked out against today rather than against the logbook.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private fun person(cover: String, today: Date?): Item {
    val set = LogbookReader.read(
        MemoryFileStore(mapOf("person.json" to """{"anna": {"insurance": $cover}}""")),
        Types.ALL,
        today = today,
    )
    return (set["anna"]!!.single<OwnedItem>("insurance") as Result.Usable).value
}

class InsuranceExpiryTest {

    private val running = """{"name": "Cover", "end_date": "2026-09-30"}"""

    @Test
    fun `days left counts from the day the logbook was opened`() {
        val cover = person(running, Date(2026, 9, 3))
        val read = assertIs<Result.Usable<*>>(cover.read("days_left"))
        assertEquals(27, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
        assertEquals(false, (cover.read("expired") as Result.Usable).value)
    }

    @Test
    fun `the last day is not yet over`() {
        val cover = person(running, Date(2026, 9, 30))
        assertEquals(0, (cover.read("days_left") as Result.Usable).value)
        assertEquals(false, (cover.read("expired") as Result.Usable).value)
    }

    @Test
    fun `the day after is expired, and the count runs negative`() {
        val cover = person(running, Date(2026, 10, 1))
        assertEquals(-1, (cover.read("days_left") as Result.Usable).value)
        assertEquals(true, (cover.read("expired") as Result.Usable).value)
    }

    @Test
    fun `cover with no end date says nothing either way`() {
        val cover = person("""{"name": "Cover"}""", Date(2026, 9, 3))
        assertEquals(Result.Absent, cover.read("days_left"))
        assertEquals(Result.Absent, cover.read("expired"))
    }

    @Test
    fun `a set that was told no day says so rather than looking blank`() {
        // A blank would read as cover with no end date, which is a different thing.
        val cover = person(running, null)
        val read = assertIs<Result.Unusable>(cover.read("days_left"))
        assertTrue("what day it is" in read.reason, read.reason)
        assertIs<Result.Unusable>(cover.read("expired"))
    }

    @Test
    fun `no day and no end date is still absent, there being nothing to count`() {
        assertEquals(Result.Absent, person("""{"name": "Cover"}""", null).read("days_left"))
    }
}

class MaintenanceExpiryTest {

    private fun work(due: String, today: Date?): Item {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to """{"steel_12": {"name": "Steel 12",
                        "maintenances": {"k1": {"type": "visual inspection", $due}}}}""",
                ),
            ),
            Types.ALL,
            today = today,
        )
        val held = assertIs<Result.Usable<*>>(set["steel_12"]!!.read("maintenances")).value
        @Suppress("UNCHECKED_CAST")
        val entries = held as Map<String, yemoja.data.Element<Any>>
        return (entries["k1"] as yemoja.data.Element.Usable).value as Item
    }

    @Test
    fun `it counts against valid_until rather than against the date the work was done`() {
        val done = work(""""date": "2025-11-05", "valid_until": "2026-11-05"""", Date(2026, 9, 3))
        assertEquals(63, (done.read("days_left") as Result.Usable).value)
        assertEquals(false, (done.read("expired") as Result.Usable).value)
    }

    @Test
    fun `work that owes nothing has no clock, a cleaning being worth recording`() {
        val done = work(""""date": "2025-11-05"""", Date(2026, 9, 3))
        assertEquals(Result.Absent, done.read("days_left"))
        assertEquals(Result.Absent, done.read("expired"))
    }

    @Test
    fun `an inspection that fell due is expired`() {
        val done = work(""""valid_until": "2026-01-05"""", Date(2026, 9, 3))
        assertEquals(true, (done.read("expired") as Result.Usable).value)
    }
}
