package yemoja.logic

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * What an agent would change, before it happens.
 *
 * See ../../../../../reconciliation.md — `RECON-8`.
 */

private fun logbook(vararg files: Pair<String, String>): Universe {
    val store = MemoryFileStore(mapOf(*files))
    return Universe(LogbookReader.read(store, Types.ALL), null, store, null, null)
}

/** Two dives and a site, enough to edit, add to and delete from. */
private fun diving(): Universe = logbook(
    "dive_site.json" to """{"blue": {"name": "Blue Hole"}}""",
    "dive/2026-06-01#0.json" to """{"max_depth": 18, "rating": 6,
        "environment": {"visibility": 12},
        "gas_sources": {"g1": {"usage": "bottom", "gas_type": "EAN32"}}}""",
    "dive/2026-06-02#0.json" to """{"max_depth": 24}""",
)

private fun stagingOf(into: Universe): Staging =
    Staging.open(into, MemoryFileStore(emptyMap()))

private fun read(universe: Universe, id: String, field: String): Any? =
    (universe.logbook[id]?.read(field) as? Result.Usable)?.value

class StagingTest {

    @Test
    fun `a staged edit says what the field was and what it would be`() {
        val universe = diving()
        val staging = stagingOf(universe)
        assertTrue(staging.empty)
        assertEquals(Outcome.Done(), staging.set("2026-06-01#0", "rating", 8))
        val staged = staging.staged.single()
        assertEquals("2026-06-01#0", staged.id)
        assertEquals(Staged.Kind.EDIT, staged.kind)
        assertEquals(listOf(Changed("rating", "6", "8")), staged.fields)
        assertEquals(6, read(universe, "2026-06-01#0", "rating"), "and nothing has happened yet")
    }

    @Test
    fun `a field inside an owned item is named by its path`() {
        val staging = stagingOf(diving())
        staging.set("2026-06-01#0", "environment.visibility", 20)
        staging.set("2026-06-01#0", "gas_sources.g1.usage", "stage")
        assertEquals(
            listOf(
                Changed("environment.visibility", "12", "20"),
                Changed("gas_sources.g1.usage", "bottom", "stage"),
            ),
            staging.staged.single().fields,
        )
    }

