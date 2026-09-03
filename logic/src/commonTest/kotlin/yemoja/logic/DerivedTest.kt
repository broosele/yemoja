package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.ItemSet
import yemoja.data.Reference
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/*
 * The fields nothing writes: worked out from what is around them, and never stored.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

private fun logbook(vararg files: Pair<String, String>): ItemSet =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** The ids a field of references names, in the order it holds them. */
private fun named(item: Item, field: String): List<String> {
    val read = assertIs<Result.Usable<*>>(item.read(field), "$field on ${item.description.name}")
    return (read.value as List<*>).map { (it as Element.Usable<*>).value }
        .map { (it as Reference.Identified).id }
}

class DiveNameTest {

    @Test
    fun `a dive is named by its id, which is where it is stored`() {
        val set = logbook("dive/2026-02-23#0.json" to """{"dive_number": 38}""")
        val read = assertIs<Result.Usable<*>>(set["2026-02-23#0"]!!.read("name"))
        assertEquals("2026-02-23#0", read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `writing a dive's name does not take, since renaming is not a field`() {
        // Role.Derived, not Overrideable: the id is what everything points at, so changing it
        // is the Universe's job and carries the references with it.
        val set = logbook("dive/2026-02-23#0.json" to """{"name": "Tuesday"}""")
        val read = assertIs<Result.Usable<*>>(set["2026-02-23#0"]!!.read("name"))
        assertEquals("2026-02-23#0", read.value)
    }
}

class BuddyCountTest {

    private fun dive(buddies: String): Item =
        logbook("dive/d#0.json" to """{"buddies": $buddies}""")["d#0"]!!

    @Test
    fun `the count is how many the list holds`() {
        val read = assertIs<Result.Usable<*>>(dive("""["@anna", "john"]""").read("buddy_count"))
        assertEquals(2, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `a plain name counts as much as a reference, both being someone who was there`() {
        assertEquals(3, (dive("""["@a", "b", "@c"]""").read("buddy_count") as Result.Usable).value)
    }

    @Test
    fun `an empty list is nobody, which is not the same as not saying`() {
        assertEquals(0, (dive("[]").read("buddy_count") as Result.Usable).value)
        val unsaid = logbook("dive/d#0.json" to "{}")["d#0"]!!
        assertEquals(Result.Absent, unsaid.read("buddy_count"))
    }

    @Test
    fun `a written count wins, since a diver may remember a number and not the names`() {
        val dive = logbook("dive/d#0.json" to """{"buddies": ["@a"], "buddy_count": 5}""")["d#0"]!!
        val read = assertIs<Result.Usable<*>>(dive.read("buddy_count"))
        assertEquals(5, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}

class ChildrenTest {

    private val world = logbook(
        "region.json" to """{
            "europe": {"name": "Europe"},
            "netherlands": {"name": "Netherlands", "parents": ["@europe"]},
            "belgium": {"name": "Belgium", "parents": ["@europe"]},
            "wadden_sea": {"name": "Wadden Sea", "parents": ["@netherlands", "@north_sea"]},
            "north_sea": {"name": "North Sea"}
        }""",
    )

    @Test
    fun `a region holds the regions naming it, in the order the set does`() {
        assertEquals(listOf("netherlands", "belgium"), named(world["europe"]!!, "children"))
    }

    @Test
    fun `a region inside two of them appears under both`() {
        assertEquals(listOf("wadden_sea"), named(world["netherlands"]!!, "children"))
        assertEquals(listOf("wadden_sea"), named(world["north_sea"]!!, "children"))
    }

    @Test
    fun `a region with nothing inside it holds an empty list, having been asked`() {
        assertEquals(emptyList(), named(world["belgium"]!!, "children"))
    }

    @Test
    fun `it is worked out, so nothing writes it`() {
        val read = assertIs<Result.Usable<*>>(world["europe"]!!.read("children"))
        assertEquals(Result.Origin.DERIVED, read.origin)
    }
}

class PartsTest {

    private val trips = logbook(
        "dive_trip.json" to """{
            "provence": {"name": "Provence"},
            "calanques": {"name": "Calanques", "parent": "@provence"},
            "open_sea": {"name": "Open sea", "parent": "@provence"},
            "lake_week": {"name": "Lake week"}
        }""",
    )

    @Test
    fun `a trip holds the legs naming it`() {
        assertEquals(listOf("calanques", "open_sea"), named(trips["provence"]!!, "parts"))
    }

    @Test
    fun `a trip nobody named holds an empty list`() {
        assertEquals(emptyList(), named(trips["lake_week"]!!, "parts"))
        assertEquals(emptyList(), named(trips["calanques"]!!, "parts"))
    }
}

class CylinderVolumeTest {

    private fun set(gear: String, cylinder: String): ItemSet = logbook(
        "gear.json" to gear,
        "dive/d#0.json" to """{"gas_sources": {"g1": {"cylinder": $cylinder}}}""",
    )

    private fun source(set: ItemSet): Item {
        val sources = assertIs<Result.Usable<*>>(set["d#0"]!!.read("gas_sources"))
        @Suppress("UNCHECKED_CAST")
        val held = sources.value as Map<String, Element<Any>>
        return (held["g1"] as Element.Usable).value as Item
    }

    private val faber = """{"faber_12": {"name": "Faber 12", "category": "cylinder",
        "capacity": 12.0}}"""

    @Test
    fun `the volume is the cylinder's capacity`() {
        val read = assertIs<Result.Usable<*>>(source(set(faber, """"@faber_12"""")).read("volume"))
        assertEquals(12.0, read.value)
        assertEquals(Result.Origin.DERIVED, read.origin)
    }

    @Test
    fun `naming no cylinder is absent, which a rented one ordinarily is`() {
        val set = logbook(
            "gear.json" to faber,
            "dive/d#0.json" to """{"gas_sources": {"g1": {"gas_type": "AIR"}}}""",
        )
        assertEquals(Result.Absent, source(set).read("volume"))
    }

    @Test
    fun `pointing at something that is not a cylinder is a fault, not a blank`() {
        val gear = """{"reg": {"name": "Reg", "category": "regulator"}}"""
        val read = assertIs<Result.Unusable>(source(set(gear, """"@reg"""")).read("volume"))
        assertTrue("cylinder" in read.reason, read.reason)
    }

    @Test
    fun `a cylinder with no capacity written on it is a fault too`() {
        val gear = """{"steel": {"name": "Steel", "category": "cylinder"}}"""
        val read = assertIs<Result.Unusable>(source(set(gear, """"@steel"""")).read("volume"))
        assertTrue("capacity" in read.reason, read.reason)
    }

    @Test
    fun `a cylinder that is not in the logbook is a fault, the capacity being unreadable`() {
        val read = assertIs<Result.Unusable>(source(set(faber, """"@gone"""")).read("volume"))
        assertTrue("gone" in read.reason, read.reason)
    }

    @Test
    fun `a written volume wins, for the cylinder that was not the one recorded`() {
        val set = logbook(
            "gear.json" to faber,
            "dive/d#0.json" to
                """{"gas_sources": {"g1": {"cylinder": "@faber_12", "volume": 15.0}}}""",
        )
        val read = assertIs<Result.Usable<*>>(source(set).read("volume"))
        assertEquals(15.0, read.value)
        assertEquals(Result.Origin.OVERRIDDEN, read.origin)
    }
}
