package yemoja.logic

import yemoja.data.KeyReference
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/*
 * A dive gaining its second profile has its first named as primary. See ../../../../../doc.md —
 * the rule is `DATA-120` in data/doc.md.
 */

private fun opened(dive: String): Pair<MemoryFileStore, Universe> {
    val store = MemoryFileStore(mapOf("dive/d#0.json" to dive))
    return store to Universe(LogbookReader.read(store, Types.ALL), null, store)
}

/** The profiles of the dive in [universe], rewritten with [added] beside them. */
private fun withAdded(universe: Universe, added: String): Stored {
    val dive = universe.logbook["d#0"]!!
    val held = LinkedHashMap<String, Stored>()
    for ((key, _) in (dive.keyed<yemoja.data.OwnedItem>("profiles") as Result.Usable).value) {
        held[key] = Stored.Members(mapOf("depth" to Stored.Elements(listOf(pointOf(0, 0), pointOf(600, 20)))))
    }
    held[added] = Stored.Members(mapOf("planned" to Stored.Leaf(true)))
    return Stored.Members(held)
}

private fun pointOf(second: Int, metres: Int): Stored =
    Stored.Elements(listOf(Stored.Leaf(second), Stored.Leaf(metres)))

private fun primaryOf(universe: Universe): Result<KeyReference> =
    universe.logbook["d#0"]!!.single<KeyReference>("primary_profile")

private const val ONE_RECORDING = """{"start_date": "2026-06-21",
    "profiles": {"p1": {"depth": [[0, 0], [600, 20]]}}}"""

class PrimaryKeptTest {

    @Test
    fun `a second profile names the first as primary`() {
        val (store, universe) = opened(ONE_RECORDING)
        val dive = universe.logbook["d#0"]!!

        assertIs<Outcome.Done>(universe.change(Operation.EDIT, Change.Write(dive, "profiles", withAdded(universe, "plan"))))

        assertEquals("p1", assertIs<Result.Usable<KeyReference>>(primaryOf(universe)).value.key)
        val reopened = LogbookReader.read(store, Types.ALL)["d#0"]!!
        assertEquals("p1", assertIs<Result.Usable<KeyReference>>(reopened.single<KeyReference>("primary_profile")).value.key, "and saved")
    }

    @Test
    fun `the dive goes on being worked from the recording it had`() {
        val (_, universe) = opened(ONE_RECORDING)
        val dive = universe.logbook["d#0"]!!
        val before = dive.read("max_depth")

        universe.change(Operation.EDIT, Change.Write(dive, "profiles", withAdded(universe, "plan")))

        assertEquals(before, universe.logbook["d#0"]!!.read("max_depth"))
    }

    @Test
    fun `a primary already named is left as it is`() {
        val (_, universe) = opened(
            """{"primary_profile": "*p1", "profiles": {"p1": {"depth": [[0, 0], [600, 20]]}}}""",
        )
        val dive = universe.logbook["d#0"]!!

        universe.change(Operation.EDIT, Change.Write(dive, "profiles", withAdded(universe, "plan")))

        assertEquals("p1", assertIs<Result.Usable<KeyReference>>(primaryOf(universe)).value.key)
    }

    @Test
    fun `a change that names a primary itself is left to do so`() {
        val (_, universe) = opened(ONE_RECORDING)
        val dive = universe.logbook["d#0"]!!

        universe.change(
            Operation.EDIT,
            Change.Write(dive, "profiles", withAdded(universe, "plan")),
            Change.Write(dive, "primary_profile", Stored.Leaf("*plan")),
        )

        assertEquals("plan", assertIs<Result.Usable<KeyReference>>(primaryOf(universe)).value.key)
    }

    @Test
    fun `a dive given its first profile names none`() {
        val (_, universe) = opened("""{"start_date": "2026-06-21"}""")
        val dive = universe.logbook["d#0"]!!
        val first = Stored.Members(mapOf("p1" to Stored.Members(mapOf("planned" to Stored.Leaf(true)))))

        universe.change(Operation.EDIT, Change.Write(dive, "profiles", first))

        assertIs<Result.Absent>(primaryOf(universe), "one profile needs no name")
    }
}