    @Test
    fun `applying writes what was staged, and empties the staging`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 8)
        staging.set("2026-06-02#0", "max_depth", 25.0)
        val applied = staging.apply()
        assertEquals(Applied(2, 2, emptyList()), applied)
        assertEquals(8, read(universe, "2026-06-01#0", "rating"))
        assertEquals(25.0, read(universe, "2026-06-02#0", "max_depth"))
        assertTrue(staging.empty, "what has happened is no longer staged")
    }

    @Test
    fun `a field that moved since it was staged says so before anything is applied`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 8)
        assertEquals(false, staging.staged.single().fields.single().moved)
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 3),
        )
        val changed = staging.staged.single().fields.single()
        assertEquals(Changed("rating", "6", "8", "3"), changed)
        assertEquals(true, changed.moved, "so a review can say so rather than find out on applying")
    }

    @Test
    fun `a field that moved since it was staged is left alone, and the rest still lands`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 8)
        staging.set("2026-06-02#0", "max_depth", 25.0)
        // Somebody edits one of them while the change waits to be looked at.
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 3),
        )
        val applied = staging.apply()
        assertEquals(1, applied.items)
        assertEquals(1, applied.fields)
        assertEquals(
            listOf(
                Refused("2026-06-01#0", "rating", "it was 6 when this was staged, and is 3 now"),
            ),
            applied.refused,
        )
        assertEquals(3, read(universe, "2026-06-01#0", "rating"), "what somebody else wrote stands")
        assertEquals(25.0, read(universe, "2026-06-02#0", "max_depth"), "and the rest landed")
    }

    @Test
    fun `a refused field stays staged when nothing else landed`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 9)
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 7),
        )
        assertEquals(1, staging.apply().refused.size)
        val kept = staging.staged.single().fields.single()
        assertEquals(Changed("rating", "6", "9", "7"), kept, "still staged, still marked as moved")
        assertEquals(1, staging.apply().refused.size, "and refused again, being still stale")
        assertEquals(7, read(universe, "2026-06-01#0", "rating"))
    }

    @Test
    fun `a refused field stays staged when its neighbours landed, and theirs go`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 9)
        staging.set("2026-06-02#0", "max_depth", 25.0)
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 7),
        )
        staging.apply()
        // The same stale row as when nothing landed: its fate does not depend on its neighbours.
        assertEquals(listOf("2026-06-01#0"), staging.staged.map { it.id })
        assertEquals(Changed("rating", "6", "9", "7"), staging.staged.single().fields.single())
    }

    @Test
    fun `within one item, the field that landed goes and the one refused stays`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 9)
        staging.set("2026-06-01#0", "environment.visibility", 20)
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 7),
        )
        val applied = staging.apply()
        assertEquals(1, applied.fields)
        val dive = universe.logbook["2026-06-01#0"]!!
        val environment = (dive.read("environment") as Result.Usable).value as yemoja.data.Item
        assertEquals(20.0, (environment.read("visibility") as Result.Usable).value)
        // Visibility landed, so it is no longer proposed; the rating did not, so it still is.
        val left = staging.staged.single().fields
        assertEquals(listOf(Changed("rating", "6", "9", "7")), left)
    }

    @Test
    fun `a refused field staged again against what is there now lands`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 9)
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 7),
        )
        assertEquals(1, staging.apply().refused.size, "refused, the logbook having moved")
        // What the agent is told to do when a field is refused: stage it again.
        staging.set("2026-06-01#0", "rating", 9)
        assertEquals(Changed("rating", "7", "9", "7"), staging.staged.single().fields.single())
        assertEquals(Applied(1, 1, emptyList()), staging.apply())
        assertEquals(9, read(universe, "2026-06-01#0", "rating"))
    }

    @Test
    fun `staging one field again leaves another field's collision to be caught`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 9)
        staging.set("2026-06-01#0", "environment.visibility", 20)
        val dive = universe.logbook["2026-06-01#0"]!!
        val environment = (dive.read("environment") as Result.Usable).value as yemoja.data.Item
        universe.change(Operation.EDIT, Change.Write(environment, "visibility", 15.0))
        // The rating is staged again; the visibility is not, and is still judged against 12.
        staging.set("2026-06-01#0", "rating", 10)
        val applied = staging.apply()
        assertEquals(10, read(universe, "2026-06-01#0", "rating"))
        assertEquals(
            listOf(
                Refused(
                    "2026-06-01#0",
                    "environment.visibility",
                    "it was 12 when this was staged, and is 15 now",
                ),
            ),
            applied.refused,
        )
    }

    @Test
    fun `clearing a field is staged as a field with nothing in it`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", null)
        assertEquals(listOf(Changed("rating", "6", null)), staging.staged.single().fields)
        staging.apply()
        assertNull(read(universe, "2026-06-01#0", "rating"))
    }

    @Test
    fun `clearing the last field of a block is staged as that field, and lands`() {
        val universe = diving()
        val staging = stagingOf(universe)
        // `visibility` is the only thing this dive's environment holds, so clearing it empties
        // the block — which a file writes by leaving the block out. `DATA-116`.
        staging.set("2026-06-01#0", "environment.visibility", null)
        assertEquals(
            listOf(Changed("environment.visibility", "12", null)),
            staging.staged.single().fields,
            "the field, not the block it was the last of",
        )
        assertEquals(Applied(1, 1, emptyList()), staging.apply())
        val dive = universe.logbook["2026-06-01#0"]!!
        val environment = (dive.read("environment") as Result.Usable).value as yemoja.data.Item
        assertEquals(Result.Absent, environment.read("visibility"))
        assertTrue(staging.empty, "and nothing is left waiting in the review")
    }

    @Test
    fun `an item to be deleted has no copy of how it would be`() {
        val universe = diving()
        val staging = stagingOf(universe)
        assertEquals(Outcome.Done(), staging.delete("2026-06-02#0"))
        val staged = staging.staged.single()
        assertEquals(Staged.Kind.DELETE, staged.kind)
        assertTrue(staged.fields.any { it.at == "max_depth" && it.to == null }, "says what goes")
        staging.apply()
        assertNull(universe.logbook["2026-06-02#0"], "the dive is gone")
        assertEquals(18.0, read(universe, "2026-06-01#0", "max_depth"), "the one beside it is not")
    }

    @Test
    fun `an item edited since its deletion was staged is not deleted`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.delete("2026-06-01#0")
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["2026-06-01#0"]!!, "rating", 3),
        )
        val applied = staging.apply()
        assertEquals(0, applied.items)
        assertEquals("rating", applied.refused.single().at)
        val why = applied.refused.single().reason
        assertTrue("edited since" in why, why)
        assertNotNull(universe.logbook["2026-06-01#0"], "the dive is still here")
        assertEquals(1, staging.staged.size, "and the deletion is still waiting to be looked at")
    }

    @Test
    fun `an item to be added has no copy of how it was, and is named when it lands`() {
        val universe = diving()
        val staging = stagingOf(universe)
        assertEquals(Outcome.Done(), staging.add("dive_site", mapOf("name" to "Elphinstone")))
        val staged = staging.staged.single()
        assertEquals(Staged.Kind.ADD, staged.kind)
        assertEquals("new#0", staged.id, "called something until it is called properly")
        assertEquals(listOf(Changed("name", null, "Elphinstone")), staged.fields)
        staging.apply()
        assertEquals("Elphinstone", read(universe, "elphinstone", "name"), "named by its type")
    }

    @Test
    fun `what does not exist, or has no such field, is refused when it is staged`() {
        val staging = stagingOf(diving())
        assertEquals(Outcome.Refused("nobody names nothing"), staging.set("nobody", "rating", 1))
        assertEquals(Outcome.Refused("nobody names nothing"), staging.delete("nobody"))
        assertEquals(
            Outcome.Refused("dive has no field at depth"),
            staging.set("2026-06-01#0", "depth", 1),
        )
        assertEquals(
            Outcome.Refused("skipper is not a type this logbook holds"),
            staging.add("skipper"),
        )
        assertTrue(staging.empty, "and nothing is staged by a refusal")
    }

    @Test
    fun `a value the field would refuse is refused here, in the field's own words`() {
        val staging = stagingOf(diving())
        val refused = staging.set("2026-06-01#0", "rating", 11)
        assertEquals(Outcome.Refused("rating should be within 1..10"), refused)
        assertTrue(staging.empty)
    }

    @Test
    fun `staging the same field twice keeps the last, against the value it started from`() {
        val staging = stagingOf(diving())
        staging.set("2026-06-01#0", "rating", 8)
        staging.set("2026-06-01#0", "rating", 9)
        assertEquals(listOf(Changed("rating", "6", "9")), staging.staged.single().fields)
    }

    @Test
    fun `an item dropped from the proposal is proposed no longer`() {
        val staging = stagingOf(diving())
        staging.set("2026-06-01#0", "rating", 8)
        staging.set("2026-06-02#0", "max_depth", 25.0)
        staging.drop("2026-06-01#0")
        assertEquals(listOf("2026-06-02#0"), staging.staged.map { it.id })
    }

    @Test
    fun `a proposal is read back from where it was staged`() {
        val universe = diving()
        val store = MemoryFileStore(emptyMap())
        Staging.open(universe, store).set("2026-06-01#0", "rating", 8)
        // The window closed and opened again: what was staged is still staged.
        val again = Staging.open(universe, store)
        assertEquals(listOf(Changed("rating", "6", "8")), again.staged.single().fields)
        again.apply()
        assertEquals(8, read(universe, "2026-06-01#0", "rating"))
    }

    @Test
    fun `a change and an import can both be waiting, and are applied on their own`() {
        val store = MemoryFileStore(mapOf("dive/2026-06-01#0.json" to """{"rating": 6}"""))
        val universe = Universe(
            LogbookReader.read(store, Types.ALL),
            null,
            store,
            null,
            null,
            MemoryFileStore(emptyMap()),
            MemoryFileStore(emptyMap()),
        )
        val staging = universe.staging!!
        staging.set("2026-06-01#0", "rating", 8)
        // Something arrives while the change waits to be looked at.
        val arriving = LogbookReader.read(
            MemoryFileStore(mapOf("dive_site.json" to """{"blue": {"name": "Blue Hole"}}""")),
            Types.ALL,
        )
        universe.importFrom(arriving, MemoryFileStore(emptyMap()))
        val importing = assertNotNull(universe.importing)
        assertEquals(1, staging.staged.size, "the change is still staged")

        // Each is applied on its own, and neither disturbs the other.
        importing.insert("blue")
        assertEquals("Blue Hole", read(universe, "blue", "name"))
        assertEquals(1, staging.staged.size, "and the change is still staged after the import")
        staging.apply()
        assertEquals(8, read(universe, "2026-06-01#0", "rating"))
    }

    @Test
    fun `an item deleted while the staging waited is said rather than written`() {
        val universe = diving()
        val staging = stagingOf(universe)
        staging.set("2026-06-01#0", "rating", 8)
        universe.change(Operation.EDIT, Change.Delete("2026-06-01#0"))
        val applied = staging.apply()
        assertEquals(0, applied.items)
        assertEquals(
            listOf(Refused("2026-06-01#0", "", "it has been deleted since")),
            applied.refused,
        )
    }
}
