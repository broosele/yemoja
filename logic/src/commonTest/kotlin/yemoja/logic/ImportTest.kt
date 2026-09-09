package yemoja.logic

import yemoja.data.Result
import yemoja.data.json.LogbookReader
import yemoja.data.json.MemoryFileStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/*
 * Items arriving from somewhere else, staged, reviewed and taken in.
 *
 * See ../../../../../doc.md — the layer's own document is logic/reconciliation.md.
 */

/** A Universe over these files. */
private fun logbook(vararg files: Pair<String, String>): Pair<MemoryFileStore, Universe> {
    val store = MemoryFileStore(mapOf(*files))
    return store to Universe(LogbookReader.read(store, Types.ALL), null, store)
}

/** An item set read from these files, which is what a source hands over. */
private fun arriving(vararg files: Pair<String, String>) =
    LogbookReader.read(MemoryFileStore(mapOf(*files)), Types.ALL)

/** An import of [from] into [into], staged in a folder of its own. */
private fun staged(
    from: Array<out Pair<String, String>>,
    into: Universe,
): Pair<MemoryFileStore, Import> {
    val staging = MemoryFileStore(emptyMap())
    return staging to Import.begin(arriving(*from), staging, into)
}

private fun nameOf(universe: Universe, id: String): String? =
    (universe.logbook[id]?.single<String>("name") as? Result.Usable)?.value

class StagedImportTest {

    @Test
    fun `the incoming items are written out as a logbook of their own`() {
        // Which is what makes reviewing one cost nothing new. `RECON-1`.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        assertTrue(staging.isFile("region.json"), staging.namesIn("").toString())
        assertEquals("A", nameOf(import.staged, "a"))
    }

    @Test
    fun `a reference between two incoming items resolves among them`() {
        // Not in the logbook they are going into, which does not hold either of them yet.
        val (_, into) = logbook("person.json" to "{}")
        val (_, import) = staged(
            arrayOf(
                "person.json" to """{"tom": {"first_name": "Tom"}}""",
                "dive/d#0.json" to """{"buddies": ["@tom"]}""",
            ),
            into,
        )
        val dive = import.staged.logbook["d#0"]!!
        val read = dive.list<yemoja.data.Reference>("buddies")
        assertIs<Result.Usable<*>>(read)
        val first = ((read as Result.Usable).value.first() as yemoja.data.Element.Usable).value
        assertIs<yemoja.data.Reference.Identified>(first)
    }

    @Test
    fun `an incoming item can be edited before it is taken in`() {
        // It is a logbook, so it is edited by what edits a logbook, and saved as it is edited.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        val region = import.staged.logbook["a"]!!
        import.staged.change(Operation.EDIT, Change.Write(region, "name", "Corrected"))
        assertTrue("Corrected" in staging.readText("region.json"), staging.readText("region.json"))
        import.apply()
        assertEquals("Corrected", nameOf(into, "a"))
    }
}

class MetImportTest {

