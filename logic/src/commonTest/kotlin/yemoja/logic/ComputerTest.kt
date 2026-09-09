package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * Which computer a recording came off, worked out from its serial. `LOGIC-23`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private const val GEAR = """{
    "perdix": {"name": "Perdix", "serial": "6A9191A5"},
    "spare": {"name": "Spare", "serial": "FL-2200-4471"},
    "fins": {"name": "Fins"}
}"""

private fun profile(fields: String): Item {
    val set = LogbookReader.read(
        MemoryFileStore(
            mapOf(
                "gear.json" to GEAR,
                "dive/d#0.json" to
                    """{"profiles": {"p1": {$fields, "depth": [[0, 0], [60, 12.0]]}}}""",
            ),
        ),
        Types.ALL,
    )
    val held = assertIs<Result.Usable<*>>(set["d#0"]!!.read("profiles")).value
    @Suppress("UNCHECKED_CAST")
    val entries = held as Map<String, Element<Any>>
    return (entries["p1"] as Element.Usable).value as Item
}

class ComputerTest {

    @Test
    fun `the gear item carrying the serial is the computer, however the serial is spelt`() {
        val read = profile(""""serial": "1787924901"""").read("dive_computer")
        val held = assertIs<Result.Usable<*>>(read)
        assertEquals(Reference.Identified("perdix"), held.value, "decimal there, hexadecimal here")
        assertEquals(Result.Origin.DERIVED, held.origin)
        assertEquals(
            Reference.Identified("spare"),
            (profile(""""serial": "fl 2200 4471"""").read("dive_computer") as Result.Usable).value,
        )
    }

    @Test
    fun `a serial no gear item carries names nothing`() {
        assertEquals(Result.Absent, profile(""""serial": "12345"""").read("dive_computer"))
    }

    @Test
    fun `no serial names nothing`() {
        assertEquals(Result.Absent, profile(""""water_type": "salt"""").read("dive_computer"))
    }

    @Test
    fun `a written computer overrides what the serial says`() {
        val read = profile(""""serial": "1787924901", "dive_computer": "@spare"""")
            .read("dive_computer")
        assertEquals(Reference.Identified("spare"), assertIs<Result.Usable<*>>(read).value)
        val borrowed = profile(""""serial": "12345", "dive_computer": "Club Computer"""")
            .read("dive_computer")
        assertEquals(Reference.OneOff("Club Computer"), assertIs<Result.Usable<*>>(borrowed).value)
    }
}
