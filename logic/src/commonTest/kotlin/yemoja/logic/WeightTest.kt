package yemoja.logic

import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * The lead a dive carried, added up from the items taken.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private val GEAR = """{
    "two_kg": {"name": "2 kg block", "category": "weights", "buoyancy": {"mass": 2.0}},
    "one_kg": {"name": "1 kg block", "category": "weights", "buoyancy": {"mass": 1.0}},
    "harness": {"name": "Harness", "category": "BCD", "buoyancy": {"mass": 3.4}},
    "unweighed": {"name": "Odd block", "category": "weights"}
}"""

private fun carried(items: String): Item {
    val set = LogbookReader.read(
        MemoryFileStore(
            mapOf("gear.json" to GEAR, "dive/d#0.json" to """{"gear": {"items": $items}}"""),
        ),
        Types.ALL,
    )
    return (set["d#0"]!!.single<OwnedItem>("gear") as Result.Usable).value
}

class WeightTest {

    @Test
    fun `the lead is every item in the weights category, added up`() {
        val gear = carried("""["@two_kg", "@one_kg", "@two_kg"]""")
        val read = assertIs<Result.Usable<*>>(gear.read("weight"))
        assertEquals(5.0, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a harness is not lead, whatever it weighs`() {
        // Its own mass is the pockets. The blocks that went in them are items of their own,
        // so counting the category counts each block once and no harness at all.
        assertEquals(2.0, (carried("""["@harness", "@two_kg"]""").read("weight") as
            Result.Usable).value)
    }

    @Test
    fun `a block with no mass written on it adds nothing rather than refusing`() {
        // Correctable is what the manual gives for items that do not tell the whole story.
        assertEquals(1.0, (carried("""["@one_kg", "@unweighed"]""").read("weight") as
            Result.Usable).value)
    }

    @Test
    fun `gear this logbook does not hold adds nothing`() {
        assertEquals(1.0, (carried("""["@one_kg", "@borrowed"]""").read("weight") as
            Result.Usable).value)
    }

    @Test
    fun `no lead among the items is none carried, not an unanswered question`() {
        assertEquals(0.0, (carried("""["@harness"]""").read("weight") as Result.Usable).value)
    }

    @Test
    fun `listing no items at all says nothing about the lead`() {
        val set = LogbookReader.read(
            MemoryFileStore(mapOf("dive/d#0.json" to """{"gear": {"mass": 24.0}}""")),
            Types.ALL,
        )
        val gear = (set["d#0"]!!.single<OwnedItem>("gear") as Result.Usable).value
        assertEquals(Result.Absent, gear.read("weight"))
    }

    @Test
    fun `a written weight wins over the items`() {
        val set = LogbookReader.read(
            MemoryFileStore(
                mapOf(
                    "gear.json" to GEAR,
                    "dive/d#0.json" to """{"gear": {"items": ["@one_kg"], "weight": 6.0}}""",
                ),
            ),
            Types.ALL,
        )
        val gear = (set["d#0"]!!.single<OwnedItem>("gear") as Result.Usable).value
        val read = assertIs<Result.Usable<*>>(gear.read("weight"))
        assertEquals(6.0, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}