    @Test
    fun `an item nothing answers to is new`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}}"""), into)
        assertEquals(Meeting.NOTHING, import.meeting("a"))
    }

    @Test
    fun `one of the same type under the same id is the same item`() {
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertEquals(Meeting.THE_SAME, import.meeting("a"))
    }

    @Test
    fun `an id taken by another type is something else`() {
        // Two logbooks can each mint north_sea, one for a region and one for a person.
        val (_, into) = logbook("region.json" to """{"north_sea": {}}""")
        val (_, import) = staged(arrayOf("person.json" to """{"north_sea": {}}"""), into)
        assertEquals(Meeting.SOMETHING_ELSE, import.meeting("north_sea"))
    }
}

class AppliedImportTest {

    @Test
    fun `a new item goes in under the id it came with`() {
        // Mint a new one and the dive that names the person no longer finds them.
        val (store, into) = logbook("person.json" to "{}", "dive/d#0.json" to "{}")
        val (_, import) = staged(
            arrayOf(
                "person.json" to """{"tom": {"first_name": "Tom"}}""",
                "dive/d#1.json" to """{"buddies": ["@tom"]}""",
            ),
            into,
        )
        assertIs<Outcome.Done>(import.apply())
        assertTrue(into.logbook["tom"] != null)
        assertTrue("@tom" in store.readText("dive/d#1.json"), store.readText("dive/d#1.json"))
    }

    @Test
    fun `a field the incoming item holds is written onto the one already held`() {
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Theirs", nameOf(into, "a"))
    }

    @Test
    fun `a field it does not hold is left alone`() {
        // Silence is not an instruction to erase: a dive computer knows nothing about buddies.
        val (_, into) = logbook("region.json" to """{"a": {"name": "Mine", "category": "sea"}}""")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "Theirs"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Theirs", nameOf(into, "a"), "the one it does hold was written")
        val category = into.logbook["a"]!!.single<String>("category")
        assertEquals("sea", (category as Result.Usable).value, "and the one it does not survived")
    }

    @Test
    fun `an id taken by another type is left out`() {
        val (_, into) = logbook("region.json" to """{"north_sea": {"name": "Mine"}}""")
        val (_, import) = staged(
            arrayOf("person.json" to """{"north_sea": {"first_name": "Tom"}}"""),
            into,
        )
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Mine", nameOf(into, "north_sea"), "and what was there is untouched")
    }

    @Test
    fun `applying twice does nothing the second time`() {
        // What was added answers to its id afterwards, so it is met as the same item.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"name": "A"}}"""), into)
        assertIs<Outcome.Done>(import.apply())
        assertIs<Outcome.Done>(import.apply())
        assertEquals(1, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `it lands whole or not at all`() {
        // One change, so the journal has one thing to record and one to take back. `REQ-2`.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(
            arrayOf("region.json" to """{"a": {"name": "A"}, "b": {"north": 91}}"""),
            into,
        )
        assertIs<Outcome.Refused>(import.apply())
        assertEquals(0, into.logbook.allOf(Types.REGION).size, "including the good one")
    }
}

class DecidedImportTest {

    @Test
    fun `an item taken in leaves the staged logbook`() {
        // Deciding is editing, so what is left in the folder is what has not been decided.
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        assertIs<Outcome.Done>(import.insert("a"))
        assertTrue(into.logbook["a"] != null)
        assertEquals(listOf("b"), import.incoming.map { import.staged.logbook.idOf(it) })
        assertTrue("a" !in staging.readText("region.json"), staging.readText("region.json"))
    }

