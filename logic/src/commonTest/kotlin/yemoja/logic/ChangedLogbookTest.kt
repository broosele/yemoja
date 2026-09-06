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
        assertEquals(Outcome.Done(), done)
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
    fun `an item added is in the logbook and in the files, under an id nobody gave`() {
        val (store, universe) = opened("person.json" to """{"anna": {}}""")
        val done = assertIs<Outcome.Done>(universe.change(
            Operation.EDIT,
            Change.Add(Types.PERSON, mapOf("first_name" to "Tom", "last_name" to "Janssen")),
        ))
        assertEquals(listOf("tom_janssen"), done.added)
        assertEquals("Tom", text(saved(store).logbook["tom_janssen"], "first_name"))
    }

    @Test
    fun `an item with nothing in it is named for its type`() {
        // The manual promises this: an item with nothing in it is a valid item, and Yemoja calls
        // it something.
        val (_, universe) = opened("person.json" to """{"anna": {}}""")
        val done = assertIs<Outcome.Done>(
            universe.change(Operation.EDIT, Change.Add(Types.PERSON)),
        )
        assertEquals(listOf("unknown_person"), done.added)
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

/** What a new item is called, which the caller does not choose. `DATA-84`. */
class MintedIdTest {

    private fun added(universe: Universe, vararg fields: Pair<String, Any?>): String =
        assertIs<Outcome.Done>(
            universe.change(Operation.EDIT, Change.Add(Types.PERSON, mapOf(*fields))),
        ).added.single()

    @Test
    fun `a name becomes an id a file name can carry`() {
        val (_, universe) = opened("person.json" to "{}")
        assertEquals("anna_de_vries", added(universe, "name" to "Anna de Vries"))
    }

    @Test
    fun `an alphabet an id cannot hold falls back rather than being mangled`() {
        // `DATA-84` puts this cost here on purpose. The name field keeps the real spelling.
        val (_, universe) = opened("person.json" to "{}")
        val id = added(universe, "name" to "Ελλάδα")
        assertEquals("unknown_person", id)
        assertEquals("Ελλάδα", text(universe.logbook[id], "name"))
    }

    @Test
    fun `a proposal already taken moves to the next index`() {
        val (_, universe) = opened("person.json" to """{"anna_de_vries": {}}""")
        assertEquals("anna_de_vries#1", added(universe, "name" to "Anna de Vries"))
        assertEquals("anna_de_vries#2", added(universe, "name" to "Anna de Vries"))
    }

    @Test
    fun `two added in one change do not both take the same id`() {
        // Minted while judging, so an id taken by one is taken for the other.
        val (_, universe) = opened("person.json" to "{}")
        val done = assertIs<Outcome.Done>(universe.change(
            Operation.EDIT,
            Change.Add(Types.PERSON, mapOf("name" to "Anna")),
            Change.Add(Types.PERSON, mapOf("name" to "Anna")),
        ))
        assertEquals(listOf("anna", "anna#1"), done.added)
    }

    @Test
    fun `a dive is named for its day and always carries an index`() {
        val (_, universe) = opened("dive.json" to "{}")
        val done = assertIs<Outcome.Done>(universe.change(
            Operation.EDIT,
            Change.Add(Types.DIVE, mapOf("start_date" to "2026-04-28")),
            Change.Add(Types.DIVE, mapOf("start_date" to "2026-04-28")),
        ))
        assertEquals(listOf("2026-04-28#0", "2026-04-28#1"), done.added)
    }

    @Test
    fun `an id freed by a deletion is handed out again`() {
        // The rule against reuse was dropped so that re-entering a dive heals what pointed at it.
        val (_, universe) = opened("person.json" to """{"anna": {}, "anna#1": {}}""")
        universe.change(Operation.EDIT, Change.Delete("anna"))
        assertEquals("anna", added(universe, "name" to "Anna"))
    }

    @Test
    fun `a field a new item cannot take refuses the whole change`() {
        val (store, universe) = opened("person.json" to "{}")
        val outcome = universe.change(
            Operation.EDIT,
            Change.Add(Types.PERSON, mapOf("birthday" to "the fourth of never")),
        )
        assertIs<Outcome.Refused>(outcome)
        assertEquals(0, universe.logbook.size, "and nothing was added")
        assertEquals(0, saved(store).logbook.size)
    }
}

/** Deleting, and what becomes of what pointed at it. */
class DeletedReferencesTest {

    private fun withBuddy(): Pair<yemoja.data.json.MemoryFileStore, Universe> = opened(
        "person.json" to """{"anna": {}, "tom": {}}""",
        "dive/2026-01-01#0.json" to """{"buddies": ["@tom", "@anna"], "previous_dive": "@x"}""",
    )

    @Test
    fun `references are left alone by default`() {
        val (store, universe) = withBuddy()
        universe.change(Operation.EDIT, Change.Delete("tom"))
        val dive = saved(store).logbook["2026-01-01#0"]!!
        assertTrue("@tom" in dive.read("buddies").toString(), "still written")
    }

    @Test
    fun `asked to, it takes the reference out of the list`() {
        val (store, universe) = withBuddy()
        universe.change(Operation.EDIT, Change.Delete("tom", alsoReferences = true))
        val dive = saved(store).logbook["2026-01-01#0"]!!
        val written = dive.read("buddies").toString()
        assertTrue("@tom" !in written, written)
        assertTrue("@anna" in written, "and the one beside it stayed")
    }

    @Test
    fun `a single reference naming it is cleared`() {
        val (store, universe) = opened(
            "dive.json" to """{"a#0": {}, "b#0": {"previous_dive": "@a#0"}}""",
        )
        universe.change(Operation.EDIT, Change.Delete("a#0", alsoReferences = true))
        val dive = saved(store).logbook["b#0"]!!
        assertEquals(Result.Absent, dive.read("previous_dive"))
    }
}
