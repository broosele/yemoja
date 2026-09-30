package yemoja.logic

import yemoja.data.Element
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Staging a field inside an entry a keyed collection does not have yet. See
 * ../../../../../reconciliation.md — `RECON-8`.
 */
class StagedEntryTest {

    private fun diving(): Universe {
        val store = MemoryFileStore(
            mapOf(
                "dive/2026-06-01#0.json" to """{"max_depth": 18,
                    "gas_sources": {"g1": {"usage": "bottom", "gas_type": "EAN32"}}}""",
            ),
        )
        return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
    }

    private fun stagingOf(into: Universe): Staging = Staging.open(into, MemoryFileStore(emptyMap()))

    @Suppress("UNCHECKED_CAST")
    private fun sourcesOf(universe: Universe): Map<String, OwnedItem> =
        ((universe.logbook["2026-06-01#0"]!!.read("gas_sources") as Result.Usable).value
            as Map<String, Element<Any>>).mapValues { (it.value as Element.Usable).value as OwnedItem }

    @Test
    fun `a field of an entry not there yet is staged, and the entry is made when it lands`() {
        val universe = diving()
        val staging = stagingOf(universe)
        assertIs<Outcome.Done>(staging.set("2026-06-01#0", "gas_sources.deco.gas_type", "EAN50"))
        assertIs<Outcome.Done>(staging.set("2026-06-01#0", "gas_sources.deco.usage", "deco"))
        assertEquals(setOf("g1"), sourcesOf(universe).keys, "nothing lands until it is applied")
        val shown = staging.staged.single().fields.map { it.at }.toSet()
        assertEquals(setOf("gas_sources.deco.gas_type", "gas_sources.deco.usage"), shown)
        val applied = staging.apply()
        assertEquals(2, applied.fields, applied.refused.toString())
        val sources = sourcesOf(universe)
        assertEquals(setOf("g1", "deco"), sources.keys)
        assertEquals("deco", (sources.getValue("deco").read("usage") as Result.Usable).value)
    }

    @Test
    fun `an entry already there keeps what it holds when another is added beside it`() {
        // Applying resolves each field to its entry before any lands. Rebuilding the collection
        // for the new entry would aim the existing entry's change at a copy no longer in the dive.
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "gas_sources.g1.gas_type", "EAN36")
        staging.set("2026-06-01#0", "gas_sources.deco.gas_type", "EAN50")
        val applied = staging.apply()
        assertEquals(2, applied.fields, applied.refused.toString())
        val sources = sourcesOf(universe)
        assertEquals(
            "EAN36",
            (sources.getValue("g1").read("gas_type") as Result.Usable).value.toString(),
            "the change to the entry that was there landed too",
        )
        assertEquals("bottom", (sources.getValue("g1").read("usage") as Result.Usable).value)
    }

    @Test
    fun `a key that could not be one is refused, and says what a key looks like`() {
        val staging = stagingOf(diving())
        val refused = assertIs<Outcome.Refused>(staging.set("2026-06-01#0", "gas_sources.my tank.gas_type", "AIR"))
        assertTrue("my tank is not a key" in refused.reason, refused.reason)
        assertTrue(staging.empty)
    }

    @Test
    fun `an entry named and then left empty stages nothing`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "gas_sources.deco.gas_type", "EAN50")
        staging.set("2026-06-01#0", "gas_sources.deco.gas_type", null)
        assertTrue(staging.empty, "an entry holding nothing is no change")
        assertNull(sourcesOf(universe)["deco"])
    }

    @Test
    fun `keys are what the model proposes, and nothing that would break a path or a reference`() {
        val staging = stagingOf(diving())
        for (good in listOf("bottom", "Plan_A", "perdix_2", "deco#1", "open-water")) {
            assertIs<Outcome.Done>(staging.set("2026-06-01#0", "gas_sources.$good.gas_type", "AIR"), good)
        }
        for (bad in listOf("*g1", "@g1", "#1", "tank!")) {
            val refused = assertIs<Outcome.Refused>(
                staging.set("2026-06-01#0", "gas_sources.$bad.gas_type", "AIR"),
                bad,
            )
            assertTrue("is not a key" in refused.reason, refused.reason)
        }
    }
}