    @Test
    fun `an item turned down leaves it too, and goes nowhere`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        assertEquals(listOf("b"), import.incoming.map { import.staged.logbook.idOf(it) })
        assertEquals(0, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `a refused item stays where it is`() {
        // So the reason can be shown against an item still on the screen.
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {"north": 91}}"""), into)
        assertIs<Outcome.Refused>(import.insert("a"))
        assertEquals(listOf("a"), import.incoming.map { import.staged.logbook.idOf(it) })
    }

    @Test
    fun `a review put down is taken up where it stopped`() {
        val (_, into) = logbook("region.json" to "{}")
        val (staging, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        val again = Import.open(staging, Types.ALL, into)
        assertEquals(listOf("b"), again.incoming.map { again.staged.logbook.idOf(it) })
    }

    @Test
    fun `applying takes everything left across`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(arrayOf("region.json" to """{"a": {}, "b": {}}"""), into)
        import.remove("a")
        assertIs<Outcome.Done>(import.apply())
        assertEquals(listOf("b"), into.logbook.allOf(Types.REGION).map { into.logbook.idOf(it) })
        assertEquals(emptyList(), import.incoming, "and nothing is left to review")
    }
}

class ListedImportTest {

    @Test
    fun `the incoming items are listed, each type in its own order`() {
        val (_, into) = logbook("region.json" to "{}")
        val (_, import) = staged(
            arrayOf("region.json" to """{"b": {"name": "Bee"}, "a": {"name": "Ay"}}"""),
            into,
        )
        val said = import.incoming.map { import.staged.logbook.idOf(it) }
        assertEquals(listOf("a", "b"), said, "alphabetical, as a region's tab lists them")
    }
}

/** A source whose ids were minted here, which no id may be matched by. */
class UnmatchedImportTest {

    private fun minted(held: String, coming: String): Pair<Universe, Import> {
        val store = MemoryFileStore(mapOf("region.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val source = LogbookReader.read(MemoryFileStore(mapOf("region.json" to coming)), Types.ALL)
        into.importFrom(source, MemoryFileStore(emptyMap()), Matching.NONE)
        return into to into.importing!!
    }

    @Test
    fun `nothing is met, however its id reads`() {
        // A minted id says what this model would have called such an item, not which it is.
        val (_, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertEquals(Meeting.NOTHING, import.meeting("a"))
    }

    @Test
    fun `an item already held is left exactly as it was`() {
        // The bug this exists for: an unrelated item under a colliding id was written over.
        val (into, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertIs<Outcome.Done>(import.apply())
        assertEquals("Mine", nameOf(into, "a"))
    }

    @Test
    fun `the arriving one goes in beside it, under a name that is free`() {
        val (into, import) = minted("""{"a": {"name": "Mine"}}""", """{"a": {"name": "Theirs"}}""")
        assertIs<Outcome.Done>(import.apply())
        val held = into.logbook.allOf(Types.REGION).map { into.logbook.idOf(it) }
        assertEquals(listOf("a", "a#1"), held)
        assertEquals("Theirs", nameOf(into, "a#1"))
    }

    @Test
    fun `two arriving under one taken name do not land on each other`() {
        val coming = """{"a": {"name": "One"}, "b": {"name": "Two"}}"""
        val (into, import) = minted("""{"a": {}}""", coming)
        assertIs<Outcome.Done>(import.apply())
        assertEquals(3, into.logbook.allOf(Types.REGION).size)
    }

    @Test
    fun `importing the same thing twice puts it in twice, which is visible`() {
        // Not what anybody wants, and better than quietly writing over what was there. The rule
        // that recognises an item by what it says is what reconciliation.md asks for.
        val store = MemoryFileStore(mapOf("region.json" to "{}"))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val coming = """{"a": {"name": "Theirs"}}"""
        repeat(2) {
            val from = MemoryFileStore(mapOf("region.json" to coming))
            into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
                Matching.NONE)
            into.importing!!.apply()
        }
        assertEquals(2, into.logbook.allOf(Types.REGION).size)
    }
}

/** Which item already held an arriving one appears to be, where nothing was matched by id. */
class ProposalTest {

    private fun dived(at: String, long: String = "3600") =
        """{"start_date": "2024-06-15", "start_time": "$at", "duration": $long}"""

    private fun over(held: String, coming: String): Import {
        val store = MemoryFileStore(mapOf("dive/d#0.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("dive/x#0.json" to coming))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
            Matching.NONE)
        return into.importing!!
    }

    @Test
    fun `one that overlaps in time is what it appears to be`() {
        // Nobody is on two dives at once, so two recordings that overlap are one dive's.
        assertEquals("d#0", over(dived("10:00:00"), dived("10:30:00")).proposal("x#0"))
    }

    @Test
    fun `one that does not overlap is nothing`() {
        assertNull(over(dived("10:00:00"), dived("14:00:00")).proposal("x#0"))
    }

    @Test
    fun `two beginning at the same instant meet, however long they say they were`() {
        val without = """{"start_date": "2024-06-15", "start_time": "10:00:00"}"""
        assertEquals("d#0", over(without, without).proposal("x#0"))
    }

    @Test
    fun `one that says no time appears to be nothing`() {
        assertNull(over(dived("10:00:00"), """{"max_depth": 12}""").proposal("x#0"))
    }

    /** An import of one region into a logbook holding another, matching nothing. */
    private fun regions(held: String, coming: String): Import {
        val store = MemoryFileStore(mapOf("region.json" to held))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("region.json" to coming))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()),
            Matching.NONE)
        return into.importing!!
    }

    @Test
    fun `a type that keeps no times is proposed by the name its type would give it`() {
        // Nine of the ten propose an id from the item's own name, so the proposal is the name
        // match: two logbooks each holding a Blue Hole both propose blue_hole.
        val import = regions("""{"x": {"name": "North Sea"}}""", """{"y": {"name": "North Sea"}}""")
        assertEquals("x", import.proposal("y"))
    }

    @Test
    fun `one of another name is proposed as nothing`() {
        val import = regions("""{"x": {"name": "North Sea"}}""", """{"y": {"name": "Red Sea"}}""")
        assertNull(import.proposal("y"))
    }

    @Test
    fun `two nobody named are not proposed as one`() {
        // They would both fall back to unknown_region, which says nothing about either.
        assertNull(regions("""{"x": {}}""", """{"y": {}}""").proposal("y"))
    }

    @Test
    fun `the index a proposal carries is left off`() {
        // It says where an item sat rather than what it is, which is what makes an id unusable
        // for matching in the first place.
        val held = """{"north_sea": {"name": "North Sea"}, "b": {"name": "North Sea"}}"""
        val import = regions(held, """{"y": {"name": "North Sea"}}""")
        assertEquals("north_sea", import.proposal("y"))
    }

    @Test
    fun `taking one in onto another writes its fields there and adds nothing`() {
        val import = over(dived("10:00:00"), """{"start_date": "2024-06-15",
            "start_time": "10:30:00", "max_depth": 28.4}""")
        assertIs<Outcome.Done>(import.insert("x#0", "d#0"))
        val into = import.staged
        assertEquals(0, into.logbook.allOf(Types.DIVE).size, "and it left the review")
    }

    @Test
    fun `a carried id is not asked about`() {
        val store = MemoryFileStore(mapOf("region.json" to "{}"))
        val into = Universe(LogbookReader.read(store, Types.ALL), null, store)
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {}}"""))
        into.importFrom(LogbookReader.read(from, Types.ALL), MemoryFileStore(emptyMap()))
        assertTrue(!into.importing!!.asked("a"))
    }
}

