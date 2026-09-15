package yemoja.ui.gui

import yemoja.data.Date
import yemoja.data.ReferenceableItem
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 * What the logbook owes. See ../../../../../../gui/doc.md — `GUI-34`.
 */
private val TODAY = Date(2026, 9, 15)

private fun set(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** A gear item owing one piece of work, due on [until]. */
private fun owing(until: String, type: String = "service", generic: String = "") = set(
    "gear.json" to """{"wing": {"name": "Wing blue", "category": "bcd"$generic,
        "maintenances": {"m1": {"type": "$type", "date": "2025-09-15",
        "valid_until": "$until"}}}}""",
)

class OwedGearTest {

    @Test
    fun `work falling due within the month is owed, and what is further off is not`() {
        assertEquals(1, owedIn(owing("2026-10-01"), null, TODAY).size)
        assertTrue(owedIn(owing("2026-11-01"), null, TODAY).isEmpty(), "six weeks off")
    }

    @Test
    fun `work due soon reads as due and work past its day reads as overdue`() {
        val soon = owedIn(owing("2026-09-27"), null, TODAY).single()
        assertFalse(soon.lapsed)
        assertEquals("Wing blue: service due in 12 days.", soon.said)
        val gone = owedIn(owing("2026-08-01"), null, TODAY).single()
        assertTrue(gone.lapsed)
        assertEquals("Wing blue: service 6 weeks overdue.", gone.said)
    }

    @Test
    fun `what lapsed long ago is still owed, however long ago it was`() {
        // A cylinder two years out of test is more of a problem than one due next week.
        val held = owedIn(owing("2021-01-01"), null, TODAY).single()
        assertTrue(held.lapsed)
        assertTrue("5 years" in held.said, held.said)
    }

    @Test
    fun `what falls due is what the work sets the clock for, where it says`() {
        val held = set(
            "gear.json" to """{"tank": {"name": "Twelve", "category": "cylinder",
                "maintenances": {"m1": {"type": "repair", "date": "2026-09-01",
                "valid_until": "2026-09-20", "follow_up_type": "visual inspection"}}}}""",
        )
        assertEquals(
            "Twelve: visual inspection due in 5 days.",
            owedIn(held, null, TODAY).single().said,
            "a repair is something that happened, not something owed",
        )
    }

    @Test
    fun `a generic item owes nothing, describing a kind rather than one that is owned`() {
        assertTrue(owedIn(owing("2026-09-20", generic = """, "generic": true"""), null, TODAY)
            .isEmpty())
    }

    @Test
    fun `an entry with no date owes nothing and starts no clock`() {
        val held = set(
            "gear.json" to """{"wing": {"name": "Wing blue", "category": "bcd",
                "maintenances": {"m1": {"type": "cleaning", "date": "2026-09-14"}}}}""",
        )
        assertTrue(owedIn(held, null, TODAY).isEmpty())
    }
}

class OwedPersonTest {

    private fun person(medical: String = "", insurance: String = ""): Pair<yemoja.data.ItemSet,
        ReferenceableItem> {
        val held = set(
            "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries"$medical
                $insurance}}""",
        )
        return held to (held["anna"] as ReferenceableItem)
    }

    @Test
    fun `a medical is taken to run a year from the check`() {
        val (held, anna) = person(""", "medical": {"last_medical_check": "2025-09-20"}""")
        val owed = owedIn(held, anna, TODAY).single()
        assertFalse(owed.lapsed)
        assertEquals("Your medical check is due in 5 days.", owed.said)
    }

    @Test
    fun `a medical a year and more past its check has fallen due`() {
        val (held, anna) = person(""", "medical": {"last_medical_check": "2024-06-11"}""")
        val owed = owedIn(held, anna, TODAY).single()
        assertTrue(owed.lapsed)
        assertTrue(owed.said.endsWith("overdue."), owed.said)
    }

    @Test
    fun `insurance runs out rather than falling overdue, a lapsed one having simply gone`() {
        val (held, anna) = person(
            insurance = """, "insurance": {"name": "DAN Silver", "start_date": "2024-10-01",
                "end_date": "2026-09-01"}""",
        )
        assertEquals(
            "Your insurance (DAN Silver) ran out 2 weeks ago.",
            owedIn(held, anna, TODAY).single().said,
        )
    }

    @Test
    fun `the twenty-ninth of February falls due on the twenty-eighth`() {
        assertEquals(Date(2025, 2, 28), yearAfter(Date(2024, 2, 29)))
        assertEquals(Date(2026, 6, 11), yearAfter(Date(2025, 6, 11)))
    }

    @Test
    fun `insurance running out is owed, and its name is said`() {
        val (held, anna) = person(
            insurance = """, "insurance": {"name": "DAN Silver", "start_date": "2025-10-01",
                "end_date": "2026-10-01"}""",
        )
        assertEquals(
            "Your insurance (DAN Silver) runs out in 2 weeks.",
            owedIn(held, anna, TODAY).single().said,
        )
    }

    @Test
    fun `nobody else's medical is the reader's business`() {
        val (held, _) = person(""", "medical": {"last_medical_check": "2020-01-01"}""")
        assertTrue(owedIn(held, null, TODAY).isEmpty(), "with no user named, nothing is owed")
    }

    @Test
    fun `the most pressing comes first, whatever kind of thing it is`() {
        val held = set(
            "gear.json" to """{"wing": {"name": "Wing blue", "category": "bcd",
                "maintenances": {"m1": {"type": "service", "valid_until": "2026-09-25"}}}}""",
            "person.json" to """{"anna": {"first_name": "Anna", "last_name": "Devries",
                "medical": {"last_medical_check": "2025-01-01"}}}""",
        )
        val owed = owedIn(held, held["anna"] as ReferenceableItem, TODAY)
        assertEquals(2, owed.size)
        assertTrue(owed.first().lapsed, "the medical went months ago")
        assertTrue(owed.first().said.startsWith("Your medical"), owed.first().said)
    }
}
