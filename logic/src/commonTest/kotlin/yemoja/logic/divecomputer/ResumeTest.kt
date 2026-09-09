package yemoja.logic.divecomputer

import yemoja.data.Date
import yemoja.data.Element
import yemoja.data.ItemSet
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Time
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import yemoja.logic.Types
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/*
 * Where a download resumes from once the device has said its serial. `LOGIC-23`.
 *
 * See ../../../../../../doc.md — the mapping is logic/divecomputer.md.
 */

private fun one(serial: String?, day: Int, held: String?, computer: String = "Reef") = Recording(
    computer = computer,
    serial = serial,
    fingerprint = held,
    began = Date(2026, 6, day),
    at = Time(10, 5, 0),
)

private fun of(vararg dived: Recording): ItemSet = Download.read(dived.asSequence())

/** A logbook kept by hand: profiles naming a gear item, and the gear item carrying a serial. */
private fun keptByHand(): ItemSet = LogbookReader.read(
    MemoryFileStore(
        mapOf(
            "gear.json" to """{"perdix": {"name": "Perdix", "serial": "6A9191A5"}}""",
            "dive/2026-06-20#0.json" to """{"profiles": {"p1": {"dive_computer": "@perdix",
                "fingerprint": "aa", "start_date": "2026-06-20", "start_time": "10:00:00"}}}""",
            "dive/2026-06-22#0.json" to """{"profiles": {"p1": {"dive_computer": "@perdix",
                "fingerprint": "bb", "start_date": "2026-06-22", "start_time": "10:00:00"}}}""",
            "dive/2026-06-23#0.json" to """{"profiles": {"p1": {"dive_computer": "Other",
                "fingerprint": "zz", "start_date": "2026-06-23", "start_time": "10:00:00"}}}""",
        ),
    ),
    Types.ALL,
)

class SerialOnProfileTest {

    @Test
    fun `the serial lands on the profile and the computer is not written`() {
        val dive = of(one("1787924901", 21, "a1")).allOf(Types.DIVE).single()
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        val profile = (profiles.values.first() as Element.Usable).value
        assertEquals("1787924901", (profile.single<String>("serial") as Result.Usable).value)
        assertEquals(Result.Absent, profile.read("dive_computer"), "no gear item carries it")
        assertEquals(listOf("reef"), profiles.keys.toList(), "the name is what it is filed under")
    }

    @Test
    fun `where the serial names a gear item, that item's id is the key`() {
        val set = Download.read(sequenceOf(one("1787924901", 21, "a1")), keptByHand())
        val dive = set.allOf(Types.DIVE).single()
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        assertEquals(listOf("perdix"), profiles.keys.toList(), "not the device's own name")
    }

    @Test
    fun `a logbook that keeps no item for it falls back to the device's name`() {
        val set = Download.read(sequenceOf(one("999", 21, "a1")), keptByHand())
        val dive = set.allOf(Types.DIVE).single()
        val profiles = (dive.keyed<OwnedItem>("profiles") as Result.Usable).value
        assertEquals(listOf("reef"), profiles.keys.toList())
    }
}

class ResumeTest {

    @Test
    fun `with a serial, the chain is the profiles carrying it`() {
        val set = of(one("111", 20, "aa"), one("111", 22, "bb"), one("222", 23, "zz"))
        assertEquals("bb", Download.after(set, "Reef", "111"))
        assertEquals("zz", Download.after(set, "Reef", "222"))
    }

    @Test
    fun `and the profiles naming the gear item that carries it`() {
        assertEquals("bb", Download.after(keptByHand(), "Shearwater Perdix 2", "1787924901"))
    }

    @Test
    fun `a serial nothing carries resumes nothing, whatever the name says`() {
        assertNull(Download.after(keptByHand(), "perdix", "999"))
    }

    @Test
    fun `without a serial, the name decides as before`() {
        assertEquals("bb", Download.after(keptByHand(), "perdix"))
        assertEquals("zz", Download.after(keptByHand(), "Other", null))
    }
}