/** How a staged review's items match is written beside them, so taking it up cannot forget. */
class RememberedMatchingTest {

    private fun holding(held: String): Universe {
        val store = MemoryFileStore(mapOf("region.json" to held))
        return Universe(LogbookReader.read(store, Types.ALL), null, store)
    }

    @Test
    fun `a review taken up again matches as it did`() {
        // Forgetting would match a minted id as though it were carried, which is the overwriting
        // Matching.NONE exists to stop.
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(emptyMap())
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        Import.begin(LogbookReader.read(from, Types.ALL), staging, into, Matching.NONE)
        assertTrue(staging.isFile(Import.ABOUT), "the matching is written beside the items")
        assertEquals(Meeting.NOTHING, Import.open(staging, Types.ALL, into).meeting("a"))
    }

    @Test
    fun `one begun as another logbook still matches by id when taken up`() {
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(emptyMap())
        val from = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        Import.begin(LogbookReader.read(from, Types.ALL), staging, into)
        assertEquals(Meeting.THE_SAME, Import.open(staging, Types.ALL, into).meeting("a"))
    }

    @Test
    fun `a folder with no note is read as matching nothing`() {
        // The safe reading of an unknown source is the one that duplicates rather than overwrites.
        val into = holding("""{"a": {"name": "Mine"}}""")
        val staging = MemoryFileStore(mapOf("region.json" to """{"a": {"name": "Theirs"}}"""))
        assertEquals(Meeting.NOTHING, Import.open(staging, Types.ALL, into).meeting("a"))
    }
}
