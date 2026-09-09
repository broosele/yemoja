package yemoja.logic

import yemoja.data.Element
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.Json
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * An arriving item laid over one already held, member by member. `RECON-6`.
 *
 * See ../../../../../doc.md — the layer's own document is logic/reconciliation.md.
 */

private fun members(stored: Stored?): Map<String, Stored> = (stored as Stored.Members).members

private fun leaf(stored: Stored?): Any? = (stored as Stored.Leaf).value

/** An import of the dive [coming] into a logbook holding the dive [held], matched by nothing. */
private fun over(held: String, coming: String): Pair<Universe, Import> {
    val store = MemoryFileStore(mapOf("dive/d#0.json" to held))
    val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
    val from = MemoryFileStore(mapOf("dive/x#0.json" to coming))
    into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()), Matching.NONE)
    return into to into.importing!!
}

class LaidOverTest {

    @Test
    fun `members are laid over member by member, and anything else is replaced`() {
        val existing = Json.parse("""{"a": 1, "b": {"x": 1, "y": 2}, "c": [1, 2], "d": 4}""")
        val incoming = Json.parse("""{"a": 9, "b": {"y": 3, "z": 4}, "c": [9]}""")
        val laid = members(laidOver(existing, incoming))
        assertEquals(listOf("a", "b", "c", "d"), laid.keys.toList(), "held order, arrivals after")
        assertEquals(9L, leaf(laid["a"]), "a value is replaced")
        assertEquals(
            mapOf("x" to 1L, "y" to 3L, "z" to 4L),
            members(laid["b"]).mapValues { leaf(it.value) },
        )
        assertEquals(1, (laid["c"] as Stored.Elements).elements.size, "a list is replaced whole")
        assertEquals(4L, leaf(laid["d"]), "what the arrival is silent about survives")
    }

    @Test
    fun `nothing held is the arrival as it came`() {
        val incoming = Json.parse("""{"y": 3}""")
        assertEquals(incoming, laidOver(null, incoming))
    }

    @Test
    fun `a second computer's profile lands beside the first, and the environment keeps its own`() {
        val (into, import) = over(
            """{"environment": {"visibility": 5},
                "gas_sources": {"g1": {"gas_type": "AIR", "volume": 12}},
                "profiles": {"p1": {"dive_computer": "@perdix", "start_date": "2024-06-15",
                    "start_time": "10:00:00", "duration": 3600}}}""",
            """{"start_date": "2024-06-15", "start_time": "10:00:30", "duration": 3500,
                "environment": {"atmospheric_pressure": 1.01},
                "profiles": {"i330r": {"fingerprint": "a1", "start_date": "2024-06-15",
                    "start_time": "10:00:30", "duration": 3500}}}""",
        )
        assertIs<Outcome.Done>(import.insert("x#0", "d#0"))
        val dive = into.logbook["d#0"]!!
        assertEquals(listOf("p1", "i330r"), keysOf(dive, "profiles"))
        val environment = (dive.read("environment") as Result.Usable).value as Item
        assertEquals(5.0, number(environment, "visibility"))
        assertEquals(1.01, number(environment, "atmospheric_pressure"))
        assertEquals(listOf("g1"), keysOf(dive, "gas_sources"), "no gas reported, none touched")
    }

    @Test
    fun `a profile arriving under a held key has its fields laid over that profile's`() {
        val (into, import) = over(
            """{"profiles": {"perdix": {"dive_computer": "@perdix", "start_date": "2024-06-15",
                "start_time": "10:00:00", "duration": 3600, "water_type": "salt"}}}""",
            """{"start_date": "2024-06-15", "start_time": "10:00:30", "duration": 3500,
                "profiles": {"perdix": {"fingerprint": "a1", "start_date": "2024-06-15",
                    "start_time": "10:00:30", "duration": 3500}}}""",
        )
        assertIs<Outcome.Done>(import.insert("x#0", "d#0"))
        val profiles = (into.logbook["d#0"]!!.keyed<OwnedItem>("profiles") as Result.Usable).value
        val profile = (profiles["perdix"] as Element.Usable).value
        assertEquals("salt", text(profile, "water_type"), "kept")
        assertEquals("a1", text(profile, "fingerprint"), "arrived")
        assertEquals(3500.0, number(profile, "duration"), "replaced")
    }
}

private fun keysOf(dive: Item, field: String): List<String> =
    (dive.keyed<OwnedItem>(field) as Result.Usable).value.keys.toList()

private fun number(item: Item, field: String): Double? =
    (item.single<Double>(field) as? Result.Usable)?.value

private fun text(item: Item, field: String): String? =
    (item.single<String>(field) as? Result.Usable)?.value
