package yemoja.logic

import yemoja.data.Date
import yemoja.data.Item
import yemoja.data.OwnedItem
import yemoja.data.Result
import yemoja.data.Stored
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Changing a logbook, which is the one way anything in one changes.
 *
 * See ../../../../../doc.md — the layer's own document is logic/doc.md.
 */

/** A logbook of these files, and a Universe over it that saves back into them. */
private fun opened(vararg files: Pair<String, String>): Pair<MemoryFileStore, Universe> {
    val store = MemoryFileStore(mapOf(*files))
    return store to Universe(LogbookReader.read(store, Types.ALL), null, store)
}

/** What the files say now, read afresh, which is the only thing a save is for. */
private fun saved(store: MemoryFileStore): Universe =
    Universe(LogbookReader.read(store, Types.ALL), null, store)

private fun text(item: Item?, field: String): String? =
    (item?.single<String>(field) as? Result.Usable)?.value

class ChangedLogbookTest {

    @Test
    fun `a field written is on disk without anything being saved`() {
        // There is no unsaved state and no save to forget: the change is the save.
        val (store, universe) = opened("person.json" to """{"anna": {"first_name": "Anna"}}""")
        val anna = universe.logbook["anna"]!!
        val done = universe.change(Operation.EDIT, Change.Write(anna, "phone", "0123"))
        assertEquals(Outcome.Done, done)
        assertEquals("0123", text(saved(store).logbook["anna"], "phone"))
    }

    @Test
    fun `one change touching two items saves both`() {
        val (store, universe) = opened(
            "person.json" to """{"anna": {}, "tom": {}}""",
        )
        universe.change(
            Operation.EDIT,
            Change.Write(universe.logbook["anna"]!!, "first_name", "Anna"),
            Change.Write(universe.logbook["tom"]!!, "first_name", "Tom"),
        )
        val again = saved(store).logbook
        assertEquals("Anna", text(again["anna"], "first_name"))
        assertEquals("Tom", text(again["tom"], "first_name"))
    }

    @Test
    fun `a change refused leaves the logbook exactly as it was`() {
        // Judged whole before any of it lands, so the good half of a bad change does not happen.
        val (store, universe) =
            opened("dive_site.json" to """{"blue_hole": {"name": "Blue Hole"}}""")
        val site = universe.logbook["blue_hole"]!!
        val outcome = universe.change(
            Operation.EDIT,
            Change.Write(site, "name", "Blue Hole II"),
            Change.Write(site, "water_type", "brackish"),
        )
        val refused = assertIs<Outcome.Refused>(outcome)
        assertTrue("brackish" in refused.reason || "salt" in refused.reason, refused.reason)
        assertEquals("Blue Hole", text(site, "name"), "the first was not applied either")
        assertEquals("Blue Hole", text(saved(store).logbook["blue_hole"], "name"))
    }

    @Test
    fun `a field of an owned item is saved into its owner's file`() {
        // An owned item has no file of its own, and no address is needed: the caller walked there.
        val (store, universe) = opened("person.json" to """{"anna": {"medical": {}}}""")
        val anna = universe.logbook["anna"]!!
        val medical = (anna.read("medical") as Result.Usable).value as OwnedItem
        universe.change(Operation.EDIT, Change.Write(medical, "blood_group", "O+"))
        val again = saved(store).logbook["anna"]!!
        val medicalAgain = (again.read("medical") as Result.Usable).value as Item
        assertEquals("O+", text(medicalAgain, "blood_group"))
    }

    @Test
    fun `an owned item is made by writing an empty set of fields`() {
        val (store, universe) = opened("person.json" to """{"anna": {}}""")
        val anna = universe.logbook["anna"]!!
        universe.change(Operation.EDIT, Change.Write(anna, "medical", Stored.Members(emptyMap())))
        val again = saved(store).logbook["anna"]!!
        assertIs<Result.Usable<*>>(again.read("medical"))
    }

    @Test
    fun `an item added is in the logbook and in the files`() {
        val (store, universe) = opened("person.json" to """{"anna": {}}""")
        universe.change(Operation.EDIT, Change.Add(Types.PERSON, "tom"))
        assertTrue(universe.logbook["tom"] != null)
        assertTrue(saved(store).logbook["tom"] != null)
    }

    @Test
    fun `an item added and filled in the same change is saved with its fields`() {
        // What "an operation can be complex" means: two parts, one thing the user did.
        val (store, universe) = opened("person.json" to """{"anna": {}}""")
        universe.change(Operation.EDIT, Change.Add(Types.PERSON, "tom"))
        val tom = universe.logbook["tom"]!!
        universe.change(Operation.EDIT, Change.Write(tom, "first_name", "Tom"))
        assertEquals("Tom", text(saved(store).logbook["tom"], "first_name"))
    }

    @Test
    fun `an item deleted is gone from the logbook and from the files`() {
        val (store, universe) = opened("person.json" to """{"anna": {}, "tom": {}}""")
        universe.change(Operation.EDIT, Change.Delete("tom"))
        assertNull(universe.logbook["tom"])
        val again = saved(store).logbook
        assertNull(again["tom"])
        assertTrue(again["anna"] != null, "and the one beside it stayed")
    }

    @Test
    fun `a reference to something deleted is left dangling, not hunted down`() {
        // A state the model already carries: a person not entered yet looks the same from here.
        val (store, universe) = opened(
            "person.json" to """{"anna": {}, "tom": {}}""",
            "dive/2026-01-01#0.json" to """{"buddies": ["@tom"]}""",
        )
        universe.change(Operation.EDIT, Change.Delete("tom"))
        val dive = saved(store).logbook["2026-01-01#0"]!!
        assertTrue("@tom" in dive.read("buddies").toString(), "the reference is still written")
    }

    @Test
    fun `a value is judged in the model's own units`() {
        val (store, universe) = opened("dive_site.json" to """{"blue_hole": {}}""")
        val site = universe.logbook["blue_hole"]!!
        universe.change(Operation.EDIT, Change.Write(site, "max_depth", 40.0))
        val read = saved(store).logbook["blue_hole"]!!.single<Double>("max_depth")
        assertEquals(40.0, (read as Result.Usable).value)
    }

    @Test
    fun `a worked-out field is not something a change may name`() {
        val (_, universe) = opened("dive/2026-01-01#0.json" to """{"start_date": "2026-01-01"}""")
        val dive = universe.logbook["2026-01-01#0"]!!
        assertEquals(Date(2026, 1, 1), (dive.single<Date>("start_date") as Result.Usable).value)
        // `name` is worked out from the id, so nothing offers to edit it and asking is a fault.
        assertTrue(
            runCatching { universe.change(Operation.EDIT, Change.Write(dive, "name", "x")) }
                .isFailure,
        )
    }
}
